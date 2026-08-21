package com.example.agent.repository;

import com.example.agent.entity.AttributeResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 属性结果 JPA 仓库。
 */
public interface AttributeResultRepository extends JpaRepository<AttributeResultEntity, Long> {

    Optional<AttributeResultEntity> findFirstByItemIdAndTaskIdOrderByIdDesc(String itemId, String taskId);

    void deleteByItemIdAndTaskId(String itemId, String taskId);

    void deleteAllByTaskId(String taskId);

    List<AttributeResultEntity> findByTaskId(String taskId);
}