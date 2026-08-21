package com.example.agent.memory;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短期记忆（会话上下文）：在多轮交互中维持上下文。
 * <p>以 conversationId 为维度，按会话保留最近若干轮对话，供规划/执行时注入上下文。
 * <p>为进程内状态（重启即清空）；跨会话的持久化累积交由 {@link GraphMemory} 长期记忆承担。
 */
@Component
public class ShortTermMemory {

    private final Map<String, Deque<String>> conversations = new ConcurrentHashMap<>();

    /** 每个会话保留的最近条目数 */
    private static final int CAPACITY = 20;

    /** 追加一条会话记录 */
    public void add(String conversationId, String entry) {
        if (conversationId == null || conversationId.isBlank() || entry == null || entry.isBlank()) {
            return;
        }
        Deque<String> queue = conversations.computeIfAbsent(conversationId, k -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(entry);
            while (queue.size() > CAPACITY) {
                queue.removeFirst();
            }
        }
    }

    /** 召回该会话最近 {@code limit} 条记录，拼接为上下文字符串；无记录返回空串 */
    public String recent(String conversationId, int limit) {
        if (conversationId == null || conversationId.isBlank()) {
            return "";
        }
        Deque<String> queue = conversations.get(conversationId);
        if (queue == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        synchronized (queue) {
            int skip = Math.max(0, queue.size() - limit);
            int i = 0;
            for (String e : queue) {
                if (i++ < skip) {
                    continue;
                }
                sb.append(e).append("\n");
            }
        }
        return sb.toString();
    }
}