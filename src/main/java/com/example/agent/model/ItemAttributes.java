package com.example.agent.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个数据项被抽取出的属性集合。
 */
public class ItemAttributes {

    private String itemId;
    private List<Attribute> attributes = new ArrayList<>();
    private String status = "OK";

    public ItemAttributes() {
    }

    public static ItemAttributes failed(String itemId, String reason) {
        ItemAttributes a = new ItemAttributes();
        a.setItemId(itemId);
        a.setStatus("FAILED");
        a.getAttributes().add(new Attribute("错误", reason));
        return a;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public List<Attribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<Attribute> attributes) {
        this.attributes = attributes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
