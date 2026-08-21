package com.example.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 分配记录：Manager 每次把子任务分配给某个能力 agent 时落一条，
 * 用于统计各 agent 成功率 / 平均耗时 / 负载均衡度，并作为阶段三选举与信用分奖惩的数据底座。
 */
@Entity
@Table(name = "agent_allocation_record")
public class AllocationRecordEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "round_no", nullable = false)
    private int round;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Column(name = "goal", columnDefinition = "text")
    private String goal;

    @Column(name = "subtask_goal", columnDefinition = "text")
    private String subtaskGoal;

    @Column(name = "capability", length = 64)
    private String capability;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AllocationRecordEntity() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public int getStepNo() {
        return stepNo;
    }

    public void setStepNo(int stepNo) {
        this.stepNo = stepNo;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getSubtaskGoal() {
        return subtaskGoal;
    }

    public void setSubtaskGoal(String subtaskGoal) {
        this.subtaskGoal = subtaskGoal;
    }

    public String getCapability() {
        return capability;
    }

    public void setCapability(String capability) {
        this.capability = capability;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}