package com.example.agent.agent;

import com.example.agent.capability.AgentContext;
import com.example.agent.capability.Capability;
import com.example.agent.capability.CapabilityAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 通用能力 agent：处理没有专用工具的通用办公任务，内部走反思增强循环。
 * <p>记忆读权已收归 Manager，本 agent 只执行，不直接 recall。
 */
@Component
@Capability(label = "general", description = "通用办公助手：处理没有专用工具的通用任务（写作、总结、规划、问答等）", style = "EFFICIENT")
public class GeneralWorker implements CapabilityAgent {

    private static final String SYSTEM_PROMPT =
            "你是一个通用办公助手。请直接、准确地完成用户给定的目标，给出可用的结果，不要多余解释。";

    private final ReflectionLoop reflectionLoop;
    private final ChatClient workerClient;

    public GeneralWorker(ReflectionLoop reflectionLoop,
                         @Qualifier("planningChatClient") ChatClient workerClient) {
        this.reflectionLoop = reflectionLoop;
        this.workerClient = workerClient;
    }

    @Override
    public AgentResult run(String goal) {
        return reflectionLoop.execute(goal, workerClient, SYSTEM_PROMPT);
    }

    @Override
    public AgentResult run(String goal, AgentContext context) {
        return reflectionLoop.execute(goal, workerClient, SYSTEM_PROMPT, context);
    }
}