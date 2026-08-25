package com.example.agent.config;

import com.example.agent.agent.AgentEvent;
import com.example.agent.agent.AgentEventSink;
import com.example.agent.service.RunEventService;

import java.util.List;
import java.util.Map;

/**
 * 当前执行的线程本地上下文：把执行 runId、事件收集列表、SSE sink 与持久化服务下放到
 * 工具调用栈（工具在 worker 线程内被 Spring AI 同步回调，恰好与 {@code runStep} 同一线程）。
 * <p>作用：让被守护的 {@code GuardedToolCallback} 也能把 tool:call / tool:approval-request 等
 * 事件送入主流程的 SSE 流与持久化日志，无需把 sink 层层传参。
 */
public final class RunContext {

    private static final ThreadLocal<RunContext> CURRENT = new ThreadLocal<>();

    private final String runId;
    private final List<AgentEvent> events;
    private final AgentEventSink sink;
    private final RunEventService runEventService;

    public RunContext(String runId, List<AgentEvent> events, AgentEventSink sink,
                      RunEventService runEventService) {
        this.runId = runId;
        this.events = events;
        this.sink = sink;
        this.runEventService = runEventService;
    }

    public static void set(RunContext ctx) {
        CURRENT.set(ctx);
    }

    /** 当前线程可能不在执行中（如仅规划/反思的调用），此时为 null，调用方需判空。 */
    public static RunContext current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** 复用主流程 emit 语义：内存收集 + 持久化 + SSE 推送。 */
    public void emit(String type, Map<String, Object> data) {
        AgentEvent event = new AgentEvent(type, runId, data);
        if (events != null) {
            events.add(event);
        }
        if (runEventService != null) {
            runEventService.append(runId, type, data);
        }
        if (sink != null) {
            sink.emit(event);
        }
    }

    public String getRunId() {
        return runId;
    }
}