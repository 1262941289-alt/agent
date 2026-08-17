package com.example.agent.model;

/**
 * 属性定义：业务方指定需要从数据中抽取的属性。
 */
public class AttributeDef {

    private String key;
    private String description;

    public AttributeDef() {
    }

    public AttributeDef(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
