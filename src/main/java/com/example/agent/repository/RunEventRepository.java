package com.example.agent.repository;

import com.example.agent.entity.RunEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * run 事件日志 JPA 仓库：追加型事实源的持久化入口。
 */
public interface RunEventRepository extends JpaRepository<RunEventEntity, String> {

    List<RunEventEntity> findByRunIdOrderBySeqAsc(String runId);

    /** 去重列出所有 runId，按最近事件倒序（历史列表用）。 */
    @Query(value = "SELECT run_id FROM run_event GROUP BY run_id ORDER BY MAX(created_at) DESC", nativeQuery = true)
    List<String> findDistinctRunIds();
}