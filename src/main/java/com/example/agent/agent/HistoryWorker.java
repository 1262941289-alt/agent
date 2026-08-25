package com.example.agent.agent;

import com.example.agent.capability.AgentContext;
import com.example.agent.capability.Capability;
import com.example.agent.capability.CapabilityAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 历史节点能力 agent（经验·自学习）：召回历史经验/人工标注，并沉淀可复用的学习结论。
 * <p>平级化：与其他能力 agent 一样注册进 AgentRegistry，可被 Manager 按能力路由。
 * <p>通过 historyChatClient 绑定经验召回/沉淀工具；不注入 Memory（读权保留给 Manager）。
 */
@Component
@Capability(label = "history", description = "经验/自学习助手：召回历史经验与人工标注，沉淀可复用的学习结论到知识图谱", style = "BALANCED")
public class HistoryWorker implements CapabilityAgent {

    private static final String SYSTEM_PROMPT = """
            你是一个经验/自学习助手，负责从历史执行中提取可复用的知识与教训。
            规则：
            1. 需要参考历史时调用 recallExperience 召回相关经验与人工标注。
            2. 需要沉淀结论时调用 recordLesson 写入知识图谱（type 用 EXPERIENCE 表成功经验 / PITFALL 表失败教训）。
            3. 用简洁中文给出：召回到哪些相关经验、可复用的结论与建议。
            """;

    private final ReflectionLoop reflectionLoop;
    private final ChatClient historyClient;

    public HistoryWorker(ReflectionLoop reflectionLoop,
                         @Qualifier("historyChatClient") ChatClient historyClient) {
        this.reflectionLoop = reflectionLoop;
        this.historyClient = historyClient;
    }

    @Override
    public AgentResult run(String goal) {
        return reflectionLoop.execute(goal, historyClient, SYSTEM_PROMPT);
    }

    @Override
    public AgentResult run(String goal, AgentContext context) {
        return reflectionLoop.execute(goal, historyClient, SYSTEM_PROMPT, context);
    }
}