package com.example.agent.service;

import com.example.agent.config.FilterConfig;
import com.example.agent.model.Decision;
import com.example.agent.model.FilterResult;
import com.example.agent.model.FilterRule;
import com.example.agent.model.ItemAttributes;
import com.example.agent.model.ItemLayer;
import com.example.agent.store.FilterStore;
import com.example.agent.store.TaskContext;
import com.example.agent.util.PromptRenderer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 第三阶段：规则筛选 Agent，并编排完整三阶段管线。
 * <p>管线：数据项 → 分层（{@link LayeringService}）→ 属性（{@link AttributeExtractor}）
 * → Agent 规则筛选（本类 decide）。
 * <p>每次筛选以 taskId 隔离，分层/属性/决策结果按 taskId 写入结果表。
 */
@Service
public class FilterAgentService {

    private final FilterConfig filterConfig;
    private final LayeringService layeringService;
    private final AttributeExtractor attributeExtractor;
    private final ChatClient filterChatClient;
    private final FilterStore filterStore;
    private final TaskContext taskContext;

    public FilterAgentService(
            FilterConfig filterConfig,
            LayeringService layeringService,
            AttributeExtractor attributeExtractor,
            @Qualifier("filterChatClient") ChatClient filterChatClient,
            FilterStore filterStore,
            TaskContext taskContext) {
        this.filterConfig = filterConfig;
        this.layeringService = layeringService;
        this.attributeExtractor = attributeExtractor;
        this.filterChatClient = filterChatClient;
        this.filterStore = filterStore;
        this.taskContext = taskContext;
    }

    /**
     * 对指定数据项执行完整三阶段筛选，结果按 taskId 写入结果表。
     *
     * @param taskId  任务 ID
     * @param itemIds 待筛选数据项 ID 列表
     * @return 完整筛选结果（含分层 / 属性 / 决策明细与统计）
     */
    public FilterResult process(String taskId, List<String> itemIds) {
        long startNs = System.nanoTime();
        FilterResult result = new FilterResult();
        result.setTaskId(taskId);

        taskContext.set(taskId);
        try {
            for (String itemId : itemIds) {
                ItemLayer layer = layeringService.layerItem(itemId);
                ItemAttributes attributes = attributeExtractor.extractAttributes(itemId);
                Decision decision = decide(itemId);
                result.getLayers().add(layer);
                result.getAttributes().add(attributes);
                result.getDecisions().add(decision);
            }
        } finally {
            taskContext.clear();
        }

        result.setTotal(itemIds.size());
        result.setPassed(result.getDecisions().stream()
                .filter(d -> "OK".equals(d.getStatus()) && d.isPassed())
                .count());
        result.setRejected(result.getDecisions().stream()
                .filter(d -> "OK".equals(d.getStatus()) && !d.isPassed())
                .count());
        result.setFailed(result.getDecisions().stream()
                .filter(d -> "FAILED".equals(d.getStatus()))
                .count());
        result.setCostMs((System.nanoTime() - startNs) / 1_000_000);
        return result;
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
}