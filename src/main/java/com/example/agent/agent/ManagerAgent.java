package com.example.agent.agent;

import com.example.agent.capability.AgentContext;
import com.example.agent.capability.AgentRegistry;
import com.example.agent.capability.CapabilityAgent;
import com.example.agent.config.RunContext;
import com.example.agent.entity.ElectionEntity;
import com.example.agent.memory.Memory;
import com.example.agent.service.AgentStatsService;
import com.example.agent.service.AgentStatusService;
import com.example.agent.service.CreditScoreService;
import com.example.agent.service.ElectionService;
import com.example.agent.service.FileContextService;
import com.example.agent.service.RunControlService;
import com.example.agent.service.RunEventService;
import com.example.agent.util.PromptRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
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
 * <p>递归主循环：观察（聚合已执行步骤的真实结果）→ 决策（DONE/CONTINUE/ABORT + 下一批步骤）→
 * 执行（按依赖分波并行）→ 循环，直到目标达成、中止或达到轮数上限。
 * <p>每轮分配都会落「分配记录」并更新对应能力 agent 的信用分；每轮结束自动选举管理者。
 * <p>执行全程通过 {@link AgentEventSink} 发射事件；Manager 自身不执行任何子任务。
 */
@Service
public class ManagerAgent {

    private static final Logger log = LoggerFactory.getLogger(ManagerAgent.class);

    /** 递归主循环的最大轮数，防止无限循环（首轮规划 + 最多 4 轮观察决策） */
    private static final int MAX_ITERATIONS = 5;

    private final PlanPlanner planPlanner;
    private final ChatClient planningClient;
    private final AgentRegistry registry;
    private final Memory memory;
    private final Executor executor;
    private final ExperienceCollector experienceCollector;
    private final ExperienceRetriever experienceRetriever;
    private final CreditScoreService creditScoreService;
    private final AgentStatsService agentStatsService;
    private final ElectionService electionService;
    private final RunControlService runControl;
    private final AgentStatusService statusService;
    private final FileContextService fileContextService;
    private final RunEventService runEventService;

    public ManagerAgent(PlanPlanner planPlanner,
                        @Qualifier("planningChatClient") ChatClient planningClient,
                        AgentRegistry registry,
                        Memory memory,
                        @Qualifier("agentExecutor") Executor executor,
                        ExperienceCollector experienceCollector,
                        ExperienceRetriever experienceRetriever,
                        CreditScoreService creditScoreService,
                        AgentStatsService agentStatsService,
                        ElectionService electionService,
                        RunControlService runControl,
                        AgentStatusService statusService,
                        FileContextService fileContextService,
                        RunEventService runEventService) {
        this.planPlanner = planPlanner;
        this.planningClient = planningClient;
        this.registry = registry;
        this.memory = memory;
        this.executor = executor;
        this.experienceCollector = experienceCollector;
        this.experienceRetriever = experienceRetriever;
        this.creditScoreService = creditScoreService;
        this.agentStatsService = agentStatsService;
        this.electionService = electionService;
        this.runControl = runControl;
        this.statusService = statusService;
        this.fileContextService = fileContextService;
        this.runEventService = runEventService;
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
        return execute(goal, conversationContext, conversationId, newRunId(), null, null);
    }

