package com.example.agent.web;

import com.example.agent.capability.AgentRegistry;
import com.example.agent.service.CreditScoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力注册中心与信用分开销入口。
 */
@RestController
@RequestMapping("/api/agents")
public class AgentRegistryController {

    private final AgentRegistry registry;
    private final CreditScoreService creditScoreService;

    public AgentRegistryController(AgentRegistry registry, CreditScoreService creditScoreService) {
        this.registry = registry;
        this.creditScoreService = creditScoreService;
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
}