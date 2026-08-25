package com.example.agent.web;

import com.example.agent.service.RunEventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * run 事件回放入口：历史运行列表 + 单次运行有序回放（复用前端 handleEvent 渲染）。
 */
@RestController
@RequestMapping("/api/agent/run")
public class RunHistoryController {

    private final RunEventService runEventService;

    public RunHistoryController(RunEventService runEventService) {
        this.runEventService = runEventService;
    }

    /** GET /api/agent/run/history?limit=20 —— 最近若干次运行摘要（新→旧）。 */
    @GetMapping("/history")
    public List<Map<String, Object>> history(@RequestParam(defaultValue = "20") int limit) {
        return runEventService.history(limit);
    }

    /** GET /api/agent/run/replay/{runId} —— 单次运行有序事件，结构同 SSE 事件。 */
    @GetMapping("/replay/{runId}")
    public List<Map<String, Object>> replay(@PathVariable String runId) {
        return runEventService.replay(runId);
    }
}