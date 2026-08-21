package com.example.agent.agent;

import com.example.agent.knowledge.KnowledgeGraphService;
import com.example.agent.knowledge.KnowledgeNodeEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据后处理：按目标相似度召回历史经验（EXPERIENCE / PITFALL）与人工标注（ANNOTATION），
 * 拼成指导 Planner 的上下文，实现“利用历史最优”的自学习反馈。
 * <p>MVP 用字符二元组（bigram）重叠度做相似度打分，替代重量级向量检索；后续可平滑替换为 embedding。
 */
@Component
public class ExperienceRetriever {

    private final KnowledgeGraphService graphService;

    public ExperienceRetriever(KnowledgeGraphService graphService) {
        this.graphService = graphService;
    }

    /**
     * 召回 top-k 相关经验/标注，返回可直接拼进 Planner 提示词的文本；无相关经验返回空串。
     */
    public String retrieve(String goal, int k) {
        if (goal == null || goal.isBlank()) {
            return "";
        }
        List<KnowledgeNodeEntity> candidates = new ArrayList<>();
        candidates.addAll(graphService.findByType("EXPERIENCE"));
        candidates.addAll(graphService.findByType("PITFALL"));
        candidates.addAll(graphService.findByType("ANNOTATION"));

        List<KnowledgeNodeEntity> ranked = candidates.stream()
                .sorted(Comparator.comparingDouble((KnowledgeNodeEntity n) -> score(goal, n)).reversed())
                .limit(k)
                .toList();
        if (ranked.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (KnowledgeNodeEntity n : ranked) {
            sb.append("- ").append(n.getContent() == null ? n.getName() : n.getContent()).append("\n");
        }
        return sb.toString();
    }

    private double score(String goal, KnowledgeNodeEntity n) {
        String text = (n.getName() == null ? "" : n.getName())
                + " " + (n.getContent() == null ? "" : n.getContent());
        return bigramOverlap(goal, text);
    }

    private double bigramOverlap(String a, String b) {
        Set<String> sa = bigrams(a);
        Set<String> sb = bigrams(b);
        if (sa.isEmpty() || sb.isEmpty()) {
            return 0;
        }
        long inter = sa.stream().filter(sb::contains).count();
        return (double) inter / Math.min(sa.size(), sb.size());
    }

    private Set<String> bigrams(String s) {
        Set<String> set = new HashSet<>();
        if (s == null || s.length() < 2) {
            return set;
        }
        for (int i = 0; i < s.length() - 1; i++) {
            set.add(s.substring(i, i + 2));
        }
        return set;
    }
}