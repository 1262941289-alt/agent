package com.example.agent.capability;

import com.example.agent.agent.AgentResult;

/**
 * 能力域 agent 契约：按子任务目标执行。
 * <p>与旧 {@code WorkerAgent} 的区别：身份信息（标签/描述）由 {@link Capability} 注解承载，
 * 接口只保留执行契约；执行时由 agent 自主调用其绑定的工具。
 */
public interface CapabilityAgent {

    /** 执行单个子任务目标 */
    AgentResult run(String goal);
}