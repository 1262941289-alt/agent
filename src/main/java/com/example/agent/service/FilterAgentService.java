package com.example.agent.service;

import com.example.agent.config.FilterConfig;
import com.example.agent.model.DataItem;
import com.example.agent.model.Decision;
import com.example.agent.model.FilterResult;
import com.example.agent.model.FilterRule;
import com.example.agent.model.ItemAttributes;
import com.example.agent.model.ItemLayer;
import com.example.agent.store.DataRepository;
import com.example.agent.store.FilterCache;
import com.example.agent.store.FilterStore;
import com.example.agent.util.PromptRenderer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 第三阶段：规则筛选 Agent，并编排完整三阶段管线。
 * <p>管线：数据源 → 分层（{@link LayeringService}）→ 属性（{@link AttributeExtractor}）
 * → Agent 规则筛选（本类 decide）。
 * <p>通过 {@link FilterCache} 缓存同一数据项的三阶段结果，重复筛选直接命中缓存，避免重复调用 LLM。
 */
@Service
public class FilterAgentService {

    private final FilterConfig filterConfig;
    private final LayeringService layeringService;
    private final AttributeExtractor attributeExtractor;
    private final ChatClient filterChatClient;
    private final DataRepository dataRepository;
    private final FilterStore filterStore;
    private final FilterCache filterCache;

    public FilterAgentService(
            FilterConfig filterConfig,
            LayeringService layeringService,
            AttributeExtractor attributeExtractor,
            @Qualifier("filterChatClient") ChatClient filterChatClient,
            DataRepository dataRepository,
            FilterStore filterStore,
            FilterCache filterCache) {
        this.filterConfig = filterConfig;
        this.layeringService = layeringService;
        this.attributeExtractor = attributeExtractor;
        this.filterChatClient = filterChatClient;
        this.dataRepository = dataRepository;
        this.filterStore = filterStore;
        this.filterCache = filterCache;
    }

    /**
     * 对仓库中所有数据执行完整三阶段筛选。
     *
     * @param taskId 任务 ID，用于结果标识
     * @return 完整筛选结果
     */
    public FilterResult filterAll(String taskId) {
        long startNs = System.nanoTime();
        filterStore.clear();

        FilterResult result = new FilterResult();
        result.setTaskId(taskId);

        List<DataItem> items = dataRepository.findAll();
        for (DataItem item : items) {
            String itemId = item.getId();
            String contentHash = sha256(item.getContent());

            // 命中缓存：直接复用三阶段结果
            FilterCache.Entry cached = filterCache.get(itemId, contentHash);
            if (cached != null) {
                result.getLayers().add(cached.layer);
                result.getAttributes().add(cached.attributes);
                result.getDecisions().add(cached.decision);
                result.setCached(result.getCached() + 1);
                continue;
            }

            // 阶段1：分层
            ItemLayer layer = layeringService.layerItem(itemId);
            // 阶段2：属性抽取
            ItemAttributes attributes = attributeExtractor.extractAttributes(itemId);
            // 阶段3：Agent 规则筛选决策
            Decision decision = decide(itemId);

            filterCache.put(itemId, contentHash, layer, attributes, decision);
            result.getLayers().add(layer);
            result.getAttributes().add(attributes);
            result.getDecisions().add(decision);
        }

        result.setTotal(items.size());
        result.setPassed(result.getDecisions().stream()
                .filter(d -> "OK".equals(d.getStatus()) && d.isPassed()).count());
        result.setRejected(result.getDecisions().stream()
                .filter(d -> "OK".equals(d.getStatus()) && !d.isPassed()).count());
        result.setFailed(result.getDecisions().stream()
                .filter(d -> "FAILED".equals(d.getStatus())).count());
        result.setCostMs((System.nanoTime() - startNs) / 1_000_000);
        return result;
    }

    /**
     * 对单个数据项执行完整三阶段筛选（分层 → 属性 → 规则筛选决策）。
     *
     * @param itemId 数据项 ID
     * @return 筛选决策
     */
    public Decision filterOne(String itemId) {
        Optional<DataItem> item = dataRepository.findById(itemId);
        String contentHash = item.map(i -> sha256(i.getContent())).orElse("");

        FilterCache.Entry cached = filterCache.get(itemId, contentHash);
        if (cached != null) {
            return cached.decision;
        }

        ItemLayer layer = layeringService.layerItem(itemId);
        ItemAttributes attributes = attributeExtractor.extractAttributes(itemId);
        Decision decision = decide(itemId);
        filterCache.put(itemId, contentHash, layer, attributes, decision);
        return decision;
    }

    /**
     * 第三阶段核心：LLM 依据规则对数据项作出通过/拒绝决策。
     */
    private Decision decide(String itemId) {
        String rulesText = renderRules();
        String systemPrompt = PromptRenderer.render(
                PromptRenderer.load("prompts/filter-system.st"),
                Map.of("rules", rulesText, "itemId", itemId)
        );
        // LLM 会自动调用工具：getLayerOf/getAttributesOf/getItem 读取分层与属性 → submitDecision 提交决策
        String response = filterChatClient.prompt()
                .system(systemPrompt)
                .user("请审查数据项 " + itemId + "，提交最终决策")
                .call()
                .content();
        Decision decision = filterStore.getDecision(itemId);
        if (decision == null) {
            return Decision.failed(itemId, "LLM 未提交决策结果。最后响应内容：" + response);
        }
        return decision;
    }

    private String renderRules() {
        StringBuilder sb = new StringBuilder();
        for (FilterRule rule : filterConfig.getRules()) {
            sb.append("优先级 ").append(rule.getPriority())
                    .append("，ID=").append(rule.getId())
                    .append("，动作=").append(rule.getAction())
                    .append("：").append(rule.getDescription()).append("\n");
        }
        return sb.toString();
    }

    /** 计算数据内容 SHA-256 摘要，用于缓存失效判断 */
    private static String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }
}
