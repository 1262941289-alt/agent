package com.example.agent.web;

import com.example.agent.agent.AgentEvent;
import com.example.agent.agent.AgentEventSink;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * 把事件实时推送给 SSE 客户端。
 * <p>{@code data} 传结构化 Map，由 Jackson 序列化一次；{@code event:} 用事件类型名。
 * <p>异常落点：客户端断开（IOException）或 emitter 已完成（IllegalStateException）时静默处理，
 * 不向上抛出，避免并行后台执行任务因推送失败而中断。
 */
public class SseEventSink implements AgentEventSink {

    private final SseEmitter emitter;

    public SseEventSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void emit(AgentEvent event) {
        synchronized (emitter) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.getType())
                        .data(event.toDataMap(), MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException ex) {
                // 预期的断连/完成异常，静默吞掉
            }
        }
    }
}