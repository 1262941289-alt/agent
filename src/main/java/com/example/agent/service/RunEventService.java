package com.example.agent.service;

import com.example.agent.entity.RunEventEntity;
import com.example.agent.repository.RunEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * run 事件日志服务：把执行过程事件持久化为不可变事实源，供历史查询与回放。
 * <p>若 DB 写入失败只记日志、绝不干扰任务执行（与 {@code memory.remember} 的兜底策略一致）。
 */
@Service
public class RunEventService {

    private static final Logger log = LoggerFactory.getLogger(RunEventService.class);

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final RunEventRepository repository;
    private final ObjectMapper objectMapper;

    /** 每个 run 的进程内递减序号（避免并发读取 max seq + 写入的竞态） */
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public RunEventService(RunEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** 追加一条事件；安全失败，不影响执行流。 */
    public void append(String runId, String type, Map<String, Object> payload) {
        if (runId == null || type == null) {
            return;
        }
        try {
            RunEventEntity e = new RunEventEntity();
            e.setId(UUID.randomUUID().toString().replace("-", ""));
            e.setRunId(runId);
            e.setSeq(counters.computeIfAbsent(runId, k -> new AtomicLong()).incrementAndGet());
            e.setType(type);
            e.setCreatedAt(Instant.now());
            e.setPayloadJson(payload == null ? null : objectMapper.writeValueAsString(payload));
            repository.save(e);
        } catch (Exception ex) {
            log.warn("run 事件写入失败 runId={} type={}: {}", runId, type, ex.getMessage());
        }
    }

    /**
     * 回放某个 run 的有序事件，结构同 SSE 载荷（{@code type, runId, ts, ...payload}），
     * 前端可用同一 handleEvent 渲染。
     */
    public List<Map<String, Object>> replay(String runId) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<RunEventEntity> events = repository.findByRunIdOrderBySeqAsc(runId);
        for (RunEventEntity e : events) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", e.getType());
            m.put("runId", e.getRunId());
            m.put("ts", e.getCreatedAt() == null ? 0 : e.getCreatedAt().toEpochMilli());
            if (e.getPayloadJson() != null) {
                try {
                    m.putAll(objectMapper.readValue(e.getPayloadJson(), MAP_TYPE));
                } catch (Exception ignored) {
                    // 载荷解析失败：仅返回 runId/ts，前端按空载荷渲染
                }
            }
            out.add(m);
        }
        return out;
    }

    /** 最近若干次运行的摘要（新→旧），不含中间事件。 */
    public List<Map<String, Object>> history(int limit) {
        int n = Math.max(1, Math.min(limit, 200));
        List<Map<String, Object>> out = new ArrayList<>();
        List<String> runIds = repository.findDistinctRunIds();
        for (String runId : runIds) {
            if (out.size() >= n) {
                break;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("runId", runId);
            String goal = "";
            String termination = "";
            String finalAnswer = "";
            long ts = 0;
            for (RunEventEntity e : repository.findByRunIdOrderBySeqAsc(runId)) {
                m.put("ts", e.getCreatedAt() == null ? 0 : e.getCreatedAt().toEpochMilli());
                Map<String, Object> p = parse(e.getPayloadJson());
                if ("run:started".equals(e.getType()) && goal.isBlank()) {
                    goal = str(p.get("goal"));
                }
                if ("run:completed".equals(e.getType()) && termination.isBlank()) {
                    termination = str(p.get("termination"));
                    finalAnswer = str(p.get("finalAnswer"));
                }
                if ("run:failed".equals(e.getType()) && termination.isBlank()) {
                    termination = "FAILED";
                    finalAnswer = str(p.get("error"));
                }
            }
            m.put("goal", goal);
            m.put("termination", termination);
            m.put("finalAnswer", finalAnswer);
            out.add(m);
        }
        return out;
    }

    /** 释放某个 run 的进程内计数器（可选，避免长时运行内存累积）。 */
    public void clearCounter(String runId) {
        counters.remove(runId);
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}