    /**
     * 核心执行：同步 / SSE 共用。
     *
     * @param streamSink    可空；非空时事件实时推送给该 sink（SSE）
     * @param fileContextId 可空；非空时从 FileContextService 取文件内容注入规划上下文
     */
    public AgentRunResult execute(String goal, String conversationContext, String conversationId,
                                  String runId, AgentEventSink streamSink, String fileContextId) {
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        AgentRunResult result = new AgentRunResult();
        result.setGoal(goal);
        runControl.register(runId);
        try {
            emit(events, streamSink, "run:started", runId,
                    Map.of("goal", nz(goal), "conversationId", nz(conversationId)));

            int termRound = agentStatsService.nextRound();
            String managerRef = electionService.currentManager();
            String planningGoal = (conversationContext == null || conversationContext.isBlank())
                    ? goal
                    : conversationContext + "\n\n当前目标（需要完成的任务）：\n" + goal;
            String fileContextContent = "";
            if (fileContextId != null) {
                FileContextService.FileContext fc = fileContextService.get(fileContextId);
                if (fc != null) {
                    fileContextContent = "文件名: " + fc.fileName()
                            + "\n格式: " + fc.extension()
                            + "\n内容:\n"
                            + fc.content().substring(0, Math.min(8000, fc.content().length()))
                            + (fc.content().length() > 8000 ? "\n...(内容已截断)" : "");
                    planningGoal += "\n\n【上传文件上下文】\n" + fileContextContent;
                }
            }
            // 阶段三最小版：注入当选管理者身份，供规划器感知（管理者只分配不执行）
            planningGoal = "本轮管理者（负责拆解与分配，不直接执行）：" + managerRef + "\n" + planningGoal;
            String experience = knowledgeContext(goal);
            emitKnowledgeInjected(events, streamSink, runId, experience);

            // 递归主循环：获取信息（观察已执行步骤的真实结果）→ 制定计划（决策 + 下一批步骤）
            // → 执行操作（分波并行）→ 循环，直到 DONE / ABORT / 超上限
            List<AgentStep> steps = new ArrayList<>();
            int iterations = 0;
            String termination;
            while (true) {
                iterations++;
                if (runControl.isCancelled(runId)) {
                    termination = "CANCELLED";
                    break;
                }
                List<AgentStep> batch;
                if (iterations == 1) {
                    batch = planPlanner.plan(planningGoal, registry.metas(), experience);
                    emit(events, streamSink, "run:plan", runId, Map.of("steps", toPlanData(batch)));
                    if (batch == null || batch.isEmpty()) {
                        // 目标无法拆解：不再空转后续决策轮，直接终止
                        termination = "PLAN_EMPTY";
                        break;
                    }
                } else {
                    IterationDecision d;
                    try {
                        d = planPlanner.decideNext(
                                iterations, planningGoal, steps, registry.metas(), experience);
                    } catch (Exception e) {
                        // 决策调用失败不应丢弃已执行的成果：保留部分结果继续汇总
                        log.warn("第 {} 轮决策调用失败 runId={}: {}", iterations, runId, e.getMessage());
                        termination = "DECISION_ERROR";
                        break;
                    }
                    emit(events, streamSink, "run:iteration", runId, iterationData(iterations, d));
                    if ("DONE".equals(d.getDecision())) {
                        termination = "DONE";
                        break;
                    }
                    if ("ABORT".equals(d.getDecision())) {
                        termination = "ABORT";
                        break;
                    }
                    batch = renumber(d.getSteps(), steps);
                    if (batch.isEmpty()) {
                        // 判 CONTINUE 却给不出新步骤：视为已完成
                        termination = "DONE";
                        break;
                    }
                }
                steps.addAll(batch);
                if (executeSteps(batch, events, streamSink, runId, termRound, goal,
                        conversationContext, fileContextContent, steps)) {
                    termination = "CANCELLED";
                    break;
                }
                // 每轮结束后自动评估操作价值（History 角色：记录当前轮信息价值）
                assessRoundValue(batch, termRound, goal, events, streamSink, runId);
                if (iterations >= MAX_ITERATIONS) {
                    termination = "MAX_ITERATIONS";
                    break;
                }
            }

            // 阶段三：每轮分配/执行结束后自动选举下一轮管理者（冷启动为 default，平票信用分高者胜）
            ElectionEntity election = electionService.elect(termRound, managerRef);
            Map<String, Object> electionData = electionData(termRound, managerRef, election);
            emit(events, streamSink, "run:elected", runId, electionData);

            String finalAnswer = steps.isEmpty()
                    ? "未能将目标拆解为可执行步骤（PLAN_EMPTY），请调整目标描述后重试。"
                    : nz(synthesize(
                            goal + "\n\n（递归循环终止状态：" + termination + "，共 " + iterations + " 轮）", steps));
            result.setSteps(steps);
            result.setFinalAnswer(finalAnswer);
            result.setTermination(termination);
            result.setIterations(iterations);
            result.setEvents(events);
            emit(events, streamSink, "run:synthesis", runId, Map.of("finalAnswer", finalAnswer));
            Map<String, Object> completed = new HashMap<>();
            completed.put("finalAnswer", finalAnswer);
            completed.put("stats", statsOf(steps));
            completed.put("election", electionData);
            completed.put("iterations", iterations);
            completed.put("termination", termination);
            emit(events, streamSink, "run:completed", runId, completed);
            try {
                memory.remember("TASK", goal, finalAnswer);
            } catch (Exception me) {
                log.warn("知识图谱沉淀失败 runId={}: {}", runId, me.getMessage());
            }
        } catch (Exception e) {
            String error = errorMessage(e);
            log.error("Agent 执行失败 runId={}: {}", runId, error, e);
            result.setFinalAnswer("执行失败：" + error);
            emit(events, streamSink, "run:failed", runId, Map.of("error", error));
        } finally {
            runControl.unregister(runId);
            triggerExperienceAsync(runId, goal, events, result);
        }
        return result;
    }

