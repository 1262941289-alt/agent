package com.example.agent.store;

import com.example.agent.model.Decision;
import com.example.agent.model.ItemAttributes;
import com.example.agent.model.ItemLayer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 筛选结果缓存（内存版）。
 * <p>同一数据项在内容未变化时，直接复用已计算的分层 / 属性 / 决策结果，
 * 避免重复调用本地 LLM，显著提升重复筛选速度。数据内容变化后自动失效。
 */
@Component
public class FilterCache {

    /** 单个数据项的缓存条目 */
    public static class Entry {
        public final String contentHash;
        public final ItemLayer layer;
        public final ItemAttributes attributes;
        public final Decision decision;

        public Entry(String contentHash, ItemLayer layer, ItemAttributes attributes, Decision decision) {
            this.contentHash = contentHash;
            this.layer = layer;
            this.attributes = attributes;
            this.decision = decision;
        }
    }

    private final ConcurrentMap<String, Entry> cache = new ConcurrentHashMap<>();

    public void put(String itemId, String contentHash, ItemLayer layer, ItemAttributes attributes, Decision decision) {
        cache.put(itemId, new Entry(contentHash, layer, attributes, decision));
    }

    /**
     * 按数据项 ID + 内容摘要取缓存；内容摘要不匹配（数据已变化）则视为未命中并清除旧条目。
     *
     * @return 命中返回条目，未命中返回 null
     */
    public Entry get(String itemId, String contentHash) {
        Entry entry = cache.get(itemId);
        if (entry == null) {
            return null;
        }
        if (!entry.contentHash.equals(contentHash)) {
            cache.remove(itemId);
            return null;
        }
        return entry;
    }

    public void clear() {
        cache.clear();
    }
}
