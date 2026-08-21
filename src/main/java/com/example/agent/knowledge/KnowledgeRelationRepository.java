package com.example.agent.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 知识图谱关系仓库。
 */
public interface KnowledgeRelationRepository extends JpaRepository<KnowledgeRelationEntity, Long> {

    List<KnowledgeRelationEntity> findBySourceId(String sourceId);

    List<KnowledgeRelationEntity> findByTargetId(String targetId);

    boolean existsBySourceIdAndTargetIdAndRelationType(String sourceId, String targetId, String relationType);
}