package com.example.agent.cleaning;

import java.util.List;
import java.util.Map;

/**
 * 确定性清洗规则：对一行扁平字段做原地清洗，返回变更记录。
 * <p>规则按 {@link #order()} 升序执行；派生新信息通过新增键实现，不破坏原始字段。
 */
public interface CleaningRule {

    String name();

    int order();

    List<CleaningChange> apply(Map<String, String> row);
}