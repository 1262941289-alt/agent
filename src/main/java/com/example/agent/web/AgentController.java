package com.example.agent.web;

import com.example.agent.agent.AgentRunResult;
import com.example.agent.agent.HierarchicalAgent;
import com.example.agent.memory.ShortTermMemory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用办公 Agent 入口：提交自然语言目标，HLA 编排执行并返回步骤与最终答复。
 * <p>通过可选的 {@code conversationId} 维持多轮会话上下文（短期记忆）。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final HierarchicalAgent hierarchicalAgent;
    private final ShortTermMemory shortTermMemory;

    public AgentController(HierarchicalAgent hierarchicalAgent, ShortTermMemory shortTermMemory) {
        this.hierarchicalAgent = hierarchicalAgent;
        this.shortTermMemory = shortTermMemory;
    }

    /**
     * 执行目标。
     * POST /api/agent/run
     */
    @PostMapping("/run")
    public AgentRunResult run(@RequestBody RunRequest request) {
        String history = shortTermMemory.recent(request.conversationId(), 6);
        AgentRunResult result = hierarchicalAgent.execute(request.goal(), history);
        shortTermMemory.add(request.conversationId(), "用户: " + request.goal());
        shortTermMemory.add(request.conversationId(), "助手: " + result.getFinalAnswer());
        return result;
    }

    /** 请求体；conversationId 为可选的会话标识，缺省则为单轮无上下文 */
    public record RunRequest(String goal, String conversationId) {
    }
}