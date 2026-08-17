package com.example.agent.service;

import com.example.agent.config.FilterConfig;
import com.example.agent.model.DataLayer;
import com.example.agent.model.ItemLayer;
import com.example.agent.store.FilterStore;
import com.example.agent.util.PromptRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 第一阶段：数据分层。
 * Agent 根据分层定义将待筛选数据划分为指定层（L1/L2/L3...），结果存入 {@link FilterStore}。
 * 可替换为规则引擎或其他算法实现。
 */
@Service
public class LayeringService {

    private final FilterConfig filterConfig;
    private final ChatClient layeringChatClient;
    private final FilterStore store;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LayeringService(
            FilterConfig filterConfig,
            @Qualifier("layeringChatClient") ChatClient layeringChatClient,
            FilterStore store) {
        this.filterConfig = filterConfig;
        this.layeringChatClient = layeringChatClient;
        this.store = store;
    }

    /**
     * 对单个数据项做分层。
     *
     * @param itemId 数据项 ID
     * @return 分层结果
     */
    public ItemLayer layerItem(String itemId) {
        String layersText = renderLayers();
        String systemPrompt = PromptRenderer.render(
                PromptRenderer.load("prompts/layering-system.st"),
                Map.of("layers", layersText, "itemId", itemId)
        );
        // LLM 会自动调用工具：getItem 读取数据 → recordLayer 提交分层结论（写入 FilterStore）
        String response = layeringChatClient.prompt()
                .system(systemPrompt)
                .user("开始处理数据项 " + itemId)
                .call()
                .content();
        // 从存储读取 LLM 提交的结果
        ItemLayer result = store.getLayer(itemId);
        if (result == null || result.getLayerCode() == null) {
            // 兜底：模型可能以 JSON 文本而非工具调用的形式返回结论，尝试从响应中解析
            ItemLayer fallback = parseLayerFromText(itemId, response);
            return fallback != null ? fallback : ItemLayer.failed(itemId,
                    "LLM 未提交分层结果，请检查模型工具调用。最后响应内容：" + response);
        }
        return result;
    }

    /** 从 LLM 文本响应中兜底解析分层结论（优先 JSON，其次纯文本正则） */
    private ItemLayer parseLayerFromText(String itemId, String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        // 1) 尝试 JSON：形如 {"itemId":"D001","layerCode":"L1","reason":"..."}
        try {
            JsonNode root = objectMapper.readTree(PromptRenderer.extractJsonObject(response));
            String code = root.path("layerCode").asText(null);
            String reason = root.path("reason").asText(null);
            String norm = code == null ? null : normalizeLayerCode(code);
            if (norm != null) {
                return buildLayer(itemId, norm, reason == null ? "（从文本兜底解析）" : reason);
            }
        } catch (Exception ignored) {
            // 继续尝试纯文本
        }
        // 2) 尝试纯文本：形如「数据项 D002 属于 L3 风险层」「分为第2层」「L1」
        // 按优先级匹配，避免把数据项 ID（如 D002）误判为层编码
        String[] patterns = {
                "(?i)\\bL\\s*\\d+\\b",        // L3 / L 3
                "第\\s*\\d+\\s*层",            // 第2层
                "(?<![A-Za-z0-9])\\d+(?![A-Za-z0-9])"  // 独立数字 3（排除 D002 这类带前缀的编号）
        };
        for (String p : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(p).matcher(response);
            if (m.find()) {
                String code = normalizeLayerCode(m.group(0));
                if (code != null) {
                    return buildLayer(itemId, code, "（从纯文本兜底解析）" + response.trim());
                }
            }
        }
        return null;
    }

    /** 归一化层编码：L3 / 第2层 / 3 -> L3；无法识别返回 null */
    private String normalizeLayerCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim()
                .replaceAll("第\\s*", "L")
                .replaceAll("\\s*层", "")
                .replaceAll("\\s+", "");
        if (s.matches("(?i)L\\d+")) {
            return s.toUpperCase();
        }
        if (s.matches("\\d+")) {
            return "L" + s;
        }
        return null;
    }

    private ItemLayer buildLayer(String itemId, String code, String reason) {
        ItemLayer layer = new ItemLayer();
        layer.setItemId(itemId);
        layer.setLayerCode(code);
        layer.setReason(reason);
        return layer;
    }

    private String renderLayers() {
        StringBuilder sb = new StringBuilder();
        for (DataLayer layer : filterConfig.getLayers()) {
            sb.append(layer.getCode()).append(" - ")
                    .append(layer.getName()).append(": ")
                    .append(layer.getDescription()).append("\n");
        }
        return sb.toString();
    }
}
