package com.example.agent.agent;

import com.example.agent.capability.AgentRegistry;
import com.example.agent.capability.CapabilityAgent;
import com.example.agent.memory.Memory;
import com.example.agent.service.AgentStatsService;
import com.example.agent.service.CreditScoreService;
import com.example.agent.util.PromptRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * ManagerAgent：多 agent 框架的编排者（纯分配，不执行）。
 * <p>流程：读公共记忆池召回 → Planner 拆解目标 → 按能力标签路由给能力 agent →
 * 失败重规划 → 汇总 → 异步写经验。
 * <p>每次分配都会落「分配记录」并更新对应能力 agent 的信用分（阶段二）。
 * <p>执行全程通过 {@link AgentEventSink} 发射事件；Manager 自身不执行任何子任务。
 */
@Service
public class ManagerAgent {

    private static final Logger log = LoggerFactory.getLogger(ManagerAgent.class);

    /** 失败重规划的最大轮数，防止无限重规划 */
    private static final int MAX_REPLAN_ROUNDS = 1;

    private final PlanPlanner planPlanner;
    private final ChatClient planningClient;
    private final AgentRegistry registry;
    private final Memory memory;
    private final Executor executor;
    private final ExperienceCollector experienceCollector;
    private final CreditScoreService creditScoreService;
    private final AgentStatsService agentStatsService;

    public ManagerAgent(PlanPlanner planPlanner,
                        @Qualifier("planningChatClient") ChatClient planningClient,
                        AgentRegistry registry,
                        Memory memory,
                        @Qualifier("agentExecutor") Executor executor,
                        ExperienceCollector experienceCollector,
                        CreditScoreService creditScoreService,
                        AgentStatsService agentStatsService) {
        this.planPlanner = planPlanner;
        this.planningClient = planningClient;
        this.registry = registry;
        this.memory = memory;
        this.executor = executor;
        this.experienceCollector = experienceCollector;
        this.creditScoreService = creditScoreService;
        this.agentStatsService = agentStatsService;
    }

    /** 执行总体目标（无会话上下文）。 */
    public AgentRunResult execute(String goal) {
        return execute(goal, "");
    }

    /** 携带会话上下文执行总体目标。 */
    public AgentRunResult execute(String goal, String conversationContext) {
        return execute(goal, conversationContext, null);
    }

    /** 携带会话上下文与 conversationId 执行（同步路径）。 */
    public AgentRunResult execute(String goal, String conversationContext, String conversationId) {
        return execute(goal, conversationContext, conversationId, newRunId(), null);
    }

