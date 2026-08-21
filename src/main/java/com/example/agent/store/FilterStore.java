package com.example.agent.store;

import com.example.agent.entity.AttributeResultEntity;
import com.example.agent.entity.DecisionResultEntity;
import com.example.agent.entity.LayerResultEntity;
import com.example.agent.model.Attribute;
import com.example.agent.model.Decision;
import com.example.agent.model.ItemAttributes;
import com.example.agent.model.ItemLayer;
import com.example.agent.repository.AttributeResultRepository;
import com.example.agent.repository.DecisionResultRepository;
import com.example.agent.repository.LayerResultRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 筛选过程的中间/结果存储（MySQL 持久化版）。
 * <p>所有写入与读取均以当前任务（{@link TaskContext} 中的 taskId）为维度，
 * 保证并行任务之间的分层/属性/决策结果相互隔离。
 * 记录：每项数据的分层结果、抽取属性、筛选决策，均落库到对应结果表。
 */
@Component
public class FilterStore {

    private final LayerResultRepository layerResultRepository;
    private final AttributeResultRepository attributeResultRepository;
    private final DecisionResultRepository decisionResultRepository;
    private final TaskContext taskContext;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FilterStore(LayerResultRepository layerResultRepository,
                       AttributeResultRepository attributeResultRepository,
                       DecisionResultRepository decisionResultRepository,
                       TaskContext taskContext) {
        this.layerResultRepository = layerResultRepository;
        this.attributeResultRepository = attributeResultRepository;
        this.decisionResultRepository = decisionResultRepository;
        this.taskContext = taskContext;
    }

    // ---------- 分层 ----------

    @Transactional
    public void setLayer(String itemId, String layerCode, String reason) {
        String taskId = taskContext.get();
        layerResultRepository.deleteByItemIdAndTaskId(itemId, taskId);
        LayerResultEntity entity = new LayerResultEntity();
        entity.setTaskId(taskId);
        entity.setItemId(itemId);
        entity.setLayerCode(layerCode);
        entity.setReason(reason);
        layerResultRepository.save(entity);
    }

    public ItemLayer getLayer(String itemId) {
        return layerResultRepository.findFirstByItemIdAndTaskIdOrderByIdDesc(itemId, taskContext.get())
                .map(this::toLayerModel)
                .orElse(null);
    }

    // ---------- 属性 ----------

    @Transactional
    public void setAttributes(String itemId, String attributesJson) {
        String taskId = taskContext.get();
        attributeResultRepository.deleteByItemIdAndTaskId(itemId, taskId);
        AttributeResultEntity entity = new AttributeResultEntity();
        entity.setTaskId(taskId);
        entity.setItemId(itemId);
        entity.setAttributesJson(attributesJson);
        attributeResultRepository.save(entity);
    }

    public ItemAttributes getAttributes(String itemId) {
        return attributeResultRepository.findFirstByItemIdAndTaskIdOrderByIdDesc(itemId, taskContext.get())
                .map(this::toAttributesModel)
                .orElse(null);
    }

    // ---------- 决策 ----------

    @Transactional
    public void setDecision(String itemId, boolean pass, String reason, String ruleId) {
        String taskId = taskContext.get();
        decisionResultRepository.deleteByItemIdAndTaskId(itemId, taskId);
        DecisionResultEntity entity = new DecisionResultEntity();
        entity.setTaskId(taskId);
        entity.setItemId(itemId);
        entity.setPassed(pass);
        entity.setReason(reason);
        entity.setMatchedRuleId(ruleId);
        decisionResultRepository.save(entity);
    }

    public Decision getDecision(String itemId) {
        return decisionResultRepository.findFirstByItemIdAndTaskIdOrderByIdDesc(itemId, taskContext.get())
                .map(this::toDecisionModel)
                .orElse(null);
    }

    // ---------- 按任务查询完整结果 ----------

    public List<ItemLayer> layersOf(String taskId) {
        return layerResultRepository.findByTaskId(taskId).stream()
                .map(this::toLayerModel)
                .toList();
    }

    public List<ItemAttributes> attributesOf(String taskId) {
        return attributeResultRepository.findByTaskId(taskId).stream()
                .map(this::toAttributesModel)
                .toList();
    }

    public List<Decision> decisionsOf(String taskId) {
        return decisionResultRepository.findByTaskId(taskId).stream()
                .map(this::toDecisionModel)
                .toList();
    }

    // ---------- 实体 → 模型 ----------

    private ItemLayer toLayerModel(LayerResultEntity e) {
        ItemLayer layer = new ItemLayer();
        layer.setItemId(e.getItemId());
        layer.setLayerCode(e.getLayerCode());
        layer.setLayerName(e.getLayerName());
        layer.setReason(e.getReason());
        layer.setStatus(e.getStatus() != null ? e.getStatus() : "OK");
        return layer;
    }

    private ItemAttributes toAttributesModel(AttributeResultEntity e) {
        ItemAttributes attrs = new ItemAttributes();
        attrs.setItemId(e.getItemId());
        attrs.setStatus(e.getStatus() != null ? e.getStatus() : "OK");
        try {
            JsonNode root = objectMapper.readTree(e.getAttributesJson());
            List<Attribute> list = new ArrayList<>();
            if (root != null && root.isObject()) {
                root.fields().forEachRemaining(f ->
                        list.add(new Attribute(f.getKey(), f.getValue().asText())));
            }
            attrs.setAttributes(list);
        } catch (Exception ex) {
            attrs.setStatus("FAILED");
            attrs.getAttributes().add(new Attribute("错误", "属性 JSON 解析失败: " + ex.getMessage()));
        }
        return attrs;
    }

    private Decision toDecisionModel(DecisionResultEntity e) {
        Decision decision = new Decision();
        decision.setItemId(e.getItemId());
        decision.setPassed(e.isPassed());
        decision.setReason(e.getReason());
        decision.setMatchedRuleId(e.getMatchedRuleId());
        decision.setStatus(e.getStatus() != null ? e.getStatus() : "OK");
        return decision;
    }
}