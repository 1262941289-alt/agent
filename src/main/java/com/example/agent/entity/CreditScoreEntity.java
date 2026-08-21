package com.example.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Agent 信用分：0~100，默认 50，甜点 80，过热 100 触发反噬（阶段二/三使用）。
 */
@Entity
@Table(name = "agent_credit_score")
public class CreditScoreEntity {

    @Id
    @Column(length = 64)
    private String label;

    @Column(nullable = false)
    private int score = 50;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CreditScoreEntity() {
    }

    public CreditScoreEntity(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}