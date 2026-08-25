package com.example.agent.web;

import com.example.agent.service.ApprovalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 审批/人机门入口：查看待审批请求 + 批准/拒绝高风险工具调用。
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /** GET /api/approvals —— 进行中的审批请求列表。 */
    @GetMapping
    public List<Map<String, Object>> pending() {
        return approvalService.pendingList();
    }

    /** POST /api/approvals/{requestId}/approve —— 放行该工具调用。 */
    @PostMapping("/{requestId}/approve")
    public Map<String, Object> approve(@PathVariable String requestId) {
        return reply(requestId, approvalService.decide(requestId, true), "批准");
    }

    /** POST /api/approvals/{requestId}/reject —— 拒绝该工具调用。 */
    @PostMapping("/{requestId}/reject")
    public Map<String, Object> reject(@PathVariable String requestId) {
        return reply(requestId, approvalService.decide(requestId, false), "拒绝");
    }

    private static Map<String, Object> reply(String requestId, boolean applied, String action) {
        if (applied) {
            return Map.of("applied", true, "requestId", requestId, "message", action + "成功，工具调用将" + ("批准".equals(action) ? "继续执行" : "被终止"));
        }
        return Map.of("applied", false, "requestId", requestId,
                "message", "请求不存在或已被裁决（可能已超时）。");
    }
}