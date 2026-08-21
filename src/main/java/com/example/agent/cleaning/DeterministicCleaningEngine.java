package com.example.agent.cleaning;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 确定性清洗引擎：按固定顺序执行已注册的确定性清洗规则（规则兜底，不依赖 LLM），
 * 返回清洗后字段与逐字段变更记录，供落库与前端展示「清洗前后对比」。
 */
@Service
public class DeterministicCleaningEngine {

    private final List<CleaningRule> rules;

    public DeterministicCleaningEngine(List<CleaningRule> rules) {
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(CleaningRule::order))
                .toList();
    }

    /** 对一行原始字段做确定性清洗，返回清洗后字段与变更记录。 */
    public CleaningReport clean(Map<String, String> raw) {
        Map<String, String> working = new LinkedHashMap<>(raw);
        List<CleaningChange> changes = new ArrayList<>();
        for (CleaningRule rule : rules) {
            changes.addAll(rule.apply(working));
        }
        return new CleaningReport(working, changes);
    }

    public List<String> ruleNames() {
        return rules.stream().map(CleaningRule::name).toList();
    }
}