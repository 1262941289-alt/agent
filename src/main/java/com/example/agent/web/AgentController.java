package com.example.agent.web;

import com.example.agent.agent.AgentRunResult;
import com.example.agent.agent.ManagerAgent;
import com.example.agent.memory.ShortTermMemory;
import com.example.agent.tools.AskUserTools;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通用办公 Agent 入口：提交自然语言目标，Manager 编排执行并返回步骤与最终答复。
 * <p>通过可选的 {@code conversationId} 维持多轮会话上下文（短期记忆）。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ManagerAgent managerAgent;
    private final ShortTermMemory shortTermMemory;
    private final AskUserTools askUserTools;

    public AgentController(ManagerAgent managerAgent, ShortTermMemory shortTermMemory, AskUserTools askUserTools) {
        this.managerAgent = managerAgent;
        this.shortTermMemory = shortTermMemory;
        this.askUserTools = askUserTools;
    }

    /**
     * 执行目标。
     * POST /api/agent/run
     */
    @PostMapping("/run")
    public AgentRunResult run(@RequestBody RunRequest request) {
        String history = shortTermMemory.recent(request.conversationId(), 6);
        AgentRunResult result = managerAgent.execute(request.goal(), history);
        shortTermMemory.add(request.conversationId(), "用户: " + request.goal());
        shortTermMemory.add(request.conversationId(), "助手: " + result.getFinalAnswer());
        return result;
    }

    /**
     * 清空会话上下文（短期记忆）。
     * POST /api/agent/context/clear?conversationId=...（缺省则清空全部会话）
     */
    @PostMapping("/context/clear")
    public Map<String, Object> clearContext(@RequestParam(required = false) String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            int cleared = shortTermMemory.clearAll();
            return Map.of("clearedConversations", cleared, "all", true);
        }
        int cleared = shortTermMemory.clear(conversationId);
        return Map.of("clearedEntries", cleared, "conversationId", conversationId, "all", false);
    }

    /** 请求体；conversationId 为可选的会话标识，缺省则为单轮无上下文 */
    public record RunRequest(String goal, String conversationId) {
    }

    /**
     * 回复 agent 的 askUser 提问。
     * POST /api/agent/ask-user/answer?questionId=...&answer=...
     */
    @PostMapping("/ask-user/answer")
    public Map<String, Object> answerAskUser(@RequestParam String questionId, @RequestParam String answer) {
        boolean ok = askUserTools.answer(questionId, answer);
        return Map.of("accepted", ok, "questionId", questionId);
    }
}