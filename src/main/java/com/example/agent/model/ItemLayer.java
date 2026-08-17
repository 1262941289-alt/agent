package com.example.agent.model;

/**
 * 单个数据项的分层结果。
 */
public class ItemLayer {

    private String itemId;
    private String layerCode;
    private String layerName;
    private String reason;
    private String status = "OK";

    public ItemLayer() {
    }

    public static ItemLayer failed(String itemId, String reason) {
        ItemLayer l = new ItemLayer();
        l.setItemId(itemId);
        l.setStatus("FAILED");
        l.setReason(reason);
        return l;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getLayerCode() {
        return layerCode;
    }

    public void setLayerCode(String layerCode) {
        this.layerCode = layerCode;
    }

    public String getLayerName() {
        return layerName;
    }

    public void setLayerName(String layerName) {
        this.layerName = layerName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
