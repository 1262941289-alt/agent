package com.example.agent.repository;

import com.example.agent.entity.AnnotationRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 标注规则仓库：按 (字段 + 原始值) 幂等查找已沉淀的确定性纠正规则。
 */
public interface AnnotationRuleRepository extends JpaRepository<AnnotationRuleEntity, String> {

    Optional<AnnotationRuleEntity> findFirstByFieldNameAndRawValue(String fieldName, String rawValue);
}