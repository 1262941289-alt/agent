package com.example.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 数据更改操作审计日志：记录对业务数据（接入/清洗/决策/人工标注等）的每次变更，
 * 重点是「谁在何时改了什么」，用于数据安全留痕与前端审计台展示。
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @Column(length = 64)
    private String id;

    /** 操作分类：INGEST / CLEAN / DECISION / ANNOTATION / CREDIT ... */
    @Column(nullable = false, length = 32)
    private String category;

    /** 动作：CREATE / UPDATE / PASS / REJECT / CORRECT ... */
    @Column(nullable = false, length = 32)
    private String action;

    /** 被修改的实体类型：data_item / decision / annotation / credit_score ... */
    @Column(name = "entity_type", length = 64)
    private String entityType;

    /** 被修改的实体 ID */
    @Column(name = "entity_id", length = 128)
    private String entityId;

    /** 一句话摘要（人可读） */
    @Column(length = 512)
    private String summary;

    /** 变更明细 JSON（before/after 或载荷），可空 */
    @Column(name = "detail_json", columnDefinition = "text")
    private String detailJson;

    /** 操作主体：system / agent:<label> / human */
    @Column(length = 64)
    private String actor;

    /** 关联的 agent 执行 runId（可空） */
    @Column(name = "run_id", length = 64)
    private String runId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}