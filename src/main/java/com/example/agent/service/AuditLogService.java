package com.example.agent.service;

import com.example.agent.entity.AuditLogEntity;
import com.example.agent.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 审计日志服务：数据更改操作的统一留痕入口，其他服务在每次业务数据变更后调用 record。
 * <p>纯追加、不修改，作为数据安全审计与前端「更改操作」展示的数据源。
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** 追加一条审计日志；detail 为可变类型（可为 null），序列化为 JSON。 */
    public void record(String category, String action, String entityType, String entityId,
                       String summary, Object detail, String actor) {
        record(category, action, entityType, entityId, summary, detail, actor, null);
    }

    public void record(String category, String action, String entityType, String entityId,
                       String summary, Object detail, String actor, String runId) {
        try {
            AuditLogEntity e = new AuditLogEntity();
            e.setId(UUID.randomUUID().toString().replace("-", ""));
            e.setCategory(blank(category, "OTHER"));
            e.setAction(blank(action, "UPDATE"));
            e.setEntityType(blank(entityType, ""));
            e.setEntityId(blank(entityId, ""));
            e.setSummary(blank(summary, ""));
            e.setDetailJson(detail == null ? null : objectMapper.writeValueAsString(detail));
            e.setActor(blank(actor, "system"));
            e.setRunId(blank(runId, null));
            repository.save(e);
        } catch (Exception ex) {
            log.warn("审计日志写入失败 category={} action={}: {}", category, action, ex.getMessage());
        }
    }

    /** 最近 N 条日志（新→旧），可按 category 过滤。 */
    public List<Map<String, Object>> recent(int limit, String category) {
        int n = Math.max(1, Math.min(limit, 500));
        PageRequest page = PageRequest.of(0, n);
        List<AuditLogEntity> list = (category == null || category.isBlank())
                ? repository.findAllByOrderByCreatedAtDesc(page)
                : repository.findByCategoryOrderByCreatedAtDesc(category, page);
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (AuditLogEntity e : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("category", e.getCategory());
            m.put("action", e.getAction());
            m.put("entityType", blank(e.getEntityType(), ""));
            m.put("entityId", blank(e.getEntityId(), ""));
            m.put("summary", blank(e.getSummary(), ""));
            m.put("detail", e.getDetailJson() == null ? "" : e.getDetailJson());
            m.put("actor", blank(e.getActor(), ""));
            m.put("createdAt", e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
            out.add(m);
        }
        return out;
    }

    private static String blank(String s, String dflt) {
        return s == null || s.isBlank() ? dflt : s;
    }
}