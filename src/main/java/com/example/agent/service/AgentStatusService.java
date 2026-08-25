package com.example.agent.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 实时状态追踪：记录各能力域 Agent 的当前运行状态（working/idle/error）。
 * <p>由 ManagerAgent 在步骤执行前后调用 markWorking/markIdle 更新，
 * 前端通过 /api/agents/status 轮询展示。
 */
@Service
public class AgentStatusService {

    public enum Status { IDLE, WORKING, ERROR }

    private final Map<String, Status> statuses = new ConcurrentHashMap<>();
    private final Map<String, String> currentTasks = new ConcurrentHashMap<>();

    public void markWorking(String agentLabel, String taskSummary) {
        statuses.put(agentLabel, Status.WORKING);
        currentTasks.put(agentLabel, taskSummary);
    }

    public void markIdle(String agentLabel) {
        statuses.put(agentLabel, Status.IDLE);
        currentTasks.remove(agentLabel);
    }

    public void markError(String agentLabel, String errorMsg) {
        statuses.put(agentLabel, Status.ERROR);
        currentTasks.put(agentLabel, errorMsg);
    }

    public Status getStatus(String agentLabel) {
        return statuses.getOrDefault(agentLabel, Status.IDLE);
    }

    public String getCurrentTask(String agentLabel) {
        return currentTasks.get(agentLabel);
    }

    public Map<String, String> statusMap() {
        Map<String, String> out = new ConcurrentHashMap<>();
        statuses.forEach((label, status) -> out.put(label, status.name().toLowerCase()));
        return out;
    }

    public Map<String, String> taskMap() {
        return new ConcurrentHashMap<>(currentTasks);
    }
}
