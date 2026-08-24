package com.example.agent.web;

import com.example.agent.knowledge.ExperienceService;
import com.example.agent.knowledge.KnowledgeNodeEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 优质经验沉淀 REST 接口：候选经验列表、人工认可/否决。
 * <p>优质经验判定：重复出现（≥2 次）且经人工认可；仅优质经验会被注入 Planner 召回。
 */
@RestController
@RequestMapping("/api/experiences")
public class ExperienceController {

    private final ExperienceService experienceService;

    public ExperienceController(ExperienceService experienceService) {
        this.experienceService = experienceService;
    }

    /** 全量经验视图（候选 + 优质），按重复次数倒序 */
    @GetMapping
    public List<Map<String, Object>> list() {
        return experienceService.listViews();
    }

    /** 人工认可（重复次数达标时自动升级为优质经验） */
    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable String id) {
        KnowledgeNodeEntity node = experienceService.approve(id);
        return node == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(experienceService.view(node));
    }

    /** 人工否决（不再召回） */
    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable String id) {
        KnowledgeNodeEntity node = experienceService.reject(id);
        return node == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(experienceService.view(node));
    }
}
