package com.example.agent.service;

import com.example.agent.capability.AgentRegistry;
import com.example.agent.capability.CapabilityMeta;
import com.example.agent.entity.ElectionEntity;
import com.example.agent.repository.ElectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 选举服务（阶段三完全版选举）。
 * <p>每轮结束对候选能力 agent 做组合加权评分（全体平权），得分最高者当选下一轮 Manager；
 * 平票时按信用分高者胜。冷启动（无分配历史）Manager 为 "default"。
 *
 * <p>composite(i) = w1·成功率(i) + w2·耗时分(i) + w3·均衡度(i) + w4·score_term(S_i)
 * 权重为可调默认值：w1=0.4, w2=0.2, w3=0.1, w4=0.3。
 */
@Service
public class ElectionService {

    private static final Logger log = LoggerFactory.getLogger(ElectionService.class);

    public static final double W_SUCCESS = 0.4;
    public static final double W_TIME = 0.2;
    public static final double W_BALANCE = 0.1;
    public static final double W_SCORE = 0.3;
    /** 耗时半衰期（ms）：avgDuration 达 1 分钟时 timeScore 降至 e^-1 */
    private static final double TIME_HALF_MS = 60_000.0;

    private final AgentStatsService statsService;
    private final CreditScoreService creditScoreService;
    private final AgentRegistry registry;
    private final ElectionRepository repository;
    private final ObjectMapper objectMapper;

    /** 当前（下一轮）Manager：冷启动 "default"，每轮选举后更新为当选 agent。 */
    private final AtomicReference<String> currentManager = new AtomicReference<>("default");

    public ElectionService(AgentStatsService statsService,
                           CreditScoreService creditScoreService,
                           AgentRegistry registry,
                           ElectionRepository repository,
                           ObjectMapper objectMapper) {
        this.statsService = statsService;
        this.creditScoreService = creditScoreService;
        this.registry = registry;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public String currentManager() {
        return currentManager.get();
    }

    /** 最近 N 次选举结果（新→旧）。 */
    public List<ElectionEntity> recent(int limit) {
        return repository.findAllByOrderByRoundDesc().stream()
                .limit(limit > 0 ? limit : 20)
                .toList();
    }

    /**
     * 对指定轮执行选举：计算候选池、加权 composite、选出 winner 并持久化，更新当前 Manager。
     *
     * @param round      轮次（termRound）
     * @param managerRef 本轮管理者的 label（冷启动 "default"）
     * @return 持久化后的选举结果
     */
    public ElectionEntity elect(int round, String managerRef) {
        Map<String, Map<String, Object>> stats = lowercased(statsService.stats());
        Set<String> candidates = candidatePool(round);
        int n = candidates.size();
        int totalLoad = stats.values().stream()
                .mapToInt(m -> ((Number) m.getOrDefault("allocationCount", 0)).intValue())
                .sum();
        double fair = n == 0 ? 0.0 : 1.0 / n;

        List<Map<String, Object>> votes = new ArrayList<>();
        String winner = null;
        double bestComposite = -1.0;
        int bestScore = -1;

        for (String label : sorted(candidates)) {
            Map<String, Object> s = stats.getOrDefault(label, emptyStats());
            int score = creditScoreService.getOrInit(label);
            double successRate = ((Number) s.getOrDefault("successRate", 0.0)).doubleValue();
            double avgMs = ((Number) s.getOrDefault("avgDurationMs", 0.0)).doubleValue();
            int allocCount = ((Number) s.getOrDefault("allocationCount", 0)).intValue();

            double successScore = successRate;
            double timeScore = Math.exp(-avgMs / TIME_HALF_MS);
            double loadShare = totalLoad == 0 ? 0.0 : (double) allocCount / totalLoad;
            double balanceScore = clamp01(1.0 - Math.abs(loadShare - fair));
            double scoreTerm = creditScoreService.scoreTerm(score);

            double composite = round4(
                    W_SUCCESS * successScore + W_TIME * timeScore
                            + W_BALANCE * balanceScore + W_SCORE * scoreTerm);

            Map<String, Object> v = new LinkedHashMap<>();
            v.put("candidate", label);
            v.put("composite", composite);
            v.put("creditScore", score);
            v.put("successRate", round4(successRate));
            v.put("timeScore", round4(timeScore));
            v.put("balanceScore", round4(balanceScore));
            v.put("scoreTerm", round4(scoreTerm));
            votes.add(v);

            boolean higher = composite > bestComposite + 1e-9;
            boolean tied = Math.abs(composite - bestComposite) <= 1e-9;
            if (higher || (tied && score > bestScore)) {
                winner = label;
                bestComposite = composite;
                bestScore = score;
            }
        }

        if (winner == null) {
            // 无候选（如空计划/无分配），保持当前 Manager 不变
            winner = currentManager.get();
        }

        ElectionEntity e = new ElectionEntity();
        e.setId(UUID.randomUUID().toString().replace("-", ""));
        e.setRound(round);
        e.setManagerRef(nz(managerRef));
        e.setWinner(winner);
        e.setCandidatesJson(writeJson(votes));
        repository.save(e);

        currentManager.set(winner);
        log.info("选举完成 round={} manager={} -> winner={} candidates={}", round, nz(managerRef), winner, n);
        return e;
    }

    /** 候选池：本轮 SUCCESS 的 agent；若无则退化为本轮被分配的 agent；再退化到全部已注册 agent。 */
    private Set<String> candidatePool(int round) {
        Set<String> succeeded = lowercasedSet(statsService.successLabelsInRound(round));
        if (!succeeded.isEmpty()) {
            return succeeded;
        }
        Set<String> allocated = lowercasedSet(statsService.allocatedLabelsInRound(round));
        if (!allocated.isEmpty()) {
            return allocated;
        }
        Set<String> all = registry.metas().stream()
                .map(m -> m.label().toLowerCase())
                .collect(Collectors.toSet());
        return all.isEmpty() ? Set.of("general") : all;
    }

    private Set<String> lowercasedSet(Set<String> in) {
        return in.stream().map(String::toLowerCase).collect(Collectors.toSet());
    }

    private Map<String, Map<String, Object>> lowercased(Map<String, Map<String, Object>> in) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> en : in.entrySet()) {
            out.put(en.getKey().toLowerCase(), en.getValue());
        }
        return out;
    }

    private List<String> sorted(Set<String> in) {
        return in.stream().sorted().toList();
    }

    private Map<String, Object> emptyStats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("allocationCount", 0);
        m.put("successCount", 0);
        m.put("successRate", 0.0);
        m.put("avgDurationMs", 0.0);
        return m;
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            log.warn("选举候选明细序列化失败: {}", e.getMessage());
            return "[]";
        }
    }

    private static double clamp01(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }

    private static double round4(double x) {
        return Math.round(x * 10000.0) / 10000.0;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}