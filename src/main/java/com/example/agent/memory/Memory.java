package com.example.agent.memory;

import java.util.List;

/**
 * Agent 记忆抽象：短期/长期记忆的统一入口。
 * <p>当前由 {@link GraphMemory} 提供长期记忆（知识图谱累积）；短期记忆可另行实现为会话态实现。
 */
public interface Memory {

    /** 依据查询召回相关知识（长期记忆检索） */
    List<String> recall(String query, int k);

    /** 写入一条知识（累积工作知识） */
    void remember(String type, String name, String content);
}