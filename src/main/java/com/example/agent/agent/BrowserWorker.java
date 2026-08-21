package com.example.agent.agent;

import com.example.agent.memory.Memory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 浏览器 Worker：通过内嵌 Playwright 工具真实操作浏览器。
 * <p>负责打开网页、抓取页面真实数据、点击/填写/提交表单（例如在 GitHub 网页抓数据、创建仓库）。
 * <p>作为 {@link WorkerAgent} 注册后，会被 {@link HierarchicalAgent} 自动发现并按 name="browser" 分派。
 */
@Component
public class BrowserWorker implements WorkerAgent {

    private static final String SYSTEM_PROMPT = """
            你是一个浏览器自动化助手，通过工具真实地操作一个浏览器页面。
            你的任务是：打开网页、抓取页面上的真实数据、点击/填写/提交表单，完成用户目标。
            规则：
            1. 先用 navigate 打开页面，再用 pageText / getText / listLinks 观察页面内容。
            2. 需要提交表单或创建内容时，先用 inspectForm 查看字段，再 fill 填写、click 提交。
            3. 动作要一步步进行，先读页面状态再操作，不要凭空假设。
            4. 操作可能因重试而重复执行，请先检查当前状态，避免重复提交（幂等）。
            5. 最终用简洁中文总结：实际做了哪些操作、抓取到的关键数据、最终结果与是否成功。
            """;

    private final ReflectionLoop reflectionLoop;
    private final ChatClient browserClient;
    private final Memory memory;

    public BrowserWorker(ReflectionLoop reflectionLoop,
                         @Qualifier("browserChatClient") ChatClient browserClient,
                         Memory memory) {
        this.reflectionLoop = reflectionLoop;
        this.browserClient = browserClient;
        this.memory = memory;
    }

    @Override
    public String name() {
        return "browser";
    }

    @Override
    public String description() {
        return "浏览器自动化助手：可打开网页、抓取页面真实数据、操作页面（点击/填写/提交），例如在 GitHub 网页抓取数据或创建仓库";
    }

    @Override
    public AgentResult run(String goal) {
        List<String> recalled = memory.recall(goal, 3);
        String enriched = goal;
        if (!recalled.isEmpty()) {
            enriched = goal + "\n\n（已积累的相关知识，供参考）\n" + String.join("\n", recalled);
        }
        return reflectionLoop.execute(enriched, browserClient, SYSTEM_PROMPT);
    }
}