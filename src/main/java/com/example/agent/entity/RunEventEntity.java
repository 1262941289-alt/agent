package com.example.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 一次执行的不可变事件日志（追加型事实源）。
 * <p>对应 {@link com.example.agent.agent.AgentEvent}，把原本仅内存/SSE 的事件流落库，
 * 支持历史查询、回放与复盘（“进 LLM 的内容可回放”的地基）。
 * <p>每条记录追加不变更；按 (runId, seq) 顺序重建完整执行轨迹。
 */
@Entity
@Table(name = "run_event", indexes = {
        @Index(name = "idx_run_seq", columnList = "run_id, seq"),
        @Index(name = "idx_run_ts", columnList = "run_id, created_at")
})
public class RunEventEntity {

    @Id
    @Column(length = 64)
    private String id;

    /** 归属的执行 runId */
    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    /** 同一 run 内的顺序号（进程内单调递增，用于回放重建） */
    @Column(nullable = false)
    private Long seq;

    /** 事件类型，如 run:started / step:status */
    @Column(nullable = false, length = 48)
    private String type;

    /** 事件特有载荷 JSON（不含 runId/ts） */
    @Column(name = "payload_json", columnDefinition = "text")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

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

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}