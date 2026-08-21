package com.example.agent.agent;

/**
 * 单个 Worker 执行一次子任务后的结果。
 */
public class AgentResult {

    private boolean success;
    private String output;
    /** 触发反思重试的次数 */
    private int reflections;

    public AgentResult() {
    }

    public static AgentResult ok(String output, int reflections) {
        AgentResult r = new AgentResult();
        r.success = true;
        r.output = output;
        r.reflections = reflections;
        return r;
    }

    public static AgentResult fail(String output) {
        AgentResult r = new AgentResult();
        r.success = false;
        r.output = output;
        return r;
    }

    public static AgentResult fail(String output, int reflections) {
        AgentResult r = fail(output);
        r.reflections = reflections;
        return r;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public int getReflections() {
        return reflections;
    }

    public void setReflections(int reflections) {
        this.reflections = reflections;
    }
}