    /**
     * 核心执行：同步 / SSE 共用。
     *
     * @param streamSink 可空；非空时事件实时推送给该 sink（SSE）
     */
    public AgentRunResult execute(String goal, String conversationContext, String conversationId,
                                  String runId, AgentEventSink streamSink) {
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        AgentRunResult result = new AgentRunResult();
        result.setGoal(goal);
        try {
            emit(events, streamSink, "run:started", runId,
                    Map.of("goal", nz(goal), "conversationId", nz(conversationId)));

            int termRound = agentStatsService.nextRound();
            String planningGoal = (conversationContext == null || conversationContext.isBlank())
                    ? goal
                    : conversationContext + "\n\n当前目标（需要完成的任务）：\n" + goal;
            String experience = memoryAsString(goal);
            List<AgentStep> steps = planPlanner.plan(planningGoal, registry.metas(), experience);
            emit(events, streamSink, "run:plan", runId, Map.of("steps", toPlanData(steps)));

            executeSteps(steps, events, streamSink, runId, termRound, goal);

            // 失败重规划：针对失败/跳过的步骤，最多重规划 MAX_REPLAN_ROUNDS 轮
            for (int round = 0; round < MAX_REPLAN_ROUNDS; round++) {
                List<AgentStep> failed = collectFailed(steps);
                if (failed.isEmpty()) {
                    break;
                }
                emit(events, streamSink, "run:replan", runId, Map.of("failed", toStepBrief(failed)));
                List<AgentStep> recovery = planPlanner.replan(goal, failed, registry.metas());
                int nextStep = steps.stream().mapToInt(AgentStep::getStep).max().orElse(0) + 1;
                for (AgentStep r : recovery) {
                    r.setStep(nextStep++);
                    r.setDependsOn(new ArrayList<>());
                    steps.add(r);
                }
                executeSteps(recovery, events, streamSink, runId, termRound, goal);
            }

            String finalAnswer = nz(synthesize(goal, steps));
            result.setSteps(steps);
            result.setFinalAnswer(finalAnswer);
            emit(events, streamSink, "run:synthesis", runId, Map.of("finalAnswer", finalAnswer));
            emit(events, streamSink, "run:completed", runId,
                    Map.of("finalAnswer", finalAnswer, "stats", statsOf(steps)));
            memory.remember("TASK", goal, finalAnswer);
        } catch (Exception e) {
            String error = errorMessage(e);
            log.error("Agent 执行失败 runId={}: {}", runId, error, e);
            result.setFinalAnswer("执行失败：" + error);
            emit(events, streamSink, "run:failed", runId, Map.of("error", error));
        } finally {
            triggerExperienceAsync(runId, goal, events, result);
        }
        return result;
    }

    private String memoryAsString(String goal) {
        List<String> recalled = memory.recall(goal, 5);
        return recalled.isEmpty() ? "" : String.join("\n", recalled);
    }

    private void triggerExperienceAsync(String runId, String goal, List<AgentEvent> events, AgentRunResult result) {
        executor.execute(() -> {
            try {
                experienceCollector.collect(runId, goal, events, result);
            } catch (Exception e) {
                log.warn("经验写入失败 runId={}: {}", runId, e.getMessage());
            }
        });
    }

