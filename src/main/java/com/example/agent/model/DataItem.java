package com.example.agent.model;

/**
 * 数据项：待筛选的一条原始数据。
 */
public class DataItem {

    private String id;
    private String content;

    public DataItem() {
    }

    public DataItem(String id, String content) {
        this.id = id;
        this.content = content;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return "DataItem{" + "id='" + id + '\'' + ", content='" + content + '\'' + '}';
    }
}
