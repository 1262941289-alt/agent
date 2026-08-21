package com.example.agent.cleaning;

import java.util.List;
import java.util.Map;

/**
 * 一条数据的清洗结果：清洗后字段映射 + 逐字段变更记录（供前后对比展示）。
 */
public record CleaningReport(Map<String, String> cleaned, List<CleaningChange> changes) {
}