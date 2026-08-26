package com.example.agent.tools;

import com.example.agent.service.ToolExecutionService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具守护包装器：把原生 {@link ToolCallback} 的调用委托给 {@link ToolExecutionService}
 * （统一走 审计 → 审批门 → 超时 → 结果上报），schema/元数据原样透传。
 * <p>在 {@code AgentConfig} 里用 {@code ToolCallbacks.from(工具POJO)} 后逐个包上此回调再绑入 ChatClient。
 * <p>另内置「防死循环护栏」：单轮工具调用预算 + 相同参数重复调用检测，防止模型陷入无限工具调用。
 */
public class GuardedToolCallback implements ToolCallback {

    /** 单轮（一次 worker .call()）允许的最大工具调用次数。 */
    private static final int MAX_TOTAL_CALLS = 18;
    /** 相同参数连续重复调用的分级阈值：3=温和提醒，5=详细警告，8=强制停止。 */
    private static final int[] REPEAT_THRESHOLDS = {3, 5, 8};
    /** 每线程单轮工具调用预算（由 ReflectionLoop 在每个 worker 回合开始时重置）。 */
    private static final ThreadLocal<Integer> TURN_BUDGET = ThreadLocal.withInitial(() -> 0);

    /** 上一轮调用的工具名+参数 key，用于检测连续重复（换工具/换参数时自动清零）。 */
    private static final ThreadLocal<String> LAST_KEY = new ThreadLocal<>();

    /**
     * 工具名+参数字符串 -> 连续重复次数（回合内累计计数）。
     * <p>注意：采用「回合内累计」而非「时间窗内连续」，避免网络超时等慢速调用（间隔 &gt;800ms）
     * 绕过防死循环护栏。每轮开始由 resetTurn() 清空，避免跨回合累计误伤。
     */
    private static final ConcurrentHashMap<String, int[]> REPEAT_STATE = new ConcurrentHashMap<>();

    private final ToolCallback delegate;
    private final ToolExecutionService toolExecutionService;

    public GuardedToolCallback(ToolCallback delegate, ToolExecutionService toolExecutionService) {
        this.delegate = delegate;
        this.toolExecutionService = toolExecutionService;
    }

    /** 开始一个新的 worker 回合时调用，重置本轮工具调用预算与重复检测链。 */
    public static void resetTurn() {
        TURN_BUDGET.set(0);
        LAST_KEY.remove();
        REPEAT_STATE.clear();
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
        return guardedCall(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return guardedCall(toolInput, toolContext);
    }

    private String guardedCall(String toolInput, ToolContext toolContext) {
        String name = delegate.getToolDefinition().name();

        int used = TURN_BUDGET.get() + 1;
        TURN_BUDGET.set(used);
        if (used > MAX_TOTAL_CALLS) {
            return "【工具调用保护】本轮已调用工具 " + used + " 次，超过上限 " + MAX_TOTAL_CALLS
                    + "。请立即停止调用任何工具，直接基于已经获得的信息输出结论/最终答复。";
        }

        String key = name + "|" + (toolInput == null ? "" : toolInput);
        // 换工具或换参数时，清除连续重复计数（像 deepseek-harness 的 repeat-tool-reminder 一样按链计数）
        String last = LAST_KEY.get();
        if (last != null && !last.equals(key)) {
            REPEAT_STATE.remove(last);
        }
        LAST_KEY.set(key);

        int repeats = registerAndCount(key);
        String reminder = checkRepeatReminder(name, repeats, toolInput);
        if (reminder != null) {
            return reminder;
        }

        return toolContext == null
                ? toolExecutionService.execute(delegate, toolInput, null)
                : toolExecutionService.execute(delegate, toolInput, toolContext);
    }

    /** 多级提醒：3=温和，5=详细，8=强制停止（参考 deepseek-harness repeat-tool-reminder 阈值 [3,5,8]）。 */
    private static String checkRepeatReminder(String name, int repeats, String toolInput) {
        if (repeats < REPEAT_THRESHOLDS[0]) {
            return null;
        }
        String argsPreview = abbreviate(toolInput);
        if (repeats >= REPEAT_THRESHOLDS[2]) {
            return "【工具调用保护·强制停止】你已连续 " + repeats + " 次以相同参数调用工具 '" + name
                    + "'（参数: " + argsPreview + "），返回结果不会改变。请立即停止调用此工具，"
                    + "换用其他工具/选择器/方法，或直接基于已获得的信息给出最终结论。不要再重试相同参数。";
        }
        if (repeats >= REPEAT_THRESHOLDS[1]) {
            return "【工具调用保护·详细警告】你已连续 " + repeats + " 次以相同参数调用工具 '" + name
                    + "'（参数: " + argsPreview + "）。之前的调用已经返回了结果，重复调用不会改变。"
                    + "请分析之前返回的结果内容，换用其他选择器或工具，或直接总结当前已获得的信息。";
        }
        // repeats >= REPEAT_THRESHOLDS[0]（3次）
        return "【工具调用保护·提醒】你已连续 " + repeats + " 次以相同参数调用工具 '" + name
                + "'。请确认是否需要换一种方式，或总结当前结果继续。";
    }

    private static int registerAndCount(String key) {
        return REPEAT_STATE.compute(key, (k, v) -> v == null ? new int[]{1} : new int[]{v[0] + 1})[0];
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= 60 ? t : t.substring(0, 60) + "...";
    }
}