package com.example.agent.tools;

import com.example.agent.service.AuditLogService;
import com.example.agent.store.FilterStore;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 规则筛选阶段工具：Agent 依据规则审查后，通过 submitDecision 提交筛选决策。
 */
@Component
public class DecisionTools {

    private final FilterStore store;
    private final AuditLogService auditLogService;

    public DecisionTools(FilterStore store, AuditLogService auditLogService) {
        this.store = store;
        this.auditLogService = auditLogService;
    }

    @Tool(description = "提交数据项的筛选决策。pass=true 表示通过，pass=false 表示拒绝")
    public void submitDecision(
            @ToolParam(description = "数据项 ID") String itemId,
            @ToolParam(description = "是否通过：true 通过，false 拒绝") boolean pass,
            @ToolParam(description = "判定理由，说明依据的分层/属性/规则") String reason,
            @ToolParam(description = "命中的规则 ID，如 R1；若无命中规则传 null") String ruleId) {
        store.setDecision(itemId, pass, reason, ruleId);
        auditLogService.record("DECISION", pass ? "PASS" : "REJECT", "data_item", itemId,
                "筛选决策：" + (pass ? "通过" : "拒绝"),
                Map.of("passed", pass, "reason", reason == null ? "" : reason,
                        "ruleId", ruleId == null ? "" : ruleId), "agent:data");
    }
}
