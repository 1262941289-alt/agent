package com.example.agent.capability;

/**
 * 能力元数据：注册中心对外暴露的（标签、描述、执行器）三元组。
 */
public record CapabilityMeta(String label, String description, CapabilityAgent agent) {
}