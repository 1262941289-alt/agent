package com.example.agent.capability;

/**
 * 能力元数据：标签、描述、角色风格、执行器。
 */
public record CapabilityMeta(String label, String description, AgentStyle style, CapabilityAgent agent) {
}
