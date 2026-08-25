package com.example.agent.service;

import com.example.agent.config.RunContext;
import com.example.agent.config.ToolGuardConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 工具守护执行管线（pre 审计 → 审批门 → 超时执行 → post 审计）。
 * <p>所有经 {@code GuardedToolCallback} 包装的工具调用统一从这里走，保证单点策略生效。
 */
@Service
public class ToolExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionService.class);

    private static final int MAX_ARGS = 500;

    private final AuditLogService auditLogService;
    private final ApprovalService approvalService;
    private final ToolGuardConfig guardConfig;
    private final Executor toolExecutor;

    public ToolExecutionService(AuditLogService auditLogService,
                                ApprovalService approvalService,
                                ToolGuardConfig guardConfig,
                                @Qualifier("toolExecutor") Executor toolExecutor) {
        this.auditLogService = auditLogService;
        this.approvalService = approvalService;
        this.guardConfig = guardConfig;
        this.toolExecutor = toolExecutor;
    }

    /**
     * 执行单次工具调用：先审计与审批（如需），再限时执行，最后审计结果。
     * 无论成功/失败/拒绝/超时都返回一段字符串，交给 LLM 判读（不抛异常导致 step 崩溃）。
     */
    public String execute(ToolCallback delegate, String toolInput, ToolContext toolContext) {
        String toolName = delegate.getToolDefinition().name();
        String args = summarize(toolInput);
        RunContext run = RunContext.current();
        ToolGuardConfig.Policy policy = guardConfig.policyFor(toolName);

        if (run != null) {
            run.emit("tool:call", Map.of(
                    "tool", toolName, "args", args, "requiresApproval", policy.isRequiresApproval()));
        }
        auditNow(toolName, "ATTEMPT", args, run, "pending");

        // 审批门（仅命中风险策略的工具进入，阻塞等待人工裁决）
        if (policy.isRequiresApproval()) {
            boolean granted = approvalService.gate(run, toolName, args);
            if (run != null) {
                run.emit("tool:decision", Map.of("tool", toolName, "granted", granted));
            }
            if (!granted) {
                auditNow(toolName, "DENIED", args, run, "denied");
                return "[工具已拒绝] 调用 " + toolName + " 未被人工批准，请改走只读方案或向用户确认。";
            }
        }

        long timeoutMs = Math.max(1L, Math.min(policy.getTimeoutSeconds(), 3600L)) * 1000L;
        ExecutorCompletionService<String> ecs = new ExecutorCompletionService<>(toolExecutor);
        Callable<String> call = () -> toolContext == null
                ? delegate.call(toolInput)
                : delegate.call(toolInput, toolContext);
        ecs.submit(call);
        Future<String> f;
        try {
            f = ecs.take();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            auditNow(toolName, "ERROR", args, run, "interrupted");
            return "[工具执行中断] " + toolName + " 的调用被中断。";
        }
        try {
            String result = f.get(timeoutMs, TimeUnit.MILLISECONDS);
            auditNow(toolName, "SUCCESS", args, run, clip(result));
            if (run != null) {
                run.emit("tool:result", Map.of("tool", toolName, "status", "SUCCESS", "output", clip(result)));
            }
            return result;
        } catch (TimeoutException te) {
            f.cancel(true);
            auditNow(toolName, "TIMEOUT", args, run, "timeout:" + timeoutMs + "ms");
            if (run != null) {
                run.emit("tool:result", Map.of("tool", toolName, "status", "TIMEOUT"));
            }
            return "[工具超时] 调用 " + toolName + " 超过 " + timeoutMs + "ms 未返回，已终止该次调用。";
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            auditNow(toolName, "ERROR", args, run, clip(msg));
            if (run != null) {
                run.emit("tool:result", Map.of("tool", toolName, "status", "ERROR", "output", clip(msg)));
            }
            return "[工具执行异常] " + toolName + ": " + msg;
        }
    }

    private void auditNow(String toolName, String action, String args, RunContext run, String detail) {
        try {
            auditLogService.record("TOOL", action, "tool", toolName,
                    summaryText(toolName, args, action), String.valueOf(detail),
                    "agent:" + toolName, run == null ? null : run.getRunId());
        } catch (Exception e) {
            log.warn("工具审计写入失败 tool={} action={}: {}", toolName, action, e.getMessage());
        }
    }

    private static String summarize(String toolInput) {
        return clip(toolInput);
    }

    private static String summaryText(String toolName, String args, String action) {
        return "工具 " + toolName + " " + action + " | 入参: " + args;
    }

    private static String clip(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > MAX_ARGS ? s.substring(0, MAX_ARGS) + "…" : s;
    }
}