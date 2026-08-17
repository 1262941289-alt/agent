package com.example.agent.model;

/**
 * 分层定义：业务方给出的层（L1/L2/L3...）及其划分标准。
 */
public class DataLayer {

    private String code;
    private String name;
    private String description;
    private int order;

    public DataLayer() {
    }

    public DataLayer(String code, String name, String description, int order) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.order = order;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
