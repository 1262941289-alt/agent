package com.example.agent.capability;

/**
 * Agent 角色风格：影响任务分配偏好和执行策略。
 * <p>风格是固定的角色特征，不随任务变化，确保多 Agent 协作中有差异化分工。
 */
public enum AgentStyle {

    /** 谨慎型：优先接低风险任务，执行前充分检查，适合数据验证、审批类任务 */
    CAUTIOUS("谨慎", "优先接低风险任务，执行前充分检查，适合数据验证、审批类"),

    /** 激进型：敢于接高难度任务，快速推进，适合探索性、开拓性任务 */
    AGGRESSIVE("激进", "敢于接高难度任务，快速推进，适合探索性、开拓性"),

    /** 平衡型：风险和效率兼顾，适合常规协作和跨域协调 */
    BALANCED("平衡", "风险和效率兼顾，适合常规协作和跨域协调"),

    /** 高效型：追求最快完成，优先接短任务，适合简单确认、快速查询 */
    EFFICIENT("高效", "追求最快完成，优先接短任务，适合简单确认、快速查询");

    private final String displayName;
    private final String description;

    AgentStyle(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
