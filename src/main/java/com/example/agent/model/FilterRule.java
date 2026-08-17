package com.example.agent.model;

/**
 * 筛选规则：由自然语言描述，Agent 依据规则审查数据并判定。
 */
public class FilterRule {

    private String id;
    private String action;      // PASS / REJECT
    private int priority;       // 优先级，数字越小越先判断
    private String description; // 规则描述（自然语言）

    public FilterRule() {
    }

    public FilterRule(String id, String action, int priority, String description) {
        this.id = id;
        this.action = action;
        this.priority = priority;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
