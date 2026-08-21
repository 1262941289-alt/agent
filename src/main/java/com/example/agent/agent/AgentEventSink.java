package com.example.agent.agent;

/**
 * 事件输出的目的地抽象：同步执行（仅内部收集）与 SSE 实时推送共用同一套执行逻辑。
 * <p>实现方负责把结构化事件序列化到具体通道；异常（如客户端断开）应在实现内处理，不得影响任务执行。
 */
public interface AgentEventSink {

    void emit(AgentEvent event);
}