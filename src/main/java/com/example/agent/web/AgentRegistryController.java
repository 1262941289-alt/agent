package com.example.agent.web;

import com.example.agent.capability.AgentRegistry;
import com.example.agent.service.AgentStatsService;
import com.example.agent.service.CreditScoreService;
import org.springframework.web.bind.annotation.GetMapping;
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

    public AgentRegistryController(AgentRegistry registry,
                                   CreditScoreService creditScoreService,
                                   AgentStatsService agentStatsService) {
        this.registry = registry;
        this.creditScoreService = creditScoreService;
        this.agentStatsService = agentStatsService;
    }

    /** GET /api/agents/capabilities —— 已注册能力清单。 */
    @GetMapping("/capabilities")
    public List<Map<String, String>> capabilities() {
        return registry.metas().stream()
                .map(m -> Map.of("label", m.label(), "description", m.description()))
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

    /** GET /api/agents/stats —— 各能力 agent 的成功率 / 平均耗时 / 负载（分配次数）。 */
    @GetMapping("/stats")
    public Map<String, Map<String, Object>> stats() {
        return agentStatsService.stats();
    }

    /** GET /api/agents/allocations?limit=20 —— 最近 N 条分配记录（数据更改操作）。 */
    @GetMapping("/allocations")
    public List<Map<String, Object>> allocations(@RequestParam(defaultValue = "20") int limit) {
        return agentStatsService.recent(limit);
    }
}