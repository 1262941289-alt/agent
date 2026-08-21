package com.example.agent.cleaning;

/**
 * 单字段清洗变更记录：规则、字段、前后值，以及是否为新增派生字段。
 */
public record CleaningChange(String rule, String field, String before, String after, boolean added) {
}