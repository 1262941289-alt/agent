package com.example.agent.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 知识图谱关系：节点间的有向边。支持双向链接（正向查询 + 反向 backlink）。
 */
@Entity
@Table(name = "knowledge_relation", indexes = {
        @Index(name = "idx_kr_source", columnList = "source_id"),
        @Index(name = "idx_kr_target", columnList = "target_id")
})
public class KnowledgeRelationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", length = 64, nullable = false)
    private String sourceId;

    @Column(name = "target_id", length = 64, nullable = false)
    private String targetId;

    @Column(name = "relation_type", length = 64)
    private String relationType;

    /** 是否为双向链接（写入时自动生成反向边） */
    @Column(name = "bidirectional")
    private boolean bidirectional;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public KnowledgeRelationEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public boolean isBidirectional() {
        return bidirectional;
    }

    public void setBidirectional(boolean bidirectional) {
        this.bidirectional = bidirectional;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}