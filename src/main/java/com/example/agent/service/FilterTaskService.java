package com.example.agent.service;

import com.example.agent.entity.FilterTaskEntity;
import com.example.agent.model.DataItem;
import com.example.agent.model.FilterResult;
import com.example.agent.repository.FilterTaskRepository;
import com.example.agent.store.DataRepository;
import com.example.agent.store.FilterStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 筛选任务编排：任务的创建、状态机与执行（同步 / 异步）。
 * <p>任务状态机：PENDING → RUNNING → SUCCESS / FAILED。
 * 任务元信息与统计落库到 filter_task 表，明细落库到分层/属性/决策结果表。
 */
@Service
public class FilterTaskService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private final FilterTaskRepository taskRepository;
    private final DataRepository dataRepository;
    private final FilterAgentService filterAgentService;
    private final FilterStore filterStore;
    private final TaskExecutor filterTaskExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FilterTaskService(FilterTaskRepository taskRepository,
                             DataRepository dataRepository,
                             FilterAgentService filterAgentService,
                             FilterStore filterStore,
                             @Qualifier("filterTaskExecutor") TaskExecutor filterTaskExecutor) {
        this.taskRepository = taskRepository;
        this.dataRepository = dataRepository;
        this.filterAgentService = filterAgentService;
        this.filterStore = filterStore;
        this.filterTaskExecutor = filterTaskExecutor;
    }

    /**
     * 提交异步任务：立即返回任务元信息（含 taskId），后台线程池执行。
     */
    public FilterTaskEntity submit(List<String> itemIds, String sourceType) {
        List<String> ids = resolveItemIds(itemIds);
        FilterTaskEntity task = createTask(ids, sourceType);
        filterTaskExecutor.execute(() -> execute(task.getTaskId(), ids));
        return task;
    }

    /**
     * 同步执行：阻塞直到完成，返回完整筛选结果。
     */
    public FilterResult runSync(List<String> itemIds) {
        List<String> ids = resolveItemIds(itemIds);
        FilterTaskEntity task = createTask(ids, null);
        return execute(task.getTaskId(), ids);
    }

    /**
     * 查询任务元信息与统计（状态、数量、耗时、错误信息等）。
     */
    public FilterTaskEntity get(String taskId) {
        return taskRepository.findById(taskId).orElse(null);
    }

    /**
     * 查询任务完整结果明细（分层 / 属性 / 决策）。
     */
    public FilterResult result(String taskId) {
        FilterTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        FilterResult r = new FilterResult();
        r.setTaskId(taskId);
        r.setTotal(task.getTotal());
        r.setPassed(task.getPassed());
        r.setRejected(task.getRejected());
        r.setFailed(task.getFailed());
        r.setCached(task.getCached());
        r.setCostMs(task.getCostMs());
        r.setLayers(filterStore.layersOf(taskId));
        r.setAttributes(filterStore.attributesOf(taskId));
        r.setDecisions(filterStore.decisionsOf(taskId));
        return r;
    }

    /** 未指定数据项时，默认筛选全量待处理数据 */
    private List<String> resolveItemIds(List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return dataRepository.findAll().stream().map(DataItem::getId).toList();
        }
        return itemIds;
    }

    private FilterTaskEntity createTask(List<String> itemIds, String sourceType) {
        FilterTaskEntity task = new FilterTaskEntity();
        task.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        task.setSourceType(sourceType == null || sourceType.isBlank() ? "MIX" : sourceType);
        task.setStatus(STATUS_PENDING);
        task.setTotal(itemIds.size());
        task.setItemIdsJson(writeJson(itemIds));
        task.setCreatedAt(Instant.now());
        return taskRepository.save(task);
    }

    private FilterResult execute(String taskId, List<String> itemIds) {
        FilterTaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return null;
        }
        task.setStatus(STATUS_RUNNING);
        task.setStartedAt(Instant.now());
        taskRepository.save(task);

        try {
            FilterResult result = filterAgentService.process(taskId, itemIds);
            task.setPassed((int) result.getPassed());
            task.setRejected((int) result.getRejected());
            task.setFailed((int) result.getFailed());
            task.setCached((int) result.getCached());
            task.setCostMs(result.getCostMs());
            task.setStatus(STATUS_SUCCESS);
            task.setFinishedAt(Instant.now());
            taskRepository.save(task);
            return result;
        } catch (Exception ex) {
            task.setStatus(STATUS_FAILED);
            task.setErrorMsg(ex.getMessage());
            task.setFinishedAt(Instant.now());
            taskRepository.save(task);
            throw new IllegalStateException("任务执行失败: " + taskId, ex);
        }
    }

    private String writeJson(List<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            return "[]";
        }
    }
}