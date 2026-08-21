package com.example.agent.web;

import com.example.agent.knowledge.KnowledgeGraphService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 人工数据标注入口：告诉模型哪些操作有效、富有成效（正负反馈信号），
 * 作为自学习（数据预处理/后处理）的人类监督源，落为 ANNOTATION 节点被 Planner 召回。
 */
@RestController
@RequestMapping("/api/agent")
public class AnnotationController {

    private final KnowledgeGraphService graphService;

    public AnnotationController(KnowledgeGraphService graphService) {
        this.graphService = graphService;
    }

    /**
     * POST /api/agent/annotations
     */
    @PostMapping("/annotations")
    public Map<String, Object> annotate(@RequestBody AnnotationRequest request) {
        graphService.annotate(request.goal(), request.positive(), request.comment());
        return Map.of("ok", true);
    }

    public record AnnotationRequest(String goal, boolean positive, String comment) {
    }
}