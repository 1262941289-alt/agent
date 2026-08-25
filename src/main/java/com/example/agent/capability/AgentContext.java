package com.example.agent.capability;

import com.example.agent.agent.AgentStep;

import java.util.List;

/**
 * Agent 运行时上下文：Worker 通过此接口向 ManagerAgent 请求信息。
 * <p>只有 ManagerAgent 有权创建实现，Worker 只能读取。
 * <p>核心设计：
 * <ul>
 *   <li>{@link #requestInfo} — Worker 主动向 Manager 要信息（公共上下文入口）</li>
 *   <li>{@link #getConversationHistory} — 多轮会话记忆</li>
 *   <li>{@link #getFileContext} — 上传文件内容</li>
 *   <li>{@link #recallMemory} — 经验/知识图谱召回</li>
 *   <li>{@link #getStepResults} — 其他步骤的执行结果</li>
 *   <li>{@link #getGoal} — 总体目标</li>
 * </ul>
 */
public interface AgentContext {

    /** Worker 向 Manager 请求信息（公共上下文统一入口） */
    String requestInfo(String query);

    /** 获取多轮会话历史 */
    String getConversationHistory();

    /** 获取上传文件内容（可能为空） */
    String getFileContext();

    /** 召回与目标相关的历史经验 */
    String recallMemory(String goal);

    /** 获取其他步骤的执行结果 */
    List<AgentStep> getStepResults();

    /** 获取总体目标 */
    String getGoal();

    /** 空 context（无上下文可用时的 fallback） */
    AgentContext EMPTY = new AgentContext() {
        @Override public String requestInfo(String query) { return ""; }
        @Override public String getConversationHistory() { return ""; }
        @Override public String getFileContext() { return ""; }
        @Override public String recallMemory(String goal) { return ""; }
        @Override public List<AgentStep> getStepResults() { return List.of(); }
        @Override public String getGoal() { return ""; }
    };
}
