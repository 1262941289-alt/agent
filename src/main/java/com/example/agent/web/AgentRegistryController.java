package com.example.agent.web;

import com.example.agent.capability.AgentRegistry;
import com.example.agent.service.AgentStatsService;
import com.example.agent.service.AgentStatusService;
import com.example.agent.service.CreditScoreService;
import com.example.agent.service.ElectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力注册中心、信用分与分配记录统计的开销入口。
 */
@RestController
@RequestMapping("/api/agents")
public class AgentRegistryController {

    private final AgentRegistry registry;
    private final CreditScoreService creditScoreService;
    private final AgentStatsService agentStatsService;
    private final ElectionService electionService;
    private final AgentStatusService statusService;

    public AgentRegistryController(AgentRegistry registry,
                                   CreditScoreService creditScoreService,
                                   AgentStatsService agentStatsService,
                                   ElectionService electionService,
                                   AgentStatusService statusService) {
        this.registry = registry;
        this.creditScoreService = creditScoreService;
        this.agentStatsService = agentStatsService;
        this.electionService = electionService;
        this.statusService = statusService;
    }

    /** GET /api/agents/capabilities —— 已注册能力清单（含角色风格）。 */
    @GetMapping("/capabilities")
    public List<Map<String, String>> capabilities() {
        return registry.metas().stream()
                .map(m -> {
                    Map<String, String> map = new LinkedHashMap<>();
                    map.put("label", m.label());
                    map.put("description", m.description());
                    map.put("style", m.style().getDisplayName());
                    map.put("styleKey", m.style().name());
                    map.put("styleDesc", m.style().getDescription());
                    return map;
                })
                .toList();
    }

    /** GET /api/agents/credit-scores —— 各能力 agent 信用分（默认 50）。 */
    @GetMapping("/credit-scores")
    public Map<String, Integer> creditScores() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (var meta : registry.metas()) {
            out.put(meta.label(), creditScoreService.getOrInit(meta.label()));
        }
        return out;
    }

    /** GET /api/agents/stats —— 各能力 agent 的成功率 / 平均耗时 / 负载 / 信用分 / 实时状态。 */
    @GetMapping("/stats")
    public Map<String, Map<String, Object>> stats() {
        Map<String, Map<String, Object>> base = agentStatsService.stats();
        for (var meta : registry.metas()) {
            String label = meta.label();
            Map<String, Object> entry = base.computeIfAbsent(label, k -> new LinkedHashMap<>());
            entry.put("creditScore", creditScoreService.getOrInit(label));
            entry.put("status", statusService.getStatus(label).name().toLowerCase());
            String task = statusService.getCurrentTask(label);
            if (task != null) {
                entry.put("currentTask", task);
            }
        }
        return base;
    }

    /** GET /api/agents/allocations?limit=20 —— 最近 N 条分配记录（数据更改操作）。 */
    @GetMapping("/allocations")
    public List<Map<String, Object>> allocations(@RequestParam(defaultValue = "20") int limit) {
        return agentStatsService.recent(limit);
    }

    /** POST /api/agents/credit-scores/reset —— 重置所有信用分到初始值。 */
    @PostMapping("/credit-scores/reset")
    public Map<String, Object> resetCreditScores() {
        var labels = registry.metas().stream().map(m -> m.label()).toList();
        creditScoreService.resetAll(labels);
        return Map.of("reset", true, "count", labels.size(), "labels", labels);
    }

    /** GET /api/agents/manager —— 当前（下一轮）管理者身份（冷启动 default）。 */
    @GetMapping("/manager")
    public Map<String, Object> manager() {
        return Map.of("currentManager", electionService.currentManager());
    }

    /** GET /api/agents/elections?limit=20 —— 最近 N 次选举结果（阶段三）。 */
    @GetMapping("/elections")
    public List<Map<String, Object>> elections(@RequestParam(defaultValue = "20") int limit) {
        return electionService.recent(limit).stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("round", e.getRound());
                    m.put("managerRef", blank(e.getManagerRef()));
                    m.put("winner", blank(e.getWinner()));
                    m.put("createdAt", e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
                    m.put("candidates", e.getCandidatesJson() == null ? "[]" : e.getCandidatesJson());
                    return m;
                })
                .toList();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? "" : s;
    }
}