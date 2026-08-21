package com.example.agent.repository;

import com.example.agent.entity.DataItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 数据项 JPA 仓库。
 */
public interface DataItemRepository extends JpaRepository<DataItemEntity, String> {
}