    /**
     * 组装注入规划器的知识上下文（按优先级置顶排列）：
     * 1) 优质经验 + 人工标注（由 {@link ExperienceRetriever} 召回，仅「重复≥2 次且经人工认可」的经验 +
     *    全部人工标注，作为最高优先遵循信号）；
     * 2) 通用长期记忆（知识图谱历史，作为背景参考）。
     */
    private String knowledgeContext(String goal) {
        StringBuilder sb = new StringBuilder();
        String quality = experienceRetriever.retrieve(goal, 6);
        if (!quality.isBlank()) {
            sb.append("【已沉淀的优质经验与人工标注（重复≥2 次且经人工认可，优先遵循）】\n")
                    .append(quality).append("\n\n");
        }
        List<String> recall = memory.recall(goal, 5);
        if (!recall.isEmpty()) {
            sb.append("【历史记忆（背景参考）】\n").append(String.join("\n", recall));
        }
        return sb.toString().trim();
    }

    /** 发射「本次规划注入的知识」事件，供控制台在时间线上显式展示优质经验是否真正参与。 */
    private void emitKnowledgeInjected(List<AgentEvent> events, AgentEventSink sink, String runId, String context) {
        long injected = Arrays.stream((context == null ? "" : context).split("\n"))
                .filter(l -> l.startsWith("- ")).count();
        String snippet = context == null ? "" : context;
        if (snippet.length() > 1500) {
            snippet = snippet.substring(0, 1500) + "…（截断）";
        }
        emit(events, sink, "run:knowledge", runId, Map.of(
                "injectedCount", injected,
                "injected", snippet));
    }

