package com.example.agent.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * HLA 拆解出的单个子任务步骤。
 */
public class AgentStep {

    private int step;
    private String goal;
    /** 执行该步骤的 Worker 名称；null 表示由编排器自行分派 */
    private String worker;
    /** 前置步骤序号（本步骤依赖哪些已完成步骤）；空表示无依赖，可与同波次步骤并行 */
    private List<Integer> dependsOn = new ArrayList<>();
    /** PENDING / RUNNING / SUCCESS / FAILED / SKIPPED */
    private String status = "PENDING";
    /** 执行结果摘要 */
    private String result;

    public AgentStep() {
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getWorker() {
        return worker;
    }

    public void setWorker(String worker) {
        this.worker = worker;
    }

    public List<Integer> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<Integer> dependsOn) {
        this.dependsOn = dependsOn;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}