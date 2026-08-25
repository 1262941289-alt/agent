package com.example.agent.service;

import com.example.agent.config.RunContext;
import com.example.agent.config.ToolGuardConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 审批/人机门：高风险工具调用在真正执行前推送 {@code tool:approval-request} 事件，
 * 阻塞等待人工在 run.html 上批准/拒绝，超时按配置默认放行或拒绝。
 * <p>只阻塞被审批的那个工具调用，不影响同波其它 step。
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT;

    /** 进行中的审批请求。 */
    private static class Pending {
        private final CompletableFuture<Boolean> future = new CompletableFuture<>();
        private final String toolName;
        private final String argsSummary;
        private final String runId;
        private final Instant createdAt = Instant.now();
        private volatile boolean decided;

        Pending(String toolName, String argsSummary, String runId) {
            this.toolName = toolName;
            this.argsSummary = argsSummary;
            this.runId = runId;
        }
    }

    private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();
    private final ToolGuardConfig guardConfig;

    public ApprovalService(ToolGuardConfig guardConfig) {
        this.guardConfig = guardConfig;
    }

    /**
     * 审批闸门：不需审批则立即放行；需审批则推送事件并阻塞等待人工裁决。
     *
     * @return true=放行执行，false=拒绝（调用方应返回“已拒绝”给 LLM）
     */
    public boolean gate(RunContext run, String toolName, String argsSummary) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        Pending p = new Pending(toolName, argsSummary, run == null ? "" : run.getRunId());
        pending.put(requestId, p);
        if (run != null) {
            run.emit("tool:approval-request", Map.of(
                    "requestId", requestId, "tool", toolName, "args", argsSummary));
        }
        boolean granted;
        try {
            granted = p.future.get(guardConfig.getApprovalTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            // 超时或等待被中断：按配置决定默认放行或拒绝
            granted = guardConfig.isApprovalTimeoutContinue();
            log.warn("审批等待超时 requestId={} tool={}，默认放行={}",
                    requestId, toolName, granted);
        }
        pending.remove(requestId, p);
        return granted;
    }

    /** 裁决某次审批请求。返回 false 表示请求不存在或已被裁决。 */
    public boolean decide(String requestId, boolean approve) {
        Pending p = pending.get(requestId);
        if (p == null) {
            return false;
        }
        if (p.decided) {
            return false;
        }
        p.decided = true;
        p.future.complete(approve);
        return true;
    }

    /** 进行中的审批请求列表（前端轮询常驻，或由 tool:approval-request 事件驱动后刷新）。 */
    public List<Map<String, Object>> pendingList() {
        return pending.entrySet().stream()
                .map(e -> toMap(e.getKey(), e.getValue()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static Map<String, Object> toMap(String id, Pending p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requestId", id);
        m.put("tool", p.toolName);
        m.put("args", p.argsSummary);
        m.put("runId", p.runId);
        m.put("createdAt", TS.format(p.createdAt));
        return m;
    }
}