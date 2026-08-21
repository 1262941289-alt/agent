package com.example.agent.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 执行过程中产生的一条事件（SSE 事件契约）。
 * <p>序列化只发生在 sink 边界一次：{@link #toDataMap()} 返回结构化 Map，
 * 由传输层用 Jackson 序列化一次为 JSON，避免“把 JSON 字符串再放进 JSON”导致的二次转义。
 */
public class AgentEvent {

    /** 形如 run:started / step:status / step:reflection */
    private final String type;
    private final String runId;
    /** 毫秒时间戳 */
    private final long ts;
    /** 事件特有字段（不含 runId/ts，由 toDataMap 注入） */
    private final Map<String, Object> payload;

    public AgentEvent(String type, String runId, Map<String, Object> payload) {
        this.type = type;
        this.runId = runId;
        this.ts = System.currentTimeMillis();
        this.payload = payload == null ? Map.of() : payload;
    }

    public String getType() {
        return type;
    }

    public String getRunId() {
        return runId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    /** 合并为 SSE data 载体的结构化对象（单次序列化边界） */
    public Map<String, Object> toDataMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", runId);
        m.put("ts", ts);
        m.putAll(payload);
        return m;
    }
}