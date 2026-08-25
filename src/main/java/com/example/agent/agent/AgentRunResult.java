package com.example.agent.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * HLA 执行一次总目标的输出：目标、拆解步骤、最终汇总答复、终止状态、轮数、事件流。
 */
public class AgentRunResult {

    private String goal;
    private List<AgentStep> steps = new ArrayList<>();
    private String finalAnswer;
    private String termination;
    private int iterations;
    private List<AgentEvent> events = new ArrayList<>();

    public AgentRunResult() {
    }

    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }

    public List<AgentStep> getSteps() { return steps; }
    public void setSteps(List<AgentStep> steps) { this.steps = steps; }

    public String getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }

    public String getTermination() { return termination; }
    public void setTermination(String termination) { this.termination = termination; }

    public int getIterations() { return iterations; }
    public void setIterations(int iterations) { this.iterations = iterations; }

    public List<AgentEvent> getEvents() { return events; }
    public void setEvents(List<AgentEvent> events) { this.events = events; }
}
