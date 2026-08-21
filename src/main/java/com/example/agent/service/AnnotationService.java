package com.example.agent.service;

import com.example.agent.entity.AnnotationRuleEntity;
import com.example.agent.entity.ItemAnnotationEntity;
import com.example.agent.knowledge.KnowledgeGraphService;
import com.example.agent.repository.AnnotationRuleRepository;
import com.example.agent.repository.ItemAnnotationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 标注回流服务：把人工标注一方面落为确定性纠正规则（规则兜底、顶替 LLM），
 * 另一方面写入知识图谱 ANNOTATION 节点供 Planner/经验召回。
 */
@Service
public class AnnotationService {

    private final ItemAnnotationRepository itemAnnotationRepository;
    private final AnnotationRuleRepository annotationRuleRepository;
    private final KnowledgeGraphService graphService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnnotationService(ItemAnnotationRepository itemAnnotationRepository,
                             AnnotationRuleRepository annotationRuleRepository,
                             KnowledgeGraphService graphService) {
        this.itemAnnotationRepository = itemAnnotationRepository;
        this.annotationRuleRepository = annotationRuleRepository;
        this.graphService = graphService;
    }

    /**
     * 数据条目级标注：三态 + 字段纠错。纠错会沉淀为确定性规则，供后续同类数据直接套用。
     */
    @Transactional
    public Map<String, Object> annotateItem(String itemId, String verdict,
                                            List<Correction> corrections, String comment) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId 不能为空");
        }
        String v = normalizeVerdict(verdict);

        ItemAnnotationEntity ann = new ItemAnnotationEntity();
        ann.setId(newId());
        ann.setItemId(itemId);
        ann.setVerdict(v);
        ann.setCorrectionsJson(toJson(corrections));
        ann.setComment(comment);
        itemAnnotationRepository.save(ann);

        List<String> ruleFields = new ArrayList<>();
        if ("WRONG".equals(v) && corrections != null) {
            for (Correction c : corrections) {
                if (c.field() == null || c.field().isBlank() || c.corrected() == null) {
                    continue;
                }
                String raw = c.raw() == null ? "" : c.raw();
                AnnotationRuleEntity rule = annotationRuleRepository
                        .findFirstByFieldNameAndRawValue(c.field(), raw)
                        .orElseGet(() -> {
                            AnnotationRuleEntity r = new AnnotationRuleEntity();
                            r.setId(newId());
                            r.setFieldName(c.field());
                            r.setRawValue(raw);
                            return r;
                        });
                rule.setCorrectedValue(c.corrected());
                rule.setSourceItemId(itemId);
                annotationRuleRepository.save(rule);
                ruleFields.add(c.field());
            }
        }

        writeItemAnnotationNode(itemId, v, corrections, comment);
        return Map.of("ok", true, "rulesCreated", ruleFields);
    }

    /**
     * 决策结果级标注：对某条 PASS/REJECT 决策做对/错评价，写入知识图谱。
     */
    @Transactional
    public void annotateDecision(String taskId, String itemId, boolean passed, boolean correct, String comment) {
        String nodeName = "标注:决策:" + shortId();
        String content = "决策标注\n任务: " + nvl(taskId)
                + "\n数据项: " + nvl(itemId)
                + "\n原决策: " + (passed ? "PASS(通过)" : "REJECT(拒绝)")
                + "\n人工评价: " + (correct ? "正确" : "错误")
                + "\n说明: " + (comment == null ? "" : comment);
        graphService.upsertNode(nodeName, "ANNOTATION", content, Map.of(
                "kind", "decision",
                "taskId", nvl(taskId),
                "itemId", nvl(itemId),
                "passed", String.valueOf(passed),
                "correct", String.valueOf(correct)));
        if (taskId != null && !taskId.isBlank()) {
            graphService.addRelation("task:" + taskId, "HUMAN_ANNOTATED", nodeName, true);
        }
    }

    public List<AnnotationRuleEntity> listRules() {
        return annotationRuleRepository.findAll();
    }

    private void writeItemAnnotationNode(String itemId, String verdict,
                                         List<Correction> corrections, String comment) {
        String nodeName = "标注:条目:" + shortId();
        String content = "条目标注\n数据项: " + itemId
                + "\n评价: " + verdict
                + "\n纠正: " + toJson(corrections)
                + "\n说明: " + (comment == null ? "" : comment);
        graphService.upsertNode(nodeName, "ANNOTATION", content, Map.of(
                "kind", "item",
                "itemId", itemId,
                "verdict", verdict,
                "corrections", toJson(corrections)));
        graphService.addRelation("item:" + itemId, "HUMAN_ANNOTATED", nodeName, true);
    }

    private String normalizeVerdict(String verdict) {
        if (verdict == null) {
            return "UNCERTAIN";
        }
        String u = verdict.strip().toUpperCase();
        if (u.contains("CORRECT") || verdict.contains("正确")) {
            return "CORRECT";
        }
        if (u.contains("WRONG") || verdict.contains("错误")) {
            return "WRONG";
        }
        return "UNCERTAIN";
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o == null ? List.of() : o);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /** 字段级纠错：field=字段名，raw=原始值，corrected=人工纠正后的正确值。 */
    public record Correction(String field, String raw, String corrected) {
    }
}