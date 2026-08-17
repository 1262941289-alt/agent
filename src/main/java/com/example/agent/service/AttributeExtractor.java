package com.example.agent.service;

import com.example.agent.config.FilterConfig;
import com.example.agent.model.Attribute;
import com.example.agent.model.AttributeDef;
import com.example.agent.model.ItemAttributes;
import com.example.agent.store.FilterStore;
import com.example.agent.util.PromptRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 第二阶段：属性抽取。
 * Agent 根据业务要求的属性定义，从数据项中抽取并结构化属性存入 {@link FilterStore}。
 * 可替换为规则引擎 / NER 模型实现。
 */
@Service
public class AttributeExtractor {

    private final FilterConfig filterConfig;
    private final ChatClient attributeChatClient;
    private final FilterStore store;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AttributeExtractor(
            FilterConfig filterConfig,
            @Qualifier("attributeChatClient") ChatClient attributeChatClient,
            FilterStore store) {
        this.filterConfig = filterConfig;
        this.attributeChatClient = attributeChatClient;
        this.store = store;
    }

    /**
     * 对单个数据项抽取属性。
     *
     * @param itemId 数据项 ID
     * @return 属性结果
     */
    public ItemAttributes extractAttributes(String itemId) {
        String attrsText = renderAttributes();
        String systemPrompt = PromptRenderer.render(
                PromptRenderer.load("prompts/attribute-system.st"),
                Map.of("attributes", attrsText, "itemId", itemId)
        );
        // LLM 会自动调用工具：getItem 读取数据 → submitAttributes 提交 JSON 属性（写入 FilterStore）
        String response = attributeChatClient.prompt()
                .system(systemPrompt)
                .user("开始抽取数据项 " + itemId + " 的属性")
                .call()
                .content();
        // 从存储读取 LLM 提交的结果
        ItemAttributes result = store.getAttributes(itemId);
        if (result == null || "FAILED".equals(result.getStatus()) || result.getAttributes().isEmpty()) {
            // 兜底：模型可能以 JSON 文本而非工具调用的形式返回属性，尝试从响应中解析
            ItemAttributes fallback = parseAttributesFromText(itemId, response);
            return fallback != null ? fallback : ItemAttributes.failed(itemId,
                    "LLM 未提交属性结果。最后响应内容：" + response);
        }
        return result;
    }

    /** 从 LLM 文本响应中兜底解析属性（形如 {"金额":"10000","地区":"中国"}） */
    private ItemAttributes parseAttributesFromText(String itemId, String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(PromptRenderer.extractJsonObject(response));
            if (root == null || !root.isObject() || root.isEmpty()) {
                return null;
            }
            ItemAttributes attrs = new ItemAttributes();
            attrs.setItemId(itemId);
            List<Attribute> list = new ArrayList<>();
            root.fields().forEachRemaining(e ->
                    list.add(new Attribute(e.getKey(), e.getValue().asText("未知"))));
            attrs.setAttributes(list);
            return attrs;
        } catch (Exception ex) {
            return null;
        }
    }

    private String renderAttributes() {
        StringBuilder sb = new StringBuilder();
        for (AttributeDef def : filterConfig.getAttributes()) {
            sb.append("- ").append(def.getKey()).append(": ").append(def.getDescription()).append("\n");
        }
        return sb.toString();
    }
}
