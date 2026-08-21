package com.example.agent.repository;

import com.example.agent.entity.DecisionResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 筛选决策结果 JPA 仓库。
 */
public interface DecisionResultRepository extends JpaRepository<DecisionResultEntity, Long> {

    Optional<DecisionResultEntity> findFirstByItemIdAndTaskIdOrderByIdDesc(String itemId, String taskId);

    void deleteByItemIdAndTaskId(String itemId, String taskId);

    void deleteAllByTaskId(String taskId);

    List<DecisionResultEntity> findByTaskId(String taskId);
}