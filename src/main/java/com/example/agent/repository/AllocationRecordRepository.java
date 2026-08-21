package com.example.agent.repository;

import com.example.agent.entity.AllocationRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 分配记录 JPA 仓库。
 */
public interface AllocationRecordRepository extends JpaRepository<AllocationRecordEntity, String> {
}