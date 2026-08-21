package com.example.agent.service;

import com.example.agent.entity.AllocationRecordEntity;
import com.example.agent.repository.AllocationRecordRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 分配记录统计：按分配记录计算各能力 agent 的成功率 / 平均耗时 / 负载（分配次数），
 * 并提供分配记录的落库与「轮次」序号，作为阶段三选举与信用分奖惩的数据底座。
 */
@Service
public class AgentStatsService {

    private final AllocationRecordRepository repository;

    public AgentStatsService(AllocationRecordRepository repository) {
        this.repository = repository;
    }

    /** 下一轮序号：当前最大 round + 1（无记录则为 1）。 */
    public int nextRound() {
        return repository.findAll().stream()
                .mapToInt(AllocationRecordEntity::getRound)
                .max()
                .orElse(0) + 1;
    }

    /** 落一条分配记录（目标 → 子任务 → 能力 agent → 状态 → 耗时）。 */
    public void record(String runId, int round, int stepNo, String goal, String subtaskGoal,
                       String capability, String status, long durationMs) {
        AllocationRecordEntity e = new AllocationRecordEntity();
        e.setId(UUID.randomUUID().toString().replace("-", ""));
        e.setRunId(runId);
        e.setRound(round);
        e.setStepNo(stepNo);
        e.setGoal(goal);
        e.setSubtaskGoal(subtaskGoal);
        e.setCapability(capability);
        e.setStatus(status);
        e.setDurationMs(durationMs);
        repository.save(e);
    }

    /** 各能力 agent 的成功率 / 平均耗时 / 负载（分配次数）。 */
    public Map<String, Map<String, Object>> stats() {
        List<AllocationRecordEntity> all = repository.findAll();
        Map<String, List<AllocationRecordEntity>> grouped = all.stream()
                .collect(Collectors.groupingBy(e -> blank(e.getCapability(), "unknown")));
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<AllocationRecordEntity>> en : grouped.entrySet()) {
            List<AllocationRecordEntity> list = en.getValue();
            long success = list.stream().filter(e -> "SUCCESS".equals(e.getStatus())).count();
            double successRate = list.isEmpty() ? 0.0 : (double) success / list.size();
            double avgMs = list.stream().mapToLong(AllocationRecordEntity::getDurationMs).average().orElse(0.0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("allocationCount", list.size());
            m.put("successCount", success);
            m.put("successRate", Math.round(successRate * 10000.0) / 10000.0);
            m.put("avgDurationMs", Math.round(avgMs));
            out.put(en.getKey(), m);
        }
        return out;
    }

    /** 最近 N 条分配记录（供前端/日志查看数据更改操作）。 */
    public List<Map<String, Object>> recent(int limit) {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .limit(limit > 0 ? limit : 20)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("runId", e.getRunId());
                    m.put("round", e.getRound());
                    m.put("stepNo", e.getStepNo());
                    m.put("capability", blank(e.getCapability(), ""));
                    m.put("status", blank(e.getStatus(), ""));
                    m.put("durationMs", e.getDurationMs());
                    m.put("subtaskGoal", blank(e.getSubtaskGoal(), ""));
                    m.put("createdAt", e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
                    return m;
                })
                .toList();
    }

    private String blank(String s, String dflt) {
        return s == null || s.isBlank() ? dflt : s;
    }
}