package com.example.agent.memory;

import com.example.agent.knowledge.KnowledgeGraphService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 基于知识图谱的长期记忆实现（GraphRAG）。
 * <p>recall：按查询检索图谱节点，返回相关知识；remember：把工作产出累积为图谱节点。
 */
@Service
public class GraphMemory implements Memory {

    private final KnowledgeGraphService graphService;

    public GraphMemory(KnowledgeGraphService graphService) {
        this.graphService = graphService;
    }

    @Override
    public List<String> recall(String query, int k) {
        return graphService.search(query, k).stream()
                .map(n -> n.getName() + ": " + (n.getContent() == null ? "" : n.getContent()))
                .toList();
    }

    @Override
    public void remember(String type, String name, String content) {
        String safeName = (name == null || name.length() <= 200) ? name : name.substring(0, 200);
        graphService.upsertNode(safeName, type, content, Map.of());
    }
}