package com.example.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 选举结果（阶段三）：每轮结束对能力 agent 投票，得票最高者成为下一轮 Manager。
 * <p>candidatesJson 存各候选的加权得分明细（{[candidate, composite, ...]}），winner 为当选标签。
 */
@Entity
@Table(name = "agent_election")
public class ElectionEntity {

    @Id
    @Column(length = 64)
    private String id;

    /** 轮次（termRound），与分配记录同源 */
    @Column(nullable = false)
    private int round;

    /** 本轮实际执行者（管理者 label，冷启动为 "default"） */
    @Column(name = "manager_ref", length = 64)
    private String managerRef;

    /** 当选者（下一轮 Manager）= 能力 agent label */
    @Column(length = 64)
    private String winner;

    /** 各候选投票得分明细 JSON */
    @Column(name = "candidates_json", columnDefinition = "text")
    private String candidatesJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public String getManagerRef() {
        return managerRef;
    }

    public void setManagerRef(String managerRef) {
        this.managerRef = managerRef;
    }

    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    public String getCandidatesJson() {
        return candidatesJson;
    }

    public void setCandidatesJson(String candidatesJson) {
        this.candidatesJson = candidatesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}