    private Map<String, Object> electionData(int round, String managerRef, ElectionEntity e) {
        Map<String, Object> m = new HashMap<>();
        m.put("round", round);
        m.put("previousManager", nz(managerRef));
        m.put("winner", nz(e.getWinner()));
        m.put("candidates", e.getCandidatesJson() == null ? "" : e.getCandidatesJson());
        return m;
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

    private Map<String, Object> iterationData(int iteration, IterationDecision d) {
        Map<String, Object> m = new HashMap<>();
        m.put("iteration", iteration);
        m.put("decision", d.getDecision());
        m.put("reason", clip(d.getReason()));
        m.put("nextSteps", toPlanData(d.getSteps()));
        return m;
    }

    /**
     * 将决策器给出的批次内局部序号重编为全局序号；批内依赖同步平移，
     * 引用历史批次步骤的依赖已被满足（进入下一轮前全部 settled），直接丢弃。
     */
    private List<AgentStep> renumber(List<AgentStep> batch, List<AgentStep> existing) {
        if (batch == null || batch.isEmpty()) {
            return new ArrayList<>();
        }
        int offset = existing.stream().mapToInt(AgentStep::getStep).max().orElse(0);
        int localSize = batch.size();
        List<AgentStep> out = new ArrayList<>();
        for (AgentStep s : batch) {
            s.setStep(offset + s.getStep());
            List<Integer> deps = new ArrayList<>();
            for (int d : s.getDependsOn()) {
                if (d >= 1 && d <= localSize) {
                    deps.add(offset + d);
                }
            }
            s.setDependsOn(deps);
            out.add(s);
        }
        return out;
    }

    private void emit(List<AgentEvent> events, AgentEventSink sink, String type, String runId,
                      Map<String, Object> data) {
        AgentEvent event = new AgentEvent(type, runId, data);
        events.add(event);
        // 可回放事实源：内存 + SSE 与持久化共用同一出口（DB 失败只记日志，不阻断执行）
        runEventService.append(runId, type, data);
        if (sink != null) {
            sink.emit(event);
        }
    }

    /**
     * 按依赖关系调度执行：无依赖的步骤在同波次并行执行；
     * 依赖步骤需等其前置步骤"已定"（SUCCESS/FAILED）后才执行，前置失败则标记 SKIPPED。
     *
     * @return true 表示收到取消信号（当前波跑完后不再开新波），剩余步骤标记 CANCELLED
     */
    private boolean executeSteps(List<AgentStep> steps, List<AgentEvent> events, AgentEventSink sink, String runId,
                                 int termRound, String goal, String conversationContext,
                                 String fileContextContent, List<AgentStep> allSteps) {
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
            } else if (runControl.isCancelled(runId)) {
                // 协作式取消：不再开新波，剩余步骤标记取消
                for (AgentStep s : ready) {
                    s.setStatus("CANCELLED");
                    s.setResult("人工停止");
                    blocked.add(s);
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
                return true;
            } else {
                wave++;
                List<Integer> stepNums = ready.stream().map(AgentStep::getStep).toList();
                emit(events, sink, "wave:started", runId, Map.of("wave", wave, "stepNums", stepNums));
                runInParallel(ready, events, sink, runId, termRound, goal,
                        conversationContext, fileContextContent, allSteps);
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
        return runControl.isCancelled(runId);
    }

    private boolean isBlocked(AgentStep s) {
        return "FAILED".equals(s.getStatus()) || "SKIPPED".equals(s.getStatus());
    }

    private void runInParallel(List<AgentStep> ready, List<AgentEvent> events, AgentEventSink sink, String runId,
                               int termRound, String goal, String conversationHistory,
                               String fileContext, List<AgentStep> allSteps) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (AgentStep step : ready) {
            futures.add(CompletableFuture.runAsync(() -> runStep(step, events, sink, runId,
                    termRound, goal, conversationHistory, fileContext, allSteps), executor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void runStep(AgentStep step, List<AgentEvent> events, AgentEventSink sink, String runId,
                         int termRound, String goal, String conversationHistory,
                         String fileContext, List<AgentStep> allSteps) {
        String label = (step.getWorker() == null || step.getWorker().isBlank())
                ? "general" : step.getWorker();
        CapabilityAgent agent = registry.resolve(label);
        step.setStatus("RUNNING");
        statusService.markWorking(label, clip(step.getGoal()));
        emit(events, sink, "run:allocation", runId, Map.of(
                "round", termRound, "step", step.getStep(), "worker", label,
                "goal", nz(step.getGoal())));
        emit(events, sink, "step:status", runId, Map.of(
                "step", step.getStep(), "status", "RUNNING", "worker", label));

        AgentContext ctx = new ManagerAgentContext(goal, conversationHistory,
                fileContext, allSteps, memory);

        long start = System.currentTimeMillis();
        AgentResult r = null;
        // 在 worker 线程注入当前 RunContext：让被守护的工具回调（审批门/事件推送）能拿到
        // runId 与 sink，否则 tool:approval-request 永远不会推送到控制台，click/fill 会静默阻塞到审批超时。
        RunContext.set(new RunContext(runId, events, sink, runEventService));
        try {
            r = agent.run(step.getGoal(), ctx);
            step.setStatus(r.isSuccess() ? "SUCCESS" : "FAILED");
            step.setResult(r.getOutput());
            step.setReflections(r.getReflections());
        } catch (Exception e) {
            step.setStatus("FAILED");
            step.setResult("执行异常: " + errorMessage(e));
        } finally {
            RunContext.clear();
        }
        long durationMs = System.currentTimeMillis() - start;
        statusService.markIdle(label);

        int reflections = r == null ? 0 : r.getReflections();
        List<Reflection> trail = r == null ? List.of() : r.getReflectionTrail();
        recordAllocation(runId, termRound, step, goal, label, durationMs, reflections);
        emitReflections(step, trail, events, sink, runId);
        emit(events, sink, "step:status", runId, stepStatus(step, label, reflections));
    }

    /** 落分配记录 + 按执行结果更新信用分（v2：难度加权 + 努力分 + 干多不罚）。 */
    private void recordAllocation(String runId, int termRound, AgentStep step, String goal,
                                  String label, long durationMs, int reflections) {
        try {
            agentStatsService.record(runId, termRound, step.getStep(), goal, step.getGoal(),
                    label, nz(step.getStatus()), durationMs);
            creditScoreService.applyOutcome(label, "SUCCESS".equals(step.getStatus()), reflections);
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

    /**
     * 每轮结束后自动评估操作价值（History 角色的自动职责）。
     * <p>评估维度：成功率、信息产出量、反思次数、步骤覆盖度
     * <p>记录到知识图谱（ROUND_VALUE 类型），供后续召回
     */
    private void assessRoundValue(List<AgentStep> batch, int round, String goal,
                                   List<AgentEvent> events, AgentEventSink sink, String runId) {
        int success = 0, failed = 0, total = batch.size();
        int infoChars = 0;
        int reflections = 0;
        Set<String> workers = new HashSet<>();

        for (AgentStep s : batch) {
            if ("SUCCESS".equals(s.getStatus())) success++;
            else if ("FAILED".equals(s.getStatus())) failed++;
            if (s.getResult() != null) infoChars += s.getResult().length();
            reflections += s.getReflections();
            if (s.getWorker() != null) workers.add(s.getWorker());
        }

        double successRate = total == 0 ? 0 : (double) success / total;
        double infoScore = Math.min(infoChars / 1000.0, 10.0);
        double reflectionPenalty = Math.min(reflections * 0.5, 5.0);
        int valueScore = Math.max(0, Math.min(100,
                (int) (successRate * 50 + infoScore * 3 + workers.size() * 5 - reflectionPenalty)));

        String summary = String.format(
                "轮次%d价值评估: 成功%d/%d(%.0f%%) | 信息产出%d字符 | 反思%d次 | 参与Agent:%s | 价值分:%d/100",
                round, success, total, successRate * 100, infoChars, reflections,
                String.join(",", workers), valueScore);

        String memKey = "轮次" + round + "价值评估:" + goal.substring(0, Math.min(50, goal.length()));
        try {
            memory.remember("ROUND_VALUE", memKey, summary);
        } catch (Exception e) {
            log.warn("轮次价值评估存储失败: {}", e.getMessage());
        }

        emit(events, sink, "round:value", runId, Map.of(
                "round", round, "success", success, "total", total,
                "successRate", Math.round(successRate * 100),
                "infoChars", infoChars, "reflections", reflections,
                "workers", workers, "valueScore", valueScore,
                "summary", summary));
    }

    private static String newRunId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * ManagerAgent 下发给 Worker 的运行时上下文实现。
     * Worker 通过此对象获取会话历史、文件内容、经验记忆、其他步骤结果等公共信息。
     */
    private static class ManagerAgentContext implements AgentContext {
        private final String goal;
        private final String conversationHistory;
        private final String fileContext;
        private final List<AgentStep> stepResults;
        private final Memory memory;

        ManagerAgentContext(String goal, String conversationHistory,
                            String fileContext, List<AgentStep> stepResults, Memory memory) {
            this.goal = goal;
            this.conversationHistory = conversationHistory;
            this.fileContext = fileContext;
            this.stepResults = stepResults;
            this.memory = memory;
        }

        @Override
        public String requestInfo(String query) {
            StringBuilder sb = new StringBuilder();
            if (conversationHistory != null && !conversationHistory.isBlank()) {
                sb.append(conversationHistory).append("\n");
            }
            if (fileContext != null && !fileContext.isBlank()) {
                sb.append(fileContext).append("\n");
            }
            String mem = recallMemory(query);
            if (mem != null && !mem.isBlank()) {
                sb.append(mem);
            }
            return sb.toString().trim();
        }

        @Override
        public String getConversationHistory() {
            return conversationHistory == null ? "" : conversationHistory;
        }

        @Override
        public String getFileContext() {
            return fileContext == null ? "" : fileContext;
        }

        @Override
        public String recallMemory(String query) {
            try {
                List<String> results = memory.recall(query, 3);
                return (results == null || results.isEmpty()) ? "" : String.join("\n", results);
            } catch (Exception e) {
                return "";
            }
        }

        @Override
        public List<AgentStep> getStepResults() {
            return stepResults == null ? List.of() : stepResults;
        }

        @Override
        public String getGoal() {
            return goal == null ? "" : goal;
        }
    }
}