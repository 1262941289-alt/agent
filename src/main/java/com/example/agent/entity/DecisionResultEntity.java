package com.example.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 筛选决策结果实体：数据项的通过/拒绝判定结论。
 */
@Entity
@Table(name = "decision_result", indexes = {
        @Index(name = "idx_decision_item", columnList = "item_id"),
        @Index(name = "idx_decision_task", columnList = "task_id")
})
public class DecisionResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", length = 64)
    private String taskId;

    @Column(name = "item_id", length = 64, nullable = false)
    private String itemId;

    @Column(name = "passed", nullable = false)
    private boolean passed;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "matched_rule_id", length = 64)
    private String matchedRuleId;

    @Column(length = 16)
    private String status = "OK";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public DecisionResultEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}