package com.example.agent.capability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 能力声明：标注在 {@link CapabilityAgent} 实现类上，供 {@link AgentRegistry} 静态扫描。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Capability {

    /** 能力唯一标签（分派 key，小写匹配） */
    String label();

    /** 能力描述，供 Manager 拆解/路由时选择 */
    String description();
}