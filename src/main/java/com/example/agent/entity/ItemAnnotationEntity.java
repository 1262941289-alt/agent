package com.example.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 数据条目级人工标注记录：对某条已接入数据的三态评价（正确/错误/不确定）+ 字段级纠错。
 */
@Entity
@Table(name = "item_annotation", indexes = {
        @Index(name = "idx_item_ann_item", columnList = "item_id")
})
public class ItemAnnotationEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "item_id", length = 64, nullable = false)
    private String itemId;

    /** CORRECT / WRONG / UNCERTAIN */
    @Column(length = 16)
    private String verdict;

    /** 字段纠错 JSON 数组：[{field, raw, corrected}] */
    @Lob
    @Column(name = "corrections_json", columnDefinition = "TEXT")
    private String correctionsJson;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ItemAnnotationEntity() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public String getCorrectionsJson() {
        return correctionsJson;
    }

    public void setCorrectionsJson(String correctionsJson) {
        this.correctionsJson = correctionsJson;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}