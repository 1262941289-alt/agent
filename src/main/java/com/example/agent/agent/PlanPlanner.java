package com.example.agent.agent;

import com.example.agent.capability.CapabilityMeta;
import com.example.agent.service.CreditScoreService;
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
 * 规划器：将总体目标拆解为子任务步骤（LLM 输出 JSON 计划），支持步骤依赖、记忆注入与失败重规划。
 * <p>输入由旧 {@code WorkerAgent} 名称列表改为能力清单 {@link CapabilityMeta}，按能力标签分派。
 */
@Component
public class PlanPlanner {

    private final ChatClient planningClient;
    private final CreditScoreService creditScoreService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlanPlanner(@Qualifier("planningChatClient") ChatClient planningClient,
                       CreditScoreService creditScoreService) {
        this.planningClient = planningClient;
        this.creditScoreService = creditScoreService;
    }

    /**
     * 拆解目标为步骤列表（不注入记忆）。
     */
    public List<AgentStep> plan(String goal, List<CapabilityMeta> capabilities) {
        return plan(goal, capabilities, "");
    }

    /**
     * 拆解目标为步骤列表，并把 Manager 召回的记忆拼进提示词指导规划。
     */
    public List<AgentStep> plan(String goal, List<CapabilityMeta> capabilities, String memory) {
        String prompt = PromptRenderer.render(
                PromptRenderer.load("prompts/planner-system.st"),
                Map.of("goal", goal, "workers", renderCapabilities(capabilities),
                        "experience", memory == null ? "" : memory)
        );
        String response = planningClient.prompt().user(prompt).call().content();
        return parseSteps(response);
    }

    /**
     * 递归主循环的每轮决策：基于已执行步骤的真实结果（环境观察）判定 DONE/CONTINUE/ABORT，
     * 并在 CONTINUE 时产出下一批步骤（补救或追加）。步骤序号为批次内局部序号，由 Manager 重编。
     */
    public IterationDecision decideNext(int iteration, String goal, List<AgentStep> steps,
                                        List<CapabilityMeta> capabilities, String memory) {
        String prompt = PromptRenderer.render(
                PromptRenderer.load("prompts/iterator-system.st"),
                Map.of(
                        "iteration", String.valueOf(iteration),
                        "lastStep", String.valueOf(lastStepNumber(steps)),
                        "goal", goal,
                        "workers", renderCapabilities(capabilities),
                        "experience", memory == null ? "" : memory,
                        "observation", renderObservation(steps)
                )
        );
        String response = planningClient.prompt().user(prompt).call().content();
        return parseDecision(response);
    }

    private int lastStepNumber(List<AgentStep> steps) {
        return steps.stream().mapToInt(AgentStep::getStep).max().orElse(0);
    }

    private String renderObservation(List<AgentStep> steps) {
        StringBuilder sb = new StringBuilder();
        for (AgentStep s : steps) {
            sb.append("[步骤").append(s.getStep()).append(' ').append(s.getStatus()).append("] ")
                    .append(s.getGoal()).append('\n')
                    .append("  结果: ").append(clipResult(s.getResult())).append('\n');
        }
        return sb.toString();
    }

    private String clipResult(String r) {
        if (r == null || r.isBlank()) {
            return "（无输出）";
        }
        String t = r.trim().replace("\n", " ");
        return t.length() > 600 ? t.substring(0, 600) + "…（截断）" : t;
    }

    private IterationDecision parseDecision(String text) {
        IterationDecision d = new IterationDecision();
        String json = PromptRenderer.extractJsonObject(text);
        if (json != null) {
            try {
                JsonNode root = objectMapper.readTree(json);
                String dec = root.path("decision").asText("CONTINUE").trim().toUpperCase();
                if ("DONE".equals(dec) || "ABORT".equals(dec) || "CONTINUE".equals(dec)) {
                    d.setDecision(dec);
                }
                d.setReason(root.path("reason").asText(""));
                d.setSteps(parseSteps(json));
            } catch (Exception ignored) {
                // 解析失败保持默认 CONTINUE，由空批次兜底转 DONE
            }
        }
        return d;
    }

    private String renderCapabilities(List<CapabilityMeta> capabilities) {
        StringBuilder sb = new StringBuilder();
        for (CapabilityMeta c : capabilities) {
            int score = creditScoreService.getOrInit(c.label());
            sb.append("- ").append(c.label())
              .append("（风格:").append(c.style().getDisplayName())
              .append(", 信用:").append(score).append("）: ")
              .append(c.description()).append("\n");
        }
        return sb.toString();
    }

    private List<AgentStep> parseSteps(String text) {
        List<AgentStep> steps = new ArrayList<>();
        String json = PromptRenderer.extractJsonObject(text);
        if (json != null) {
            try {
                JsonNode root = objectMapper.readTree(json);
                JsonNode arr = root != null ? root.path("steps") : null;
                if (arr != null && arr.isArray()) {
                    int idx = 1;
                    for (JsonNode n : arr) {
                        AgentStep s = new AgentStep();
                        s.setStep(idx++);
                        s.setGoal(n.path("goal").asText("").trim());
                        String worker = n.path("worker").asText(null);
                        s.setWorker((worker == null || worker.isBlank() || "null".equalsIgnoreCase(worker))
                                ? null : worker);
                        JsonNode deps = n.path("dependsOn");
                        if (deps != null && deps.isArray()) {
                            for (JsonNode d : deps) {
                                if (d != null && d.isNumber()) {
                                    s.getDependsOn().add(d.asInt());
                                }
                            }
                        }
                        if (s.getGoal() != null && !s.getGoal().isEmpty()) {
                            steps.add(s);
                        }
                    }
                }
            } catch (Exception ignored) {
                // 解析失败，走单步兜底
            }
        }
        if (steps.isEmpty()) {
            AgentStep fallback = new AgentStep();
            fallback.setStep(1);
            fallback.setGoal(text == null || text.isBlank() ? "处理目标" : compact(text));
            steps.add(fallback);
        }
        return steps;
    }

    private String compact(String text) {
        String t = text.trim();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }
}