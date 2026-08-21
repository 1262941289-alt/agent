package com.example.agent.agent;

import com.example.agent.memory.Memory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通用 Worker：处理没有专用工具的通用办公任务，内部走反思增强循环。
 * <p>执行前先从长期记忆召回相关知识，增强上下文；后续接入办公工具时，
 * 可将 {@code workerClient} 替换为绑定了工具集的 ChatClient。
 */
@Component
public class GeneralWorker implements WorkerAgent {

    private static final String SYSTEM_PROMPT =
            "你是一个通用办公助手。请直接、准确地完成用户给定的目标，给出可用的结果，不要多余解释。";

    private final ReflectionLoop reflectionLoop;
    private final ChatClient workerClient;
    private final Memory memory;

    public GeneralWorker(ReflectionLoop reflectionLoop,
                         @Qualifier("planningChatClient") ChatClient workerClient,
                         Memory memory) {
        this.reflectionLoop = reflectionLoop;
        this.workerClient = workerClient;
        this.memory = memory;
    }

    @Override
    public String name() {
        return "general";
    }

    @Override
    public String description() {
        return "通用办公助手：处理没有专用工具的通用任务（写作、总结、规划、问答等）";
    }

    @Override
    public AgentResult run(String goal) {
        List<String> recalled = memory.recall(goal, 3);
        String enriched = goal;
        if (!recalled.isEmpty()) {
            enriched = goal + "\n\n（已积累的相关知识，供参考）\n" + String.join("\n", recalled);
        }
        return reflectionLoop.execute(enriched, workerClient, SYSTEM_PROMPT);
    }
}