package com.example.agent.agent;

import com.example.agent.knowledge.ExperienceService;
import com.example.agent.knowledge.KnowledgeGraphService;
import com.example.agent.knowledge.KnowledgeNodeEntity;
import com.example.agent.service.EmbeddingVectorClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据后处理：按目标相似度召回历史经验与人工标注，拼成指导 Planner 的上下文。
 * <p>召回范围：优质经验（EXPERIENCE/PITFALL 中「重复出现且经人工认可」的沉淀）+ 全部人工标注
 * （ANNOTATION 本身即人类信号）。打分优先采用 Python 向量服务的余弦相似度，
 * 服务不可用时 fail-open 回退字符 bigram 重叠度，保证召回始终可用。
 */
@Component
public class ExperienceRetriever {

    private final KnowledgeGraphService graphService;
    private final ExperienceService experienceService;
    private final EmbeddingVectorClient vectorClient;

    public ExperienceRetriever(KnowledgeGraphService graphService,
                               ExperienceService experienceService,
                               EmbeddingVectorClient vectorClient) {
        this.graphService = graphService;
        this.experienceService = experienceService;
        this.vectorClient = vectorClient;
    }

    /**
     * 召回 top-k 相关优质经验/标注，返回可直接拼进 Planner 提示词的文本；无相关经验返回空串。
     */
    public String retrieve(String goal, int k) {
        if (goal == null || goal.isBlank()) {
            return "";
        }
        List<KnowledgeNodeEntity> candidates = new ArrayList<>();
        for (String type : List.of("EXPERIENCE", "PITFALL")) {
            for (KnowledgeNodeEntity n : graphService.findByType(type)) {
                if (experienceService.isQuality(n)) {
                    candidates.add(n);
                }
            }
        }
        candidates.addAll(graphService.findByType("ANNOTATION"));

        List<KnowledgeNodeEntity> ranked = ranked(goal, candidates, k);
        if (ranked.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (KnowledgeNodeEntity n : ranked) {
            sb.append("- ").append(n.getContent() == null ? n.getName() : n.getContent()).append("\n");
        }
        return sb.toString();
    }

    private List<KnowledgeNodeEntity> ranked(String goal, List<KnowledgeNodeEntity> candidates, int k) {
        int n = candidates.size();
        double[] bigram = new double[n];
        List<String> texts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String t = text(candidates.get(i));
            texts.add(t);
            bigram[i] = bigramOverlap(goal, t);
        }
        double[] vec = vectorClient.similarity(goal, texts);
        double[] combined = new double[n];
        for (int i = 0; i < n; i++) {
            combined[i] = (vec != null && i < vec.length) ? vec[i] : bigram[i];
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(combined[b], combined[a]));
        int count = Math.min(Math.max(k, 0), n);
        List<KnowledgeNodeEntity> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(candidates.get(order[i]));
        }
        return out;
    }

    private String text(KnowledgeNodeEntity n) {
        return (n.getName() == null ? "" : n.getName())
                + " " + (n.getContent() == null ? "" : n.getContent());
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