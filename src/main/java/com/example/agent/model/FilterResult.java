package com.example.agent.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次完整筛选任务的输出结果。
 */
public class FilterResult {

    private String taskId;
    private long total;
    private long passed;
    private long rejected;
    private long failed;
    private long cached;
    private long costMs;
    private List<ItemLayer> layers = new ArrayList<>();
    private List<ItemAttributes> attributes = new ArrayList<>();
    private List<Decision> decisions = new ArrayList<>();

    public FilterResult() {
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPassed() {
        return passed;
    }

    public void setPassed(long passed) {
        this.passed = passed;
    }

    public long getRejected() {
        return rejected;
    }

    public void setRejected(long rejected) {
        this.rejected = rejected;
    }

    public long getFailed() {
        return failed;
    }

    public void setFailed(long failed) {
        this.failed = failed;
    }

    public long getCached() {
        return cached;
    }

    public void setCached(long cached) {
        this.cached = cached;
    }

    public long getCostMs() {
        return costMs;
    }

    public void setCostMs(long costMs) {
        this.costMs = costMs;
    }

    public List<ItemLayer> getLayers() {
        return layers;
    }

    public void setLayers(List<ItemLayer> layers) {
        this.layers = layers;
    }

    public List<ItemAttributes> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<ItemAttributes> attributes) {
        this.attributes = attributes;
    }

    public List<Decision> getDecisions() {
        return decisions;
    }

    public void setDecisions(List<Decision> decisions) {
        this.decisions = decisions;
    }
}
