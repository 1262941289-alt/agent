package com.example.agent.capability;

import com.example.agent.agent.AgentResult;

/**
 * 能力域 agent 契约：按子任务目标执行。
 * <p>Worker 执行时可通过 {@link AgentContext} 向 ManagerAgent 请求公共上下文信息。
 */
public interface CapabilityAgent {

    /** 执行单个子任务目标（无上下文，兼容旧调用） */
    default AgentResult run(String goal) {
        return run(goal, AgentContext.EMPTY);
    }

    /** 执行单个子任务目标，携带 ManagerAgent 下发的运行时上下文 */
    AgentResult run(String goal, AgentContext context);
}
