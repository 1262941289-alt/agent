package com.example.agent.agent;

import com.example.agent.knowledge.ExperienceService;
import com.example.agent.knowledge.KnowledgeGraphService;
import com.example.agent.knowledge.KnowledgeNodeEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据预处理：把一次执行的事件流聚合为结构化“经验”，并在 run 结束后异步写入。
 * <p>流程：规则聚合（成功/失败、步骤、反思次数）→ 失败 run 用 LLM 精炼改进点 →
 * 经 {@link ExperienceService} 去重合并落库（先为候选，重复且人工认可后升级为优质经验）。
 */
@Component
public class ExperienceCollector {

    private static final Logger log = LoggerFactory.getLogger(ExperienceCollector.class);

    private final KnowledgeGraphService graphService;
    private final ExperienceService experienceService;
    private final ChatClient refineClient;

    public ExperienceCollector(KnowledgeGraphService graphService,
                               ExperienceService experienceService,
                               @Qualifier("planningChatClient") ChatClient refineClient) {
        this.graphService = graphService;
        this.experienceService = experienceService;
        this.refineClient = refineClient;
    }

    public void collect(String runId, String goal, List<AgentEvent> events, AgentRunResult result) {
        int success = 0, failed = 0, skipped = 0, reflections = 0;
        for (AgentEvent e : events) {
            String type = e.getType();
            if ("step:status".equals(type)) {
                Object status = e.getPayload().get("status");
                if ("SUCCESS".equals(status)) {
                    success++;
                } else if ("FAILED".equals(status)) {
                    failed++;
                } else if ("SKIPPED".equals(status)) {
                    skipped++;
                }
            } else if ("step:reflection".equals(type)) {
                reflections++;
            }
        }
        boolean ok = failed == 0 && skipped == 0;
        String summary = "目标: " + goal + "\n"
                + "结果: " + (ok ? "成功" : "失败") + "\n"
                + "步骤: 成功 " + success + " / 失败 " + failed + " / 跳过 " + skipped
                + "，反思 " + reflections + " 次";
        if (!ok) {
            summary += "\n改进建议: " + refine(goal, events);
        }
        KnowledgeNodeEntity node = experienceService.collectExperience(runId, goal, summary, ok);
        graphService.addRelation(goal, "HAS_EXPERIENCE", node.getName(), true);
        log.info("经验已收集 runId={} type={} repeat={}", runId, node.getType(),
                experienceService.repeatCount(node));
    }

    private String refine(String goal, List<AgentEvent> events) {
        try {
            return refineClient.prompt()
                    .system("你是经验总结器。用最多两句话总结失败原因与可执行的改进点，直接输出中文，不要编号。")
                    .user("总体目标：" + goal + "\n执行轨迹：\n" + brief(events))
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("经验精炼失败: {}", e.getMessage());
            return "（LLM 精炼失败，仅保留结构化汇总）";
        }
    }

    private String brief(List<AgentEvent> events) {
        StringBuilder sb = new StringBuilder();
        for (AgentEvent e : events) {
            Object status = e.getPayload().get("status");
            if ("step:status".equals(e.getType()) && "FAILED".equals(status)) {
                sb.append("- 步骤 ").append(e.getPayload().get("step"))
                        .append(" 失败：").append(e.getPayload().get("output")).append("\n");
            }
        }
        return sb.length() == 0 ? "（无失败步骤明细）" : sb.toString();
    }
}