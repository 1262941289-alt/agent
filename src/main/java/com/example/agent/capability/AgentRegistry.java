package com.example.agent.capability;

import com.example.agent.agent.AgentResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力注册中心：启动时扫描所有 {@link CapabilityAgent} Bean，按 {@link Capability}
 * 标签建索引，提供能力清单与按标签解析；预留动态挂载 hook（阶段二/三热插拔）。
 */
@Component
public class AgentRegistry {

    private final Map<String, CapabilityAgent> byLabel = new LinkedHashMap<>();
    private final List<CapabilityMeta> metas = new ArrayList<>();

    public AgentRegistry(List<CapabilityAgent> agents) {
        for (CapabilityAgent agent : agents) {
            Capability cap = agent.getClass().getAnnotation(Capability.class);
            if (cap == null) {
                continue;
            }
            register(cap.label(), cap.description(), agent);
        }
    }

    /** 能力清单（label + description + executor），供 Manager 规划时选择。 */
    public List<CapabilityMeta> metas() {
        return metas;
    }

    /** 按能力标签解析执行器；未知标签回退 general，再回退通用失败执行器。 */
    public CapabilityAgent resolve(String label) {
        if (label != null) {
            CapabilityAgent agent = byLabel.get(label.toLowerCase());
            if (agent != null) {
                return agent;
            }
        }
        CapabilityAgent general = byLabel.get("general");
        return general != null ? general : goal -> AgentResult.fail("无可用能力执行器");
    }

    /** 动态挂载能力（预留：阶段二/三热插拔）。 */
    public void dynamicMount(String label, String description, CapabilityAgent agent) {
        register(label, description, agent);
    }

    private void register(String label, String description, CapabilityAgent agent) {
        String key = label.toLowerCase();
        byLabel.put(key, agent);
        metas.add(new CapabilityMeta(key, description, agent));
    }
}