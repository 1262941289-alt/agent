package com.example.agent.repository;

import com.example.agent.entity.LayerResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 分层结果 JPA 仓库。
 */
public interface LayerResultRepository extends JpaRepository<LayerResultEntity, Long> {

    Optional<LayerResultEntity> findFirstByItemIdAndTaskIdOrderByIdDesc(String itemId, String taskId);

    void deleteByItemIdAndTaskId(String itemId, String taskId);

    void deleteAllByTaskId(String taskId);

    List<LayerResultEntity> findByTaskId(String taskId);
}