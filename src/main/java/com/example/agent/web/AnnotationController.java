package com.example.agent.web;

import com.example.agent.entity.AnnotationRuleEntity;
import com.example.agent.entity.DataItemEntity;
import com.example.agent.entity.DecisionResultEntity;
import com.example.agent.knowledge.KnowledgeGraphService;
import com.example.agent.repository.DataItemRepository;
import com.example.agent.repository.DecisionResultRepository;
import com.example.agent.service.AnnotationService;
import com.example.agent.service.AuditLogService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人工数据标注入口：操作级 / 数据条目级 / 决策结果级三层标注，
 * 作为自学习（数据预处理/后处理）的人类监督源，落为 ANNOTATION 节点被 Planner 召回，
 * 条目级纠错同时沉淀为确定性规则。
 */
@RestController
@RequestMapping("/api/agent")
public class AnnotationController {

    private final KnowledgeGraphService graphService;
    private final AnnotationService annotationService;
    private final DecisionResultRepository decisionResultRepository;
    private final DataItemRepository dataItemRepository;
    private final AuditLogService auditLogService;

    public AnnotationController(KnowledgeGraphService graphService,
                                AnnotationService annotationService,
                                DecisionResultRepository decisionResultRepository,
                                DataItemRepository dataItemRepository,
                                AuditLogService auditLogService) {
        this.graphService = graphService;
        this.annotationService = annotationService;
        this.decisionResultRepository = decisionResultRepository;
        this.dataItemRepository = dataItemRepository;
        this.auditLogService = auditLogService;
    }

    /** 操作/目标级标注。 POST /api/agent/annotations */
    @PostMapping("/annotations")
    public Map<String, Object> annotate(@RequestBody AnnotationRequest request) {
        graphService.annotate(request.goal(), request.positive(), request.comment());
        auditLogService.record("ANNOTATION", request.positive() ? "AGREE" : "REJECT", "goal", "",
                "目标级标注：" + (request.positive() ? "正向" : "负向"), request.comment(), "human");
        return Map.of("ok", true);
    }

    /** 数据条目级标注（三态 + 字段纠错）。 POST /api/agent/annotations/item */
    @PostMapping("/annotations/item")
    public Map<String, Object> annotateItem(@RequestBody ItemAnnotationRequest request) {
        Map<String, Object> r = annotationService.annotateItem(
                request.itemId(), request.verdict(), request.corrections(), request.comment());
        auditLogService.record("ANNOTATION", "ITEM", "data_item", request.itemId(),
                "条目级标注 verdict=" + request.verdict(),
                Map.of("verdict", request.verdict() == null ? "" : request.verdict(),
                        "corrections", request.corrections() == null ? List.of() : request.corrections()),
                "human");
        return r;
    }

    /** 决策结果级标注。 POST /api/agent/annotations/decision */
    @PostMapping("/annotations/decision")
    public Map<String, Object> annotateDecision(@RequestBody DecisionAnnotationRequest request) {
        annotationService.annotateDecision(
                request.taskId(), request.itemId(), request.passed(), request.correct(), request.comment());
        auditLogService.record("ANNOTATION", "DECISION", "decision", request.itemId(),
                "决策级标注 correct=" + request.correct(),
                Map.of("passed", request.passed(), "correct", request.correct()), "human");
        return Map.of("ok", true);
    }

    /** 已沉淀的确定性规则清单。 GET /api/agent/annotations/rules */
    @GetMapping("/annotations/rules")
    public List<AnnotationRuleEntity> rules() {
        return annotationService.listRules();
    }

    /** 最近决策（含数据原文），供决策级标注。 GET /api/agent/decisions/recent */
    @GetMapping("/decisions/recent")
    public List<Map<String, Object>> recentDecisions(@RequestParam(defaultValue = "20") int limit) {
        int n = Math.min(Math.max(limit, 1), 100);
        List<Map<String, Object>> out = new ArrayList<>();
        for (DecisionResultEntity d : decisionResultRepository.findTop50ByOrderByIdDesc()) {
            if (out.size() >= n) {
                break;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("taskId", d.getTaskId());
            m.put("itemId", d.getItemId());
            m.put("passed", d.isPassed());
            m.put("reason", d.getReason());
            m.put("matchedRuleId", d.getMatchedRuleId());
            m.put("createdAt", d.getCreatedAt());
            DataItemEntity item = dataItemRepository.findById(d.getItemId()).orElse(null);
            m.put("itemContent", item == null ? null : item.getContent());
            out.add(m);
        }
        return out;
    }

    /** 清空全部决策结果（决策结果标注工作台「清空」按钮）。 DELETE /api/agent/decisions */
    @DeleteMapping("/decisions")
    public Map<String, Object> clearDecisions() {
        long count = decisionResultRepository.count();
        decisionResultRepository.deleteAllInBatch();
        auditLogService.record("DATA", "DELETE", "decision", "",
                "清空决策结果 " + count + " 条", Map.of("cleared", count), "human");
        return Map.of("cleared", count);
    }

    public record AnnotationRequest(String goal, boolean positive, String comment) {
    }

    public record ItemAnnotationRequest(String itemId, String verdict,
                                        List<AnnotationService.Correction> corrections,
                                        String comment) {
    }

    public record DecisionAnnotationRequest(String taskId, String itemId, boolean passed,
                                            boolean correct, String comment) {
    }
}