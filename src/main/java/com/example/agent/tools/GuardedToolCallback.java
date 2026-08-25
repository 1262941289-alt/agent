package com.example.agent.tools;

import com.example.agent.service.ToolExecutionService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * 工具守护包装器：把原生 {@link ToolCallback} 的调用委托给 {@link ToolExecutionService}
 * （统一走 审计 → 审批门 → 超时 → 结果上报），schema/元数据原样透传。
 * <p>在 {@code AgentConfig} 里用 {@code ToolCallbacks.from(工具POJO)} 后逐个包上此回调再绑入 ChatClient。
 */
public class GuardedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolExecutionService toolExecutionService;

    public GuardedToolCallback(ToolCallback delegate, ToolExecutionService toolExecutionService) {
        this.delegate = delegate;
        this.toolExecutionService = toolExecutionService;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return toolExecutionService.execute(delegate, toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return toolExecutionService.execute(delegate, toolInput, toolContext);
    }
}