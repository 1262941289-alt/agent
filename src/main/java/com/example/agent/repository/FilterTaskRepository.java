package com.example.agent.repository;

import com.example.agent.entity.FilterTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 筛选任务 JPA 仓库。
 */
public interface FilterTaskRepository extends JpaRepository<FilterTaskEntity, String> {
}