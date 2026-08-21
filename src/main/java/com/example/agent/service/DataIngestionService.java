package com.example.agent.service;

import com.example.agent.dto.DataItemInput;

import java.util.List;

/**
 * 数据接入统一抽象：所有来源的数据均通过该接口进入系统并落库。
 */
public interface DataIngestionService {

    String SOURCE_REST = "REST";
    String SOURCE_DB = "DB";
    String SOURCE_MQ = "MQ";
    String SOURCE_FILE = "FILE";

    /**
     * 批量接入数据项，落库并返回入库后的数据项 ID 列表。
     *
     * @param items 待接入的数据项
     * @return 入库的 ID 列表
     */
    List<String> ingest(List<DataItemInput> items);
}