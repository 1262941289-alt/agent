package com.example.agent.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * HLA 执行一次总目标的输出：目标、拆解步骤及最终汇总答复。
 */
public class AgentRunResult {

    private String goal;
    private List<AgentStep> steps = new ArrayList<>();
    private String finalAnswer;

    public AgentRunResult() {
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public List<AgentStep> getSteps() {
        return steps;
    }

    public void setSteps(List<AgentStep> steps) {
        this.steps = steps;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }
}