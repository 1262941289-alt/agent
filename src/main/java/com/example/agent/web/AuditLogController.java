package com.example.agent.web;

import com.example.agent.service.AuditLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 审计日志查询入口：展示数据更改操作留痕。
 */
@RestController
@RequestMapping("/api/audit")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /** GET /api/audit?limit=100&category=DECISION —— 最近 N 条审计日志（可按分类过滤）。 */
    @GetMapping
    public List<Map<String, Object>> recent(@RequestParam(defaultValue = "100") int limit,
                                            @RequestParam(required = false) String category) {
        return auditLogService.recent(limit, category);
    }
}