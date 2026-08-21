package com.example.agent.agent;

import com.example.agent.capability.Capability;
import com.example.agent.capability.CapabilityAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 数据能力 agent：对数据项做分层（L1-L3）、属性抽取与筛选决策（通过/拒绝）。
 * <p>通过 dataChatClient 绑定数据查询/分层/属性/决策四类工具，自主完成数据处理目标。
 * <p>记忆读权已收归 Manager，本 agent 只执行，不直接 recall。
 */
@Component
@Capability(label = "data", description = "数据处理助手：查询数据项、划分风险层(L1-L3)、抽取属性、提交筛选决策(通过/拒绝)")
public class DataWorker implements CapabilityAgent {

    private static final String SYSTEM_PROMPT = """
            你是一个数据处理助手，通过工具对“数据项”做分层、属性抽取与筛选决策。
            规则：
            1. 先调用 listItems 枚举数据项，再用 getItem 深查具体条目（getLayerOf / getAttributesOf 查看已有分层/属性）。
            2. 分层结论用 layer 工具提交（层编码必须用 L1/L2/L3），属性用 attribute 工具提交，决策用 decision 工具提交（pass=true 通过 / false 拒绝）。
            3. 只依据给定数据与规则判断，不要凭空假设。
            4. 最终用简洁中文总结：处理了哪些数据项、分层/属性/决策结论与依据。
            """;

    private final ReflectionLoop reflectionLoop;
    private final ChatClient dataClient;

    public DataWorker(ReflectionLoop reflectionLoop,
                      @Qualifier("dataChatClient") ChatClient dataClient) {
        this.reflectionLoop = reflectionLoop;
        this.dataClient = dataClient;
    }

    @Override
    public AgentResult run(String goal) {
        return reflectionLoop.execute(goal, dataClient, SYSTEM_PROMPT);
    }
}