package com.example.agent.web;

import com.example.agent.agent.AgentRunResult;
import com.example.agent.agent.HierarchicalAgent;
import com.example.agent.memory.ShortTermMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Agent 流式入口：SSE 实时推送执行轨迹（run / plan / wave / step 事件）。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentStreamController {

    private final HierarchicalAgent hierarchicalAgent;
    private final ShortTermMemory shortTermMemory;
    private final Executor executor;

    public AgentStreamController(HierarchicalAgent hierarchicalAgent,
                                 ShortTermMemory shortTermMemory,
                                 @Qualifier("agentExecutor") Executor executor) {
        this.hierarchicalAgent = hierarchicalAgent;
        this.shortTermMemory = shortTermMemory;
        this.executor = executor;
    }

    /**
     * GET /api/agent/run/stream?goal=...&conversationId=...
     */
    @GetMapping(value = "/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String goal,
                             @RequestParam(required = false) String conversationId) {
        SseEmitter emitter = new SseEmitter(0L);
        String runId = UUID.randomUUID().toString().replace("-", "");
        String history = shortTermMemory.recent(conversationId, 6);

        executor.execute(() -> {
            try {
                AgentRunResult result = hierarchicalAgent.execute(
                        goal, history, conversationId, runId, new SseEventSink(emitter));
                shortTermMemory.add(conversationId, "用户: " + goal);
                shortTermMemory.add(conversationId, "助手: " + result.getFinalAnswer());
            } catch (Exception ignored) {
                // execute 内部已 emit run:failed，此处兜底，避免后台任务未完成
            } finally {
                safeComplete(emitter);
            }
        });
        return emitter;
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // emitter 可能已因客户端断开而完成，忽略
        }
    }
}