    private void emit(List<AgentEvent> events, AgentEventSink sink, String type, String runId,
                      Map<String, Object> data) {
        AgentEvent event = new AgentEvent(type, runId, data);
        events.add(event);
        if (sink != null) {
            sink.emit(event);
        }
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
    private void executeSteps(List<AgentStep> steps, List<AgentEvent> events, AgentEventSink sink, String runId,
                              int termRound, String goal) {
        Map<Integer, AgentStep> byNumber = new HashMap<>();
        for (AgentStep s : steps) {
            byNumber.put(s.getStep(), s);
        }

        Set<Integer> settled = new HashSet<>();
        Set<Integer> pending = new HashSet<>(byNumber.keySet());

        int wave = 0;
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
                wave++;
                List<Integer> stepNums = ready.stream().map(AgentStep::getStep).toList();
                emit(events, sink, "wave:started", runId, Map.of("wave", wave, "stepNums", stepNums));
                runInParallel(ready, events, sink, runId, termRound, goal);
                emit(events, sink, "wave:completed", runId, Map.of("wave", wave, "stepNums", stepNums));
            }
            for (AgentStep s : ready) {
                pending.remove(s.getStep());
                settled.add(s.getStep());
            }
            for (AgentStep s : blocked) {
                emit(events, sink, "step:status", runId, stepStatus(s, "", 0));
                pending.remove(s.getStep());
                settled.add(s.getStep());
            }
        }
    }

    private boolean isBlocked(AgentStep s) {
        return "FAILED".equals(s.getStatus()) || "SKIPPED".equals(s.getStatus());
    }

    private void runInParallel(List<AgentStep> ready, List<AgentEvent> events, AgentEventSink sink, String runId,
                               int termRound, String goal) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (AgentStep step : ready) {
            futures.add(CompletableFuture.runAsync(() -> runStep(step, events, sink, runId, termRound, goal), executor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void runStep(AgentStep step, List<AgentEvent> events, AgentEventSink sink, String runId,
                         int termRound, String goal) {
        String label = (step.getWorker() == null || step.getWorker().isBlank())
                ? "general" : step.getWorker();
        CapabilityAgent agent = registry.resolve(label);
        step.setStatus("RUNNING");
        emit(events, sink, "run:allocation", runId, Map.of(
                "round", termRound, "step", step.getStep(), "worker", label,
                "goal", nz(step.getGoal())));
        emit(events, sink, "step:status", runId, Map.of(
                "step", step.getStep(), "status", "RUNNING", "worker", label));

        long start = System.currentTimeMillis();
        AgentResult r = null;
        try {
            r = agent.run(step.getGoal());
            step.setStatus(r.isSuccess() ? "SUCCESS" : "FAILED");
            step.setResult(r.getOutput());
            step.setReflections(r.getReflections());
        } catch (Exception e) {
            step.setStatus("FAILED");
            step.setResult("执行异常: " + errorMessage(e));
        }
        long durationMs = System.currentTimeMillis() - start;

        recordAllocation(runId, termRound, step, goal, label, durationMs);

        int reflections = r == null ? 0 : r.getReflections();
        List<Reflection> trail = r == null ? List.of() : r.getReflectionTrail();
        emitReflections(step, trail, events, sink, runId);
        emit(events, sink, "step:status", runId, stepStatus(step, label, reflections));
    }

    /** 落分配记录 + 按执行结果更新信用分（成功+奖、失败−罚，逼近100触发过热反噬）。 */
    private void recordAllocation(String runId, int termRound, AgentStep step, String goal,
                                  String label, long durationMs) {
        try {
            agentStatsService.record(runId, termRound, step.getStep(), goal, step.getGoal(),
                    label, nz(step.getStatus()), durationMs);
            creditScoreService.applyOutcome(label, "SUCCESS".equals(step.getStatus()));
        } catch (Exception e) {
            log.warn("分配记录/信用分更新失败 runId={} step={}: {}", runId, step.getStep(), e.getMessage());
        }
    }

    private void emitReflections(AgentStep step, List<Reflection> trail,
                                 List<AgentEvent> events, AgentEventSink sink, String runId) {
        int iteration = 0;
        for (Reflection rf : trail) {
            iteration++;
            emit(events, sink, "step:reflection", runId, Map.of(
                    "step", step.getStep(),
                    "iteration", iteration,
                    "satisfied", rf.isSatisfied(),
                    "critique", clip(rf.getCritique()),
                    "nextAction", clip(rf.getNextAction())));
        }
    }

    private Map<String, Object> stepStatus(AgentStep step, String worker, int reflections) {
        return Map.of(
                "step", step.getStep(),
                "status", nz(step.getStatus()),
                "worker", nz(worker),
                "output", nz(step.getResult()),
                "reflections", reflections);
    }

    private List<Map<String, Object>> toPlanData(List<AgentStep> steps) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentStep s : steps) {
            out.add(Map.of(
                    "step", s.getStep(),
                    "goal", nz(s.getGoal()),
                    "worker", nz(s.getWorker()),
                    "dependsOn", s.getDependsOn() == null ? List.of() : s.getDependsOn()));
        }
        return out;
    }

    private List<Map<String, Object>> toStepBrief(List<AgentStep> failed) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentStep s : failed) {
            out.add(Map.of(
                    "step", s.getStep(),
                    "goal", nz(s.getGoal()),
                    "result", nz(s.getResult())));
        }
        return out;
    }

    private Map<String, Object> statsOf(List<AgentStep> steps) {
        int success = 0;
        int failed = 0;
        int reflections = 0;
        for (AgentStep s : steps) {
            if ("SUCCESS".equals(s.getStatus())) {
                success++;
            } else {
                failed++;
            }
            reflections += s.getReflections();
        }
        return Map.of("total", steps.size(), "success", success, "failed", failed, "reflections", reflections);
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

    private String clip(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String errorMessage(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static String newRunId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}