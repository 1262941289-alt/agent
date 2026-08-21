package com.example.agent.agent;

import com.example.agent.memory.Memory;
import com.example.agent.util.PromptRenderer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Hierarchical Agent（HLA，最高层级自主性）编排器。
 * <p>流程：Planner 拆解目标 → 按依赖关系并行/串行分派给 Worker（Worker 内部跑反思循环）→ 失败重规划 → 汇总为最终答复。
 * <p>Worker 以 Spring Bean 形式注入并自动注册；办公能力接入时只需新增 {@link WorkerAgent} 实现即可被自动发现。
 */
@Service
public class HierarchicalAgent {

    /** 失败重规划的最大轮数，防止无限重规划 */
    private static final int MAX_REPLAN_ROUNDS = 1;

    private final PlanPlanner planPlanner;
    private final ChatClient planningClient;
    private final Memory memory;
    private final Executor executor;
    private final Map<String, WorkerAgent> workers = new LinkedHashMap<>();

    public HierarchicalAgent(PlanPlanner planPlanner,
                             List<WorkerAgent> workerList,
                             @Qualifier("planningChatClient") ChatClient planningClient,
                             Memory memory,
                             @Qualifier("agentExecutor") Executor executor) {
        this.planPlanner = planPlanner;
        this.planningClient = planningClient;
        this.memory = memory;
        this.executor = executor;
        for (WorkerAgent w : workerList) {
            this.workers.put(w.name().toLowerCase(), w);
        }
    }

    /**
     * 执行总体目标，返回拆解步骤与最终答复。
     */
    public AgentRunResult execute(String goal) {
        return execute(goal, "");
    }

    /**
     * 携带会话上下文执行总体目标。
     *
     * @param goal                本次要完成的总体目标
     * @param conversationContext 短期记忆召回的前文（可为空），仅用于规划/执行增强上下文
     */
    public AgentRunResult execute(String goal, String conversationContext) {
        String planningGoal = (conversationContext == null || conversationContext.isBlank())
                ? goal
                : conversationContext + "\n\n当前目标（需要完成的任务）：\n" + goal;
        List<AgentStep> steps = planPlanner.plan(planningGoal, new ArrayList<>(workers.values()));
        executeSteps(steps);

        // 失败重规划：针对失败/跳过的步骤，最多重规划 MAX_REPLAN_ROUNDS 轮
        for (int round = 0; round < MAX_REPLAN_ROUNDS; round++) {
            List<AgentStep> failed = collectFailed(steps);
            if (failed.isEmpty()) {
                break;
            }
            List<AgentStep> recovery = planPlanner.replan(goal, failed, new ArrayList<>(workers.values()));
            int nextStep = steps.stream().mapToInt(AgentStep::getStep).max().orElse(0) + 1;
            for (AgentStep r : recovery) {
                r.setStep(nextStep++);
                r.setDependsOn(new ArrayList<>()); // 补救步骤独立执行
                steps.add(r);
            }
            executeSteps(recovery);
        }

        AgentRunResult result = new AgentRunResult();
        result.setGoal(goal);
        result.setSteps(steps);
        result.setFinalAnswer(synthesize(goal, steps));
        memory.remember("TASK", goal, result.getFinalAnswer());
        return result;
    }

    private List<AgentStep> collectFailed(List<AgentStep> steps) {
        List<AgentStep> failed = new ArrayList<>();
        for (AgentStep s : steps) {
            if ("FAILED".equals(s.getStatus()) || "SKIPPED".equals(s.getStatus())) {
                failed.add(s);
            }
        }
        return failed;
    }

    /**
     * 按依赖关系调度执行：无依赖的步骤在同波次并行执行；
     * 依赖步骤需等其前置步骤"已定"（SUCCESS/FAILED）后才执行，前置失败则标记 SKIPPED。
     */
    private void executeSteps(List<AgentStep> steps) {
        Map<Integer, AgentStep> byNumber = new HashMap<>();
        for (AgentStep s : steps) {
            byNumber.put(s.getStep(), s);
        }

        Set<Integer> settled = new HashSet<>();
        Set<Integer> pending = new HashSet<>(byNumber.keySet());

        int guard = 0;
        while (!pending.isEmpty() && guard++ <= steps.size() + 1) {
            List<AgentStep> ready = new ArrayList<>();
            List<AgentStep> blocked = new ArrayList<>();
            for (int num : pending) {
                AgentStep s = byNumber.get(num);
                if (s.getDependsOn() == null || s.getDependsOn().isEmpty()) {
                    ready.add(s);
                } else if (s.getDependsOn().stream().allMatch(settled::contains)) {
                    if (s.getDependsOn().stream().anyMatch(d -> isBlocked(byNumber.get(d)))) {
                        s.setStatus("SKIPPED");
                        s.setResult("依赖步骤失败，跳过");
                        blocked.add(s);
                    } else {
                        ready.add(s);
                    }
                }
            }
            if (ready.isEmpty()) {
                // 存在循环依赖或缺失依赖，无法推进：兜底标记剩余步骤
                for (int num : pending) {
                    AgentStep s = byNumber.get(num);
                    s.setStatus("FAILED");
                    s.setResult("存在循环依赖或缺失依赖，无法调度");
                    blocked.add(s);
                }
            } else {
                runInParallel(ready);
            }
            for (AgentStep s : ready) {
                pending.remove(s.getStep());
                settled.add(s.getStep());
            }
            for (AgentStep s : blocked) {
                pending.remove(s.getStep());
                settled.add(s.getStep());
            }
        }
    }

    private boolean isBlocked(AgentStep s) {
        return "FAILED".equals(s.getStatus()) || "SKIPPED".equals(s.getStatus());
    }

    private void runInParallel(List<AgentStep> ready) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (AgentStep step : ready) {
            futures.add(CompletableFuture.runAsync(() -> runStep(step), executor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void runStep(AgentStep step) {
        WorkerAgent worker = resolve(step.getWorker());
        step.setStatus("RUNNING");
        try {
            AgentResult r = worker.run(step.getGoal());
            step.setStatus(r.isSuccess() ? "SUCCESS" : "FAILED");
            step.setResult(r.getOutput());
        } catch (Exception e) {
            step.setStatus("FAILED");
            step.setResult("执行异常: " + e.getMessage());
        }
    }

    private WorkerAgent resolve(String name) {
        if (name != null) {
            WorkerAgent w = workers.get(name.toLowerCase());
            if (w != null) {
                return w;
            }
        }
        WorkerAgent general = workers.get("general");
        return general != null ? general : new WorkerAgent() {
            @Override
            public String name() {
                return "fallback";
            }

            @Override
            public String description() {
                return "回退执行器";
            }

            @Override
            public AgentResult run(String goal) {
                return AgentResult.fail("无可用执行器");
            }
        };
    }

    private String synthesize(String goal, List<AgentStep> steps) {
        StringBuilder sb = new StringBuilder();
        for (AgentStep s : steps) {
            sb.append("[步骤").append(s.getStep()).append(" ").append(s.getStatus()).append("] ")
                    .append(s.getGoal()).append("\n结果: ").append(s.getResult()).append("\n\n");
        }
        String prompt = PromptRenderer.render(
                PromptRenderer.load("prompts/synthesizer-system.st"),
                Map.of("goal", goal, "steps", sb.toString())
        );
        return planningClient.prompt().user(prompt).call().content();
    }
}