package com.example.agent.web;

import com.example.agent.entity.FilterTaskEntity;
import com.example.agent.model.FilterResult;
import com.example.agent.service.FilterTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 筛选任务 REST 接口：提交 / 同步执行 / 查询任务。
 */
@RestController
@RequestMapping("/api/filter")
public class FilterController {

    private final FilterTaskService filterTaskService;

    public FilterController(FilterTaskService filterTaskService) {
        this.filterTaskService = filterTaskService;
    }

    /**
     * 提交异步筛选任务（不传 itemIds 则筛选全量待处理数据）。
     * POST /api/filter/tasks
     */
    @PostMapping("/tasks")
    public FilterTaskEntity submit(@RequestBody(required = false) SubmitRequest request) {
        return filterTaskService.submit(
                request == null ? null : request.itemIds(),
                request == null ? null : request.sourceType());
    }

    /**
     * 同步执行筛选并返回完整结果。
     * POST /api/filter/tasks/run
     */
    @PostMapping("/tasks/run")
    public FilterResult runSync(@RequestBody(required = false) SubmitRequest request) {
        return filterTaskService.runSync(request == null ? null : request.itemIds());
    }

    /**
     * 查询任务状态与统计。
     * GET /api/filter/tasks/{taskId}
     */
    @GetMapping("/tasks/{taskId}")
    public FilterTaskEntity get(@PathVariable String taskId) {
        return filterTaskService.get(taskId);
    }

    /**
     * 查询任务完整结果明细（分层 / 属性 / 决策）。
     * GET /api/filter/tasks/{taskId}/result
     */
    @GetMapping("/tasks/{taskId}/result")
    public FilterResult result(@PathVariable String taskId) {
        return filterTaskService.result(taskId);
    }

    /** 任务提交请求体 */
    public record SubmitRequest(List<String> itemIds, String sourceType) {
    }
}