package com.example.agent.tools;

import com.example.agent.store.FilterStore;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 属性抽取阶段工具：模型在读取数据后，通过 submitAttributes 提交结构化属性。
 */
@Component
public class AttributeRecordTools {

    private final FilterStore store;

    public AttributeRecordTools(FilterStore store) {
        this.store = store;
    }

    @Tool(description = "提交数据项的抽取属性。attributesJson 必须是 JSON 对象字符串，如 {\"金额\":\"10000\",\"地区\":\"中国\"}，只包含要求抽取的属性")
    public void submitAttributes(
            @ToolParam(description = "数据项 ID") String itemId,
            @ToolParam(description = "属性 JSON 对象字符串") String attributesJson) {
        store.setAttributes(itemId, attributesJson);
    }
}
