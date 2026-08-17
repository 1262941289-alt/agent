package com.example.agent.store;

import com.example.agent.model.DataItem;

import java.util.List;
import java.util.Optional;

/**
 * 数据源抽象：业务方实现此接口接入真实数据（数据库、文件、接口等）。
 */
public interface DataRepository {

    List<DataItem> findAll();

    Optional<DataItem> findById(String id);
}
