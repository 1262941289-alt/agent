package com.example.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 数据项实体：一条待筛选的原始数据。
 */
@Entity
@Table(name = "data_item", indexes = {
        @Index(name = "idx_data_item_status", columnList = "status"),
        @Index(name = "idx_data_item_source", columnList = "source_type")
})
public class DataItemEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Lob
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 确定性清洗后的内容（JSON），与原始 content 并列以支持前后对比 */
    @Lob
    @Column(name = "cleaned_content", columnDefinition = "TEXT")
    private String cleanedContent;

    /** 清洗变更日志（JSON 数组），逐字段记录 before/after */
    @Lob
    @Column(name = "cleaning_log", columnDefinition = "TEXT")
    private String cleaningLog;

    /** 数据来源：REST / DB / MQ / FILE */
    @Column(name = "source_type", length = 16)
    private String sourceType;

    /** 处理状态：PENDING / FILTERED */
    @Column(length = 16)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public DataItemEntity() {
    }

    public DataItemEntity(String id, String content, String sourceType) {
        this.id = id;
        this.content = content;
        this.sourceType = sourceType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCleanedContent() {
        return cleanedContent;
    }

    public void setCleanedContent(String cleanedContent) {
        this.cleanedContent = cleanedContent;
    }

    public String getCleaningLog() {
        return cleaningLog;
    }

    public void setCleaningLog(String cleaningLog) {
        this.cleaningLog = cleaningLog;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
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