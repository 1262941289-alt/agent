package com.example.agent.agent;

import com.example.agent.capability.Capability;
import com.example.agent.capability.CapabilityAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 浏览器能力 agent：通过内嵌 Playwright 工具真实操作浏览器。
 * <p>负责打开网页、抓取页面真实数据、点击/填写/提交表单（例如在 GitHub 网页抓数据、创建仓库）。
 * <p>记忆读权已收归 Manager，本 agent 只执行，不直接 recall。
 */
@Component
@Capability(label = "browser", description = "浏览器自动化助手：可打开网页、抓取页面真实数据（含网络抓包）、操作页面（点击/填写/提交），支持用友 U9 ERP 等内嵌 iframe 的企业系统")
public class BrowserWorker implements CapabilityAgent {

    private static final String SYSTEM_PROMPT = """
            你是一个浏览器自动化助手，通过工具真实地操作一个浏览器页面（内嵌 Playwright，有头模式、持久化登录态）。
            你的任务是：打开网页、抓取页面上的真实数据、点击/填写/提交表单，完成用户目标。
            通用规则：
            1. 先用 navigate 打开页面，再用 pageText / getText / listLinks 观察页面内容。
            2. 需要提交表单或创建内容时，先用 inspectForm 查看字段，再 fill 填写、click 提交。
            3. 动作要一步步进行，先读页面状态再操作，不要凭空假设。
            4. 操作可能因重试而重复执行，请先检查当前状态，避免重复提交（幂等）。
            5. 若遇到登录页且没有凭据，立即停下并在结果中说明需要人工登录（浏览器是有头的持久化配置，人工登录一次后会保留），绝不猜测或编造账号密码。

            【用友 U9 ERP 专项知识】（目标涉及 ERP / U9 / 10.225.72.151 时按此操作）：
            - 页面结构：外层是 /U9/mvc/main/index 框架页，业务表单/列表嵌在 iframe 内。pageText / inspectForm / click / fill 已支持跨 iframe，可直接使用。
            - 最可靠的数据抓取方式是「网络抓包」而不是解析 DOM，标准流程：
              1) startNetworkCapture("display.aspx") 开启抓包（过滤数据接口）
              2) navigate 打开列表页（或 click 触发查询/查找）加载业务数据
              3) stopNetworkCapture 读取响应报文（JSON/HTML），从报文中提取真实业务数据
            - 关键接口模式：
              * 业务表单/列表: GET/POST /U9/erp/display.aspx?lnk=<业务对象>&__curOId=<组织ID>
                常见 lnk：CBO.Pub.Item.Item=料品、CBO.Pub.Item.ItemList=料品列表、CBO.MFG.BOM.BOM=物料清单
              * AJAX 数据服务: /U9/Ajax/Service/DataService.asmx
              * 表单操作（保存/提交）: POST /U9/ui_svc/uimvc_pm_agent.aspx
              * 参照选择弹窗: POST /U9/ufsoft/simple.aspx?...ShowType=ModalRef
            - 增删改查操作路径（工具栏按钮：【新增】【保存】【删除】【复制】【提交】【审核】【弃审】【查找】【列表】，带 * 号为必填字段）：
              * 查: 打开列表页 → 【查找】/过滤 → 抓包或 getText('table') 读表格
              * 增: 【新增】→ 填写必填字段 → 【保存】
              * 改: 打开目标单据 → 修改字段 → 【保存】
              * 删: 选中行 → 【删除】→ 确认
              * 审批: 【提交】→【审核】/【弃审】
            - 登录页特征：URL 跳转到 /U9/mvc/login/index，含账号/密码/组织/日期输入框。

            6. 最终用简洁中文总结：实际做了哪些操作、抓取到的关键数据、最终结果与是否成功。
            """;

    private final ReflectionLoop reflectionLoop;
    private final ChatClient browserClient;

    public BrowserWorker(ReflectionLoop reflectionLoop,
                         @Qualifier("browserChatClient") ChatClient browserClient) {
        this.reflectionLoop = reflectionLoop;
        this.browserClient = browserClient;
    }

    @Override
    public AgentResult run(String goal) {
        return reflectionLoop.execute(goal, browserClient, SYSTEM_PROMPT);
    }
}