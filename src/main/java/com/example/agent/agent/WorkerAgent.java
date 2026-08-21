package com.example.agent.agent;

/**
 * Worker 契约：HLA 编排器把单个子任务分发给实现本接口的执行器。
 * <p>办公能力（文档、消息、日历、任务、邮件等）各自实现本接口并注册为 Spring Bean，
 * 即可被 {@code HierarchicalAgent} 自动发现并按 {@link #name()} 分派。
 */
public interface WorkerAgent {

    /** 执行器唯一名称（分派依据） */
    String name();

    /** 执行器能力描述，供 Planner 拆解时选择 */
    String description();

    /** 执行单个子任务目标 */
    AgentResult run(String goal);
}