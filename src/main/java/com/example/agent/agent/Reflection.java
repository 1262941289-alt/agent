package com.example.agent.agent;

/**
 * 反思评估结果：判断执行器输出是否已达成目标，并给出改进意见。
 */
public class Reflection {

    private boolean satisfied = true;
    private String critique = "";
    private String nextAction = "";

    public Reflection() {
    }

    public boolean isSatisfied() {
        return satisfied;
    }

    public void setSatisfied(boolean satisfied) {
        this.satisfied = satisfied;
    }

    public String getCritique() {
        return critique;
    }

    public void setCritique(String critique) {
        this.critique = critique;
    }

    public String getNextAction() {
        return nextAction;
    }

    public void setNextAction(String nextAction) {
        this.nextAction = nextAction;
    }
}