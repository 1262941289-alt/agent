package com.example.agent.web;

import com.example.agent.cleaning.CleaningReport;
import com.example.agent.cleaning.DeterministicCleaningEngine;
import com.example.agent.repository.DataItemRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 清洗结果只读接口：查询某条数据的前后对比，或临时对一段 JSON 试跑清洗规则。
 */
@RestController
@RequestMapping("/api/clean")
public class CleaningController {

    private final DeterministicCleaningEngine cleaningEngine;
    private final DataItemRepository dataItemRepository;
    private final ObjectMapper objectMapper;

    public CleaningController(DeterministicCleaningEngine cleaningEngine,
                              DataItemRepository dataItemRepository,
                              ObjectMapper objectMapper) {
        this.cleaningEngine = cleaningEngine;
        this.dataItemRepository = dataItemRepository;
        this.objectMapper = objectMapper;
    }

    /** 已注册规则清单。GET /api/clean/rules */
    @GetMapping("/rules")
    public List<String> rules() {
        return cleaningEngine.ruleNames();
    }

    /** 查询某条已接入数据的清洗结果。GET /api/clean/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        return dataItemRepository.findById(id)
                .map(e -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("id", e.getId());
                    out.put("content", e.getContent());
                    out.put("cleanedContent", e.getCleanedContent());
                    out.put("cleaningLog", e.getCleaningLog());
                    return ResponseEntity.ok(out);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** 试跑：对一段 JSON 对象内容执行清洗，返回前后对比。POST /api/clean/try */
    @PostMapping("/try")
    public ResponseEntity<CleaningReport> tryClean(@RequestBody Map<String, Object> body) {
        Object content = body.get("content");
        if (!(content instanceof String s) || s.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Map<String, String> raw = objectMapper.readValue(
                    s, new TypeReference<LinkedHashMap<String, String>>() {});
            return ResponseEntity.ok(cleaningEngine.clean(raw));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}