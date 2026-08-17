package com.example.agent.model;

/**
 * 单个数据项的筛选决策。
 */
public class Decision {

    private String itemId;
    private boolean passed;          // true=通过，false=拒绝
    private String reason;           // 判定依据
    private String matchedRuleId;    // 命中的规则 ID，未命中则为 null
    private String status = "OK";

    public Decision() {
    }

    public static Decision failed(String itemId, String reason) {
        Decision d = new Decision();
        d.setItemId(itemId);
        d.setStatus("FAILED");
        d.setReason(reason);
        return d;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getMatchedRuleId() {
        return matchedRuleId;
    }

    public void setMatchedRuleId(String matchedRuleId) {
        this.matchedRuleId = matchedRuleId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
