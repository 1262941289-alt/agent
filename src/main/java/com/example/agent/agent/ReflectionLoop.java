package com.example.agent.agent;

import com.example.agent.util.PromptRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 反思增强的工具使用循环（Reflection-enhanced Tool Use）。
 * <p>流程：执行 → 反思 → 若不满足目标，则将批评与下一步指令反馈回下一轮，直到达成或耗尽迭代。
 * <p>{@code workerClient} 可绑定任意工具集（含后续接入的办公工具）；反思评估使用无工具的纯推理客户端。
 */
@Component
public class ReflectionLoop {

    /** 最大反思迭代次数，防止无限循环 */
    public static final int MAX_ITERATIONS = 3;

    private final ChatClient reflectionClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReflectionLoop(@Qualifier("planningChatClient") ChatClient reflectionClient) {
        this.reflectionClient = reflectionClient;
    }

    /**
     * 围绕给定目标执行反思循环。
     *
     * @param goal          子任务目标
     * @param workerClient  执行用的 ChatClient（可带工具）
     * @param systemPrompt  执行器的系统提示词
     * @return 执行结果（含反思次数）
     */
    public AgentResult execute(String goal, ChatClient workerClient, String systemPrompt) {
        String context = goal;
        String lastOutput = "";
        int reflections = 0;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            lastOutput = workerClient.prompt()
                    .system(systemPrompt)
                    .user(context)
                    .call()
                    .content();

            Reflection reflection = reflect(goal, lastOutput);
            if (reflection.isSatisfied()) {
                return AgentResult.ok(lastOutput, reflections);
            }
            reflections++;
            context = goal
                    + "\n\n【上一轮输出】\n" + lastOutput
                    + "\n【反思（需改进）】\n" + reflection.getCritique()
                    + "\n【下一步指令】\n" + reflection.getNextAction();
        }
        return AgentResult.fail((lastOutput == null ? "" : lastOutput)
                + "\n[注意] 经 " + MAX_ITERATIONS + " 轮反思仍未满足目标，结果可能不完整。", reflections);
    }

    private Reflection reflect(String goal, String output) {
        String prompt = PromptRenderer.render(
                PromptRenderer.load("prompts/reflector-system.st"),
                Map.of("goal", goal, "output", output == null ? "" : output)
        );
        String response = reflectionClient.prompt().user(prompt).call().content();
        return parseReflection(response);
    }

    private Reflection parseReflection(String text) {
        Reflection r = new Reflection();
        r.setSatisfied(true); // 默认满足，避免解析失败导致死循环
        String json = PromptRenderer.extractJsonObject(text);
        if (json == null) {
            return r;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root != null) {
                if (root.has("satisfied")) {
                    r.setSatisfied(root.path("satisfied").asBoolean(true));
                }
                r.setCritique(root.path("critique").asText(""));
                r.setNextAction(root.path("nextAction").asText(""));
            }
        } catch (Exception ignored) {
            // 解析失败：视为已满足，直接返回当前输出
        }
        return r;
    }
}