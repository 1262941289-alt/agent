package com.example.agent.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归主循环每轮的决策结果：继续（含下一批步骤）/ 完成 / 中止。
 */
public class IterationDecision {

    /** CONTINUE / DONE / ABORT */
    private String decision = "CONTINUE";
    private String reason = "";
    /** decision 为 CONTINUE 时下一批需执行的步骤（局部序号，由 Manager 重编为全局序号） */
    private List<AgentStep> steps = new ArrayList<>();

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<AgentStep> getSteps() {
        return steps;
    }

    public void setSteps(List<AgentStep> steps) {
        this.steps = steps;
    }
}
