package com.example.agent.dto;

/**
 * 数据接入的统一输入对象（各来源 REST/DB/MQ/FILE 最终都转换为该结构）。
 */
public class DataItemInput {

    /** 数据项 ID；为空则自动生成 */
    private String id;

    /** 数据原文内容，必填 */
    private String content;

    /** 数据来源：REST / DB / MQ / FILE；为空时由接入服务按来源补默认值 */
    private String sourceType;

    public DataItemInput() {
    }

    public DataItemInput(String id, String content, String sourceType) {
        this.id = id;
        this.content = content;
        this.sourceType = sourceType;
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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }
}