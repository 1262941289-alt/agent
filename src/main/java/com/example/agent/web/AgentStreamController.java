package com.example.agent.web;

import com.example.agent.agent.AgentRunResult;
import com.example.agent.agent.ManagerAgent;
import com.example.agent.memory.ShortTermMemory;
import com.example.agent.service.RunControlService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Agent 流式入口：SSE 实时推送执行轨迹（run / plan / wave / step 事件）。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentStreamController {

    private final ManagerAgent managerAgent;
    private final ShortTermMemory shortTermMemory;
    private final Executor executor;
    private final RunControlService runControl;

    public AgentStreamController(ManagerAgent managerAgent,
                                 ShortTermMemory shortTermMemory,
                                 @Qualifier("agentExecutor") Executor executor,
                                 RunControlService runControl) {
        this.managerAgent = managerAgent;
        this.shortTermMemory = shortTermMemory;
        this.executor = executor;
        this.runControl = runControl;
    }

    /**
     * GET /api/agent/run/stream?goal=...&conversationId=...
     */
    @GetMapping(value = "/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String goal,
                             @RequestParam(required = false) String conversationId,
                             @RequestParam(required = false) String fileContextId) {
        SseEmitter emitter = new SseEmitter(0L);
        String runId = UUID.randomUUID().toString().replace("-", "");
        String history = shortTermMemory.recent(conversationId, 6);

        executor.execute(() -> {
            try {
                AgentRunResult result = managerAgent.execute(
                        goal, history, conversationId, runId, new SseEventSink(emitter), fileContextId);
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

    /**
     * POST /api/agent/stop?runId=...（runId 为空时停止最近一次运行）
     * 协作式停止：当前执行的步骤跑完后，在下一波/下一轮前优雅终止（termination=CANCELLED）。
     */
    @PostMapping("/stop")
    public Map<String, Object> stop(@RequestParam(required = false) String runId) {
        String cancelled = runControl.cancel(runId);
        if (cancelled == null) {
            return Map.of("stopped", false, "message", "没有进行中的运行");
        }
        return Map.of("stopped", true, "runId", cancelled,
                "message", "停止信号已发送，当前步骤执行完后将终止");
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // emitter 可能已因客户端断开而完成，忽略
        }
    }
}