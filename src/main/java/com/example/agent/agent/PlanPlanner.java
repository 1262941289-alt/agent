package com.example.agent.agent;

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
 * HLA 规划器：将总体目标拆解为子任务步骤（LLM 输出 JSON 计划），支持步骤依赖、历史经验注入与失败重规划。
 */
@Component
public class PlanPlanner {

    private final ChatClient planningClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlanPlanner(@Qualifier("planningChatClient") ChatClient planningClient) {
        this.planningClient = planningClient;
    }

    /**
     * 拆解目标为步骤列表（不注入历史经验）。
     */
    public List<AgentStep> plan(String goal, List<WorkerAgent> workers) {
        return plan(goal, workers, "");
    }

    /**
     * 拆解目标为步骤列表，并把召回的历史经验拼进提示词指导规划。
     */
    public List<AgentStep> plan(String goal, List<WorkerAgent> workers, String experience) {
        String prompt = PromptRenderer.render(
                PromptRenderer.load("prompts/planner-system.st"),
                Map.of("goal", goal, "workers", renderWorkers(workers),
                        "experience", experience == null ? "" : experience)
        );
        String response = planningClient.prompt().user(prompt).call().content();
        return parseSteps(response);
    }

    /**
     * 失败重规划：针对执行失败的步骤，让 Planner 重新产出补救步骤。
     */
    public List<AgentStep> replan(String goal, List<AgentStep> failed, List<WorkerAgent> workers) {
        StringBuilder sb = new StringBuilder();
        for (AgentStep f : failed) {
            sb.append("- ").append(f.getGoal()).append("（失败原因：")
                    .append(f.getResult() == null ? "未知" : f.getResult()).append("）\n");
        }
        String replanGoal = "以下子步骤执行失败，请针对失败部分重新规划补救步骤：\n"
                + sb + "\n原始总体目标：\n" + goal;
        return plan(replanGoal, workers);
    }

    private String renderWorkers(List<WorkerAgent> workers) {
        StringBuilder sb = new StringBuilder();
        for (WorkerAgent w : workers) {
            sb.append("- ").append(w.name()).append(": ").append(w.description()).append("\n");
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