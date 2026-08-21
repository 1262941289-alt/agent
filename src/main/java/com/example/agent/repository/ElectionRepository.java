package com.example.agent.repository;

import com.example.agent.entity.ElectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 选举结果 JPA 仓库。
 */
public interface ElectionRepository extends JpaRepository<ElectionEntity, String> {

    List<ElectionEntity> findAllByOrderByRoundDesc();
}