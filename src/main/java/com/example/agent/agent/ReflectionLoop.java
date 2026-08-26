package com.example.agent.agent;

import com.example.agent.capability.AgentContext;
import com.example.agent.tools.GuardedToolCallback;
import com.example.agent.util.PromptRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 反思增强的工具使用循环（Reflection-enhanced Tool Use）。
 * <p>流程：执行 → 反思 → 若不满足目标，则将批评与下一步指令反馈回下一轮，直到达成或耗尽迭代。
 * <p>每次“未满足”的反思会记入 {@code reflectionTrail}，供编排器发射精简的 step:reflection 事件。
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
     * @return 执行结果（含反思次数与反思轨迹）
     */
    public AgentResult execute(String goal, ChatClient workerClient, String systemPrompt) {
        return execute(goal, workerClient, systemPrompt, AgentContext.EMPTY);
    }

    /**
     * 围绕给定目标执行反思循环，携带 ManagerAgent 下发的运行时上下文。
     * <p>Worker 可通过 context 向 Manager 请求公共信息（会话历史、文件内容、经验记忆等）。
     */
    public AgentResult execute(String goal, ChatClient workerClient, String systemPrompt, AgentContext ctx) {
        String managerContext = buildManagerContext(goal, ctx);
        String context = goal + (managerContext.isBlank() ? "" : "\n\n" + managerContext);
        String lastOutput = "";
        int reflections = 0;
        List<Reflection> trail = new ArrayList<>();

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            GuardedToolCallback.resetTurn();
            lastOutput = workerClient.prompt()
                    .system(systemPrompt)
                    .user(context)
                    .call()
                    .content();

            Reflection reflection = reflect(goal, lastOutput);
            if (reflection.isSatisfied()) {
                return AgentResult.ok(lastOutput, reflections, trail);
            }
            reflections++;
            trail.add(reflection);
            context = goal
                    + (managerContext.isBlank() ? "" : "\n\n" + managerContext)
                    + "\n\n【上一轮输出】\n" + lastOutput
                    + "\n【反思（需改进）】\n" + reflection.getCritique()
                    + "\n【下一步指令】\n" + reflection.getNextAction();
        }
        return AgentResult.fail((lastOutput == null ? "" : lastOutput)
                + "\n[注意] 经 " + MAX_ITERATIONS + " 轮反思仍未满足目标，结果可能不完整。", reflections, trail);
    }

    /** 从 AgentContext 构建注入 Worker 的公共上下文 */
    private String buildManagerContext(String goal, AgentContext ctx) {
        if (ctx == null || ctx == AgentContext.EMPTY) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String history = ctx.getConversationHistory();
        if (history != null && !history.isBlank()) {
            sb.append("【会话历史】\n").append(history).append("\n\n");
        }
        String fileCtx = ctx.getFileContext();
        if (fileCtx != null && !fileCtx.isBlank()) {
            sb.append("【文件上下文】\n").append(fileCtx).append("\n\n");
        }
        String memory = ctx.recallMemory(goal);
        if (memory != null && !memory.isBlank()) {
            sb.append("【相关经验】\n").append(memory).append("\n\n");
        }
        var stepResults = ctx.getStepResults();
        if (stepResults != null && !stepResults.isEmpty()) {
            StringBuilder steps = new StringBuilder();
            for (var s : stepResults) {
                if (s.getStatus() != null && !"PENDING".equals(s.getStatus())) {
                    steps.append("[步骤").append(s.getStep()).append(" ")
                          .append(s.getStatus()).append("] ")
                          .append(s.getGoal() == null ? "" : s.getGoal().substring(0, Math.min(80, s.getGoal().length())))
                          .append(" → ").append(s.getResult() == null ? "" : s.getResult().substring(0, Math.min(200, s.getResult().length())))
                          .append("\n");
                }
            }
            if (steps.length() > 0) {
                sb.append("【其他步骤结果】\n").append(steps).append("\n");
            }
        }
        return sb.toString().trim();
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