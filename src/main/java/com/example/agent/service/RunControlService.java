package com.example.agent.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 运行取消控制：登记进行中的 runId，支持按 runId 或「最近一次」取消。
 * <p>取消是协作式的：ManagerAgent 在循环轮次与执行波次之间检查标志，
 * 当前正在执行的步骤会跑完，之后优雅终止（termination=CANCELLED）。
 */
@Service
public class RunControlService {

    private final Map<String, Boolean> cancelled = new ConcurrentHashMap<>();
    private final AtomicReference<String> latestRunId = new AtomicReference<>();

    public void register(String runId) {
        cancelled.put(runId, Boolean.FALSE);
        latestRunId.set(runId);
    }

    public void unregister(String runId) {
        cancelled.remove(runId);
        latestRunId.compareAndSet(runId, null);
    }

    public boolean isCancelled(String runId) {
        return Boolean.TRUE.equals(cancelled.get(runId));
    }

    /** 取消指定 run；runId 为空时取消最近一次运行的 run。返回实际被取消的 runId，无进行中 run 时返回 null。 */
    public String cancel(String runId) {
        String target = (runId == null || runId.isBlank()) ? latestRunId.get() : runId;
        if (target != null && cancelled.computeIfPresent(target, (k, v) -> Boolean.TRUE) != null) {
            return target;
        }
        return null;
    }
}
