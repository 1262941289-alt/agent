package com.example.agent.config;

import com.example.agent.model.AttributeDef;
import com.example.agent.model.DataLayer;
import com.example.agent.model.FilterRule;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 筛选框架配置加载：分层定义、属性定义、筛选规则均来自 application.yml。
 * 业务变更时只需改配置，无需改代码。
 */
@Configuration
@ConfigurationProperties(prefix = "sk-agent.filter")
public class FilterConfig {

    private List<DataLayer> layers = new ArrayList<>();
    private List<AttributeDef> attributes = new ArrayList<>();
    private List<FilterRule> rules = new ArrayList<>();

    public List<DataLayer> getLayers() {
        return layers;
    }

    public void setLayers(List<DataLayer> layers) {
        this.layers = layers;
    }

    public List<AttributeDef> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<AttributeDef> attributes) {
        this.attributes = attributes;
    }

    public List<FilterRule> getRules() {
        return rules;
    }

    public void setRules(List<FilterRule> rules) {
        // 按优先级从小到大排序
        rules.sort((r1, r2) -> Integer.compare(r1.getPriority(), r2.getPriority()));
        this.rules = rules;
    }
}
