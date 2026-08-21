package com.example.agent.tools;

import com.example.agent.agent.ExperienceRetriever;
import com.example.agent.knowledge.KnowledgeGraphService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 历史/自学习工具：暴露给历史节点能力 agent（HistoryWorker）的 @Tool 方法。
 * <p>覆盖：召回历史经验与人工标注、沉淀学习结论到知识图谱。不注入 Memory（读权保留给 Manager）。
 */
@Component
public class HistoryTools {

    private final ExperienceRetriever retriever;
    private final KnowledgeGraphService graphService;

    public HistoryTools(ExperienceRetriever retriever, KnowledgeGraphService graphService) {
        this.retriever = retriever;
        this.graphService = graphService;
    }

    @Tool(description = "按目标/关键词召回 top-k 条相关历史经验与人工标注，返回可供参考的文本")
    public String recallExperience(@ToolParam(description = "查询目标或关键词") String query,
                                   @ToolParam(description = "返回条数，默认 5") Integer k) {
        int n = k == null ? 5 : k;
        String result = retriever.retrieve(query, n);
        return result == null || result.isBlank() ? "（暂无相关历史经验）" : result;
    }

    @Tool(description = "沉淀一条学习结论到知识图谱。type 用 EXPERIENCE(成功经验) 或 PITFALL(失败教训)")
    public String recordLesson(@ToolParam(description = "类型：EXPERIENCE 或 PITFALL") String type,
                               @ToolParam(description = "结论名称") String name,
                               @ToolParam(description = "结论内容") String content) {
        String t = (type == null || type.isBlank()) ? "EXPERIENCE" : type;
        graphService.upsertNode(name, t, content, Map.of());
        return "已沉淀经验节点：" + name;
    }
}