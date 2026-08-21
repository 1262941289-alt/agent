package com.example.agent.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个 Worker 执行一次子任务后的结果。
 */
public class AgentResult {

    private boolean success;
    private String output;
    /** 触发反思重试的次数 */
    private int reflections;
    /** 每轮反思的记录（未满足时的 critique/nextAction），供编排器发射 step:reflection 事件 */
    private List<Reflection> reflectionTrail = new ArrayList<>();

    public AgentResult() {
    }

    public static AgentResult ok(String output, int reflections) {
        return ok(output, reflections, new ArrayList<>());
    }

    public static AgentResult ok(String output, int reflections, List<Reflection> trail) {
        AgentResult r = new AgentResult();
        r.success = true;
        r.output = output;
        r.reflections = reflections;
        r.reflectionTrail = trail == null ? new ArrayList<>() : trail;
        return r;
    }

    public static AgentResult fail(String output) {
        return fail(output, 0, new ArrayList<>());
    }

    public static AgentResult fail(String output, int reflections) {
        return fail(output, reflections, new ArrayList<>());
    }

    public static AgentResult fail(String output, int reflections, List<Reflection> trail) {
        AgentResult r = new AgentResult();
        r.success = false;
        r.output = output;
        r.reflections = reflections;
        r.reflectionTrail = trail == null ? new ArrayList<>() : trail;
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

    public List<Reflection> getReflectionTrail() {
        return reflectionTrail;
    }

    public void setReflectionTrail(List<Reflection> reflectionTrail) {
        this.reflectionTrail = reflectionTrail;
    }
}