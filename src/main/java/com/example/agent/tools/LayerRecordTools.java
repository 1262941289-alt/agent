package com.example.agent.tools;

import com.example.agent.store.FilterStore;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 分层阶段工具：模型在读取数据后，通过 recordLayer 提交分层结论。
 */
@Component
public class LayerRecordTools {

    private final FilterStore store;

    public LayerRecordTools(FilterStore store) {
        this.store = store;
    }

    @Tool(description = "将数据项划分到指定层并提交结论。层编码必须使用给定的层编码（如 L1/L2/L3），不得自造")
    public void recordLayer(
            @ToolParam(description = "数据项 ID") String itemId,
            @ToolParam(description = "层编码，如 L1/L2/L3") String layerCode,
            @ToolParam(description = "划分依据/原因，一句话说明") String reason) {
        store.setLayer(itemId, layerCode, reason);
    }
}
