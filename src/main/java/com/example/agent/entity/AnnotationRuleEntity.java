package com.example.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 人工标注沉淀出的确定性规则：字段的原始值 → 应纠正为的值。
 * 命中后由清洗引擎直接套用（规则兜底，顶替 LLM），按 (field_name, raw_value) 幂等去重。
 */
@Entity
@Table(name = "annotation_rule", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ann_rule_field_raw", columnNames = {"field_name", "raw_value"})
})
public class AnnotationRuleEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "field_name", length = 128, nullable = false)
    private String fieldName;

    @Column(name = "raw_value", length = 512, nullable = false)
    private String rawValue;

    @Column(name = "corrected_value", length = 512)
    private String correctedValue;

    @Column(name = "source_item_id", length = 64)
    private String sourceItemId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AnnotationRuleEntity() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getRawValue() {
        return rawValue;
    }

    public void setRawValue(String rawValue) {
        this.rawValue = rawValue;
    }

    public String getCorrectedValue() {
        return correctedValue;
    }

    public void setCorrectedValue(String correctedValue) {
        this.correctedValue = correctedValue;
    }

    public String getSourceItemId() {
        return sourceItemId;
    }

    public void setSourceItemId(String sourceItemId) {
        this.sourceItemId = sourceItemId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}