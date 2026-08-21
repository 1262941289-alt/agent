package com.example.agent.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 知识图谱节点仓库。
 */
public interface KnowledgeNodeRepository extends JpaRepository<KnowledgeNodeEntity, String> {

    Optional<KnowledgeNodeEntity> findFirstByNameIgnoreCase(String name);

    List<KnowledgeNodeEntity> findByNameContainingIgnoreCase(String keyword);

    List<KnowledgeNodeEntity> findByType(String type);
}