package com.example.agent.repository;

import com.example.agent.entity.CreditScoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 信用分 JPA 仓库。
 */
public interface CreditScoreRepository extends JpaRepository<CreditScoreEntity, String> {
}