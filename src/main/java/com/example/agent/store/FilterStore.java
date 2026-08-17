package com.example.agent.store;

import com.example.agent.model.Attribute;
import com.example.agent.model.Decision;
import com.example.agent.model.ItemAttributes;
import com.example.agent.model.ItemLayer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 筛选过程的中间/结果存储（内存版）。
 * 记录：每项数据的分层结果、抽取属性、筛选决策。
 * 后续可替换为 JDBC/Redis 实现以支持持久化与并发任务。
 */
@Component
public class FilterStore {

    private final ConcurrentMap<String, ItemLayer> layers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ItemAttributes> attributes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Decision> decisions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 清空上一次任务产生的中间数据 */
    public void clear() {
        layers.clear();
        attributes.clear();
        decisions.clear();
    }

    // ---------- 分层 ----------

    public void setLayer(String itemId, String layerCode, String reason) {
        ItemLayer l = new ItemLayer();
        l.setItemId(itemId);
        l.setLayerCode(layerCode);
        l.setReason(reason);
        layers.put(itemId, l);
    }

    public ItemLayer getLayer(String itemId) {
        return layers.get(itemId);
    }

    // ---------- 属性 ----------

    public void setAttributes(String itemId, String attributesJson) {
        ItemAttributes itemAttrs = new ItemAttributes();
        itemAttrs.setItemId(itemId);
        try {
            JsonNode root = objectMapper.readTree(attributesJson);
            List<Attribute> attrs = new ArrayList<>();
            if (root != null && root.isObject()) {
                root.fields().forEachRemaining(e ->
                        attrs.add(new Attribute(e.getKey(), e.getValue().asText())));
            }
            itemAttrs.setAttributes(attrs);
        } catch (Exception ex) {
            itemAttrs.setStatus("FAILED");
            itemAttrs.getAttributes().add(new Attribute("错误", "属性 JSON 解析失败: " + ex.getMessage()));
        }
        attributes.put(itemId, itemAttrs);
    }

    public ItemAttributes getAttributes(String itemId) {
        return attributes.get(itemId);
    }

    // ---------- 决策 ----------

    public void setDecision(String itemId, boolean pass, String reason, String ruleId) {
        Decision d = new Decision();
        d.setItemId(itemId);
        d.setPassed(pass);
        d.setReason(reason);
        d.setMatchedRuleId(ruleId);
        decisions.put(itemId, d);
    }

    public Decision getDecision(String itemId) {
        return decisions.get(itemId);
    }

    // ---------- 结果快照 ----------

    public Map<String, ItemLayer> allLayers() {
        return layers;
    }

    public Map<String, ItemAttributes> allAttributes() {
        return attributes;
    }

    public Map<String, Decision> allDecisions() {
        return decisions;
    }
}
