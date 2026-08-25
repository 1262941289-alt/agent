package com.example.agent.web;

import com.example.agent.knowledge.KnowledgeGraphService;
import com.example.agent.knowledge.KnowledgeNodeEntity;
import com.example.agent.knowledge.KnowledgeRelationEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱 REST 接口：节点写入、双向链接、检索与上下文组装。
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeGraphService graphService;

    public KnowledgeController(KnowledgeGraphService graphService) {
        this.graphService = graphService;
    }

    /** 写入/更新节点 */
    @PostMapping("/nodes")
    public KnowledgeNodeEntity upsertNode(@RequestBody NodeRequest request) {
        return graphService.upsertNode(request.name(), request.type(), request.content(), request.properties());
    }

    /** 建立（双向）链接 */
    @PostMapping("/links")
    public Map<String, Object> addLink(@RequestBody LinkRequest request) {
        graphService.addRelation(request.source(), request.relationType(), request.target(), request.bidirectional());
        return Map.of("ok", true);
    }

    /** 关键字检索节点 */
    @GetMapping("/search")
    public List<KnowledgeNodeEntity> search(@RequestParam String q,
                                            @RequestParam(defaultValue = "10") int k) {
        return graphService.search(q, k);
    }

    /** 按类型查询节点（如 TASK / EXPERIENCE / ANNOTATION），验证沉淀结果 */
    @GetMapping("/nodes")
    public List<KnowledgeNodeEntity> nodes(@RequestParam(required = false) String type) {
        if (type == null || type.isBlank()) {
            return graphService.search("", Integer.MAX_VALUE);
        }
        return graphService.findByType(type);
    }

    /** 组装 GraphRAG 上下文（命中节点 + 一跳邻居） */
    @GetMapping("/context")
    public Map<String, String> context(@RequestParam String q,
                                       @RequestParam(defaultValue = "10") int k) {
        return Map.of("context", graphService.buildContext(q, k));
    }

    /** 查询某节点及其正向/反向链接 */
    @GetMapping("/{id}/graph")
    public Map<String, Object> graph(@PathVariable String id) {
        return Map.of(
                "node", graphService.findNode(id),
                "outgoing", graphService.outgoing(id),
                "incoming", graphService.incoming(id));
    }

    public record NodeRequest(String name, String type, String content, Map<String, String> properties) {
    }

    public record LinkRequest(String source, String relationType, String target, boolean bidirectional) {
    }
}