package com.example.agent.web;

import com.example.agent.model.Decision;
import com.example.agent.model.FilterResult;
import com.example.agent.service.FilterAgentService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 筛选 Agent REST 接口。
 */
@RestController
@RequestMapping("/api/filter")
public class FilterController {

    private final FilterAgentService filterAgentService;

    public FilterController(FilterAgentService filterAgentService) {
        this.filterAgentService = filterAgentService;
    }

    /**
     * 对所有数据执行完整三阶段筛选。
     * POST /api/filter/run
     */
    @PostMapping("/run")
    public FilterResult runAll() {
        return filterAgentService.filterAll(UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * 对单个数据项执行三阶段筛选。
     * POST /api/filter/run/{itemId}
     */
    @PostMapping("/run/{itemId}")
    public Decision runOne(@PathVariable String itemId) {
        // 先清空，再跑单条
        return filterAgentService.filterOne(itemId);
    }
}