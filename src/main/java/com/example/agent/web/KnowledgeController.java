package com.example.agent.web;

import com.example.agent.cleaning.CleaningChange;
import com.example.agent.cleaning.CleaningReport;
import com.example.agent.cleaning.DeterministicCleaningEngine;
import com.example.agent.entity.AnnotationRuleEntity;
import com.example.agent.knowledge.KnowledgeGraphService;
import com.example.agent.knowledge.KnowledgeNodeEntity;
import com.example.agent.repository.AnnotationRuleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识图谱 REST 接口：节点写入、双向链接、检索与上下文组装，以及「沉淀回测」。
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeGraphService graphService;
    private final DeterministicCleaningEngine cleaningEngine;
    private final AnnotationRuleRepository annotationRuleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KnowledgeController(KnowledgeGraphService graphService,
                               DeterministicCleaningEngine cleaningEngine,
                               AnnotationRuleRepository annotationRuleRepository) {
        this.graphService = graphService;
        this.cleaningEngine = cleaningEngine;
        this.annotationRuleRepository = annotationRuleRepository;
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

    /**
     * 沉淀回测：对一段扁平 JSON 数据执行确定性清洗，回调验证「人工纠错沉淀的规则」是否兜底生效，
     * 并返回已沉淀规则清单与知识图谱 ANNOTATION 节点，形成「标注 → 沉淀 → 回测」闭环。
     * POST /api/knowledge/verify  body: {"content": "{\"city\":\"beijing\"}"}
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody Map<String, Object> body) {
        Object content = body.get("content");
        if (!(content instanceof String s) || s.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "content 不能为空"));
        }
        Map<String, String> raw;
        try {
            raw = objectMapper.readValue(s, new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "content 不是合法的扁平 JSON 对象"));
        }

        CleaningReport report = cleaningEngine.clean(new LinkedHashMap<>(raw));

        List<String> hitFields = new ArrayList<>();
        for (CleaningChange c : report.changes()) {
            if ("人工标注规则".equals(c.rule())) {
                hitFields.add(c.field());
            }
        }

        List<Map<String, Object>> rules = annotationRuleRepository.findAll().stream()
                .limit(100)
                .map(this::ruleToMap)
                .toList();

        List<Map<String, Object>> nodes = graphService.findByType("ANNOTATION").stream()
                .limit(30)
                .map(this::nodeToMap)
                .toList();

        String query = raw.values().stream()
                .filter(v -> v != null && !v.isBlank())
                .limit(6)
                .collect(Collectors.joining(" "));
        String recallContext = query.isBlank() ? "" : graphService.buildContext(query, 5);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cleaned", report.cleaned());
        out.put("changes", report.changes());
        out.put("ruleHitFields", hitFields);
        out.put("annotationRulesTotal", rules.size());
        out.put("annotationRules", rules);
        out.put("annotationNodes", nodes);
        out.put("recallContext", recallContext);
        return ResponseEntity.ok(out);
    }

    private Map<String, Object> ruleToMap(AnnotationRuleEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("field", r.getFieldName());
        m.put("rawValue", r.getRawValue());
        m.put("correctedValue", r.getCorrectedValue());
        m.put("sourceItemId", r.getSourceItemId());
        return m;
    }

    private Map<String, Object> nodeToMap(KnowledgeNodeEntity n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", n.getName());
        m.put("type", n.getType());
        m.put("content", n.getContent());
        m.put("updatedAt", n.getUpdatedAt() == null ? "" : n.getUpdatedAt().toString());
        return m;
    }

    public record NodeRequest(String name, String type, String content, Map<String, String> properties) {
    }

    public record LinkRequest(String source, String relationType, String target, boolean bidirectional) {
    }
}