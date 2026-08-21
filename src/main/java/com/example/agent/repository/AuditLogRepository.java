package com.example.agent.repository;

import com.example.agent.entity.AuditLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 审计日志 JPA 仓库。
 */
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, String> {

    List<AuditLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditLogEntity> findByCategoryOrderByCreatedAtDesc(String category, Pageable pageable);
}