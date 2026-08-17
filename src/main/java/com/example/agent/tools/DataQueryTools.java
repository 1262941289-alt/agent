package com.example.agent.tools;

import com.example.agent.model.Attribute;
import com.example.agent.model.DataItem;
import com.example.agent.model.ItemAttributes;
import com.example.agent.model.ItemLayer;
import com.example.agent.store.DataRepository;
import com.example.agent.store.FilterStore;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 数据查询工具：供 Agent 在分层、属性抽取、规则筛选阶段查询原始数据。
 */
@Component
public class DataQueryTools {

    private final DataRepository dataRepository;
    private final FilterStore store;

    public DataQueryTools(DataRepository dataRepository, FilterStore store) {
        this.dataRepository = dataRepository;
        this.store = store;
    }

    @Tool(description = "根据数据项 ID 获取该数据项的原始内容")
    public String getItem(@ToolParam(description = "数据项 ID") String itemId) {
        Optional<DataItem> item = dataRepository.findById(itemId);
        return item.map(d -> "数据项[" + d.getId() + "]内容：" + d.getContent())
                .orElse("未找到数据项：" + itemId);
    }

    @Tool(description = "查询数据项被划分到的层编码、层名及原因")
    public String getLayerOf(@ToolParam(description = "数据项 ID") String itemId) {
        ItemLayer layer = store.getLayer(itemId);
        if (layer == null || layer.getLayerCode() == null) {
            return "数据项 " + itemId + " 尚未分层";
        }
        return "数据项 " + itemId + " 属于层 [" + layer.getLayerCode() + "]"
                + (layer.getLayerName() != null ? "（" + layer.getLayerName() + "）" : "")
                + "，原因：" + layer.getReason();
    }

    @Tool(description = "查询数据项已抽取的属性，返回 key=value 列表")
    public String getAttributesOf(@ToolParam(description = "数据项 ID") String itemId) {
        ItemAttributes attrs = store.getAttributes(itemId);
        if (attrs == null || attrs.getAttributes().isEmpty()) {
            return "数据项 " + itemId + " 暂无属性";
        }
        StringBuilder sb = new StringBuilder("数据项 " + itemId + " 属性：");
        for (Attribute a : attrs.getAttributes()) {
            sb.append(a.getKey()).append("=").append(a.getValue()).append("；");
        }
        return sb.toString();
    }
}
