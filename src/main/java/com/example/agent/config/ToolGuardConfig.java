package com.example.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工具守护策略配置：按工具名声明是否需要人工审批、单次调用超时。
 * <p>绑定 {@code sk-agent.tool-guard}；默认安全——只读工具放行，写/交互类工具需审批。
 * <p>{@link #enabled} 为审批总开关：置 false 时所有工具直接放行，不再进入人工审批门。
 */
@Component
@ConfigurationProperties(prefix = "sk-agent.tool-guard")
public class ToolGuardConfig {

    /** 记录一条策略。 */
    public static class Policy {
        private List<String> names = new ArrayList<>();
        private boolean requiresApproval;
        private int timeoutSeconds;

        public List<String> getNames() {
            return names;
        }

        public void setNames(List<String> names) {
            this.names = names == null ? new ArrayList<>() : names;
        }

        public boolean isRequiresApproval() {
            return requiresApproval;
        }

        public void setRequiresApproval(boolean requiresApproval) {
            this.requiresApproval = requiresApproval;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /** 审批总开关：false 时所有工具直接放行，不再进入人工审批门（无人工值守/追求速度场景）。 */
    private boolean enabled = true;

    /** 默认单次工具调用超时（秒），与 LLM 读超时保持一致。 */
    private int defaultTimeoutSeconds = 180;

    /** 审批等待超时（秒）；超时未裁决时按 {@code approvalTimeoutContinue} 决定放行与否。 */
    private int approvalTimeoutSeconds = 600;

    /** 审批等待超时后的默认行为：true=放行/继续，false=拒绝。 */
    private boolean approvalTimeoutContinue = true;

    /** 默认风险的工具名（未在 policies 命中时对命中以下名称的视为需审批）。 */
    private List<String> riskTools = new ArrayList<>(List.of("fill", "click", "pressKey", "screenshot"));

    private List<Policy> policies = new ArrayList<>();

    private volatile Map<String, Policy> index;

    /** 按工具名取策略：总开关关闭时一律放行；否则命中策略取策略配置，命中默认风险名单则需审批。 */
    public Policy policyFor(String toolName) {
        // 审批总开关关闭：所有工具直接放行，不进入审批门
        if (!enabled) {
            return safetyDefault();
        }
        if (toolName == null || toolName.isBlank()) {
            return safetyDefault();
        }
        Map<String, Policy> idx = index();
        Policy p = idx.get(toolName);
        if (p != null) {
            Policy copy = new Policy();
            copy.setRequiresApproval(p.isRequiresApproval());
            copy.setTimeoutSeconds(p.getTimeoutSeconds() > 0 ? p.getTimeoutSeconds() : defaultTimeoutSeconds);
            return copy;
        }
        // 命中默认风险名单则需审批
        Policy def = safetyDefault();
        if (riskTools.contains(toolName)) {
            def.setRequiresApproval(true);
        }
        return def;
    }

    private Policy safetyDefault() {
        Policy p = new Policy();
        p.setRequiresApproval(false);
        p.setTimeoutSeconds(defaultTimeoutSeconds);
        return p;
    }

    private Map<String, Policy> index() {
        Map<String, Policy> local = index;
        if (local == null) {
            synchronized (this) {
                local = index;
                if (local == null) {
                    Map<String, Policy> build = policies.stream()
                            .filter(x -> x.getNames() != null)
                            .flatMap(x -> x.getNames().stream().map(n -> Map.entry(n, x)))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
                    index = local = Collections.unmodifiableMap(build);
                }
            }
        }
        return local;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    public void setDefaultTimeoutSeconds(int defaultTimeoutSeconds) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    public int getApprovalTimeoutSeconds() {
        return approvalTimeoutSeconds;
    }

    public void setApprovalTimeoutSeconds(int approvalTimeoutSeconds) {
        this.approvalTimeoutSeconds = approvalTimeoutSeconds;
    }

    public boolean isApprovalTimeoutContinue() {
        return approvalTimeoutContinue;
    }

    public void setApprovalTimeoutContinue(boolean approvalTimeoutContinue) {
        this.approvalTimeoutContinue = approvalTimeoutContinue;
    }

    public List<String> getRiskTools() {
        return riskTools;
    }

    public void setRiskTools(List<String> riskTools) {
        this.riskTools = riskTools == null ? new ArrayList<>() : riskTools;
    }

    public List<Policy> getPolicies() {
        return policies;
    }

    public void setPolicies(List<Policy> policies) {
        this.policies = policies == null ? new ArrayList<>() : policies;
        this.index = null;
    }
}