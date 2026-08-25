package com.example.agent.service;

import com.example.agent.agent.AgentEvent;
import com.example.agent.agent.AgentRunResult;
import com.example.agent.agent.AgentStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 导出服务：将 Agent 执行结果转为可下载文件内容。
 * <p>支持格式：TXT / Markdown / JSON / CSV
 * <p>上游：ManagerAgent 执行结果 + 事件流
 * <p>下游：ExportController 提供下载，前端 run.html 一键导出
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    /**
     * 导出执行结果为指定格式。
     *
     * @param format  txt / md / json / csv
     * @param goal    任务目标
     * @param result  执行结果
     * @param events 事件流
     * @return 文件内容字符串
     */
    public String export(String format, String goal, AgentRunResult result, List<AgentEvent> events) {
        return switch (format.toLowerCase()) {
            case "json" -> exportJson(goal, result, events);
            case "csv" -> exportCsv(result, events);
            case "md" -> exportMarkdown(goal, result, events);
            default -> exportText(goal, result, events);
        };
    }

    /** 获取文件扩展名 */
    public String extension(String format) {
        return switch (format.toLowerCase()) {
            case "json" -> "json";
            case "csv" -> "csv";
            case "md" -> "md";
            default -> "txt";
        };
    }

    /** 获取 Content-Type */
    public String contentType(String format) {
        return switch (format.toLowerCase()) {
            case "json" -> "application/json;charset=UTF-8";
            case "csv" -> "text/csv;charset=UTF-8";
            case "md" -> "text/markdown;charset=UTF-8";
            default -> "text/plain;charset=UTF-8";
        };
    }

    private String exportText(String goal, AgentRunResult result, List<AgentEvent> events) {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("Agent 执行报告\n");
        sb.append("========================================\n\n");
        sb.append("目标: ").append(goal).append("\n");
        sb.append("终止状态: ").append(result.getTermination()).append("\n");
        sb.append("轮数: ").append(result.getIterations()).append("\n");
        sb.append("步骤总数: ").append(result.getSteps() == null ? 0 : result.getSteps().size()).append("\n\n");

        sb.append("--- 最终答案 ---\n");
        sb.append(result.getFinalAnswer()).append("\n\n");

        if (result.getSteps() != null && !result.getSteps().isEmpty()) {
            sb.append("--- 步骤明细 ---\n");
            for (AgentStep step : result.getSteps()) {
                sb.append(String.format("[步骤%d] %s | 状态:%s | 分配给:%s\n",
                        step.getStep(), step.getGoal(), step.getStatus(), step.getWorker()));
                if (step.getResult() != null) {
                    sb.append("  结果: ").append(truncate(step.getResult(), 500)).append("\n");
                }
                sb.append("\n");
            }
        }

        if (events != null && !events.isEmpty()) {
            sb.append("--- 事件流 ---\n");
            for (AgentEvent e : events) {
                sb.append(String.format("[%s] %s\n", e.getType(), e.getPayload()));
            }
        }

        return sb.toString();
    }

    private String exportMarkdown(String goal, AgentRunResult result, List<AgentEvent> events) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Agent 执行报告\n\n");
        sb.append("## 基本信息\n\n");
        sb.append("| 字段 | 值 |\n|------|----|\n");
        sb.append("| 目标 | ").append(goal).append(" |\n");
        sb.append("| 终止状态 | ").append(result.getTermination()).append(" |\n");
        sb.append("| 轮数 | ").append(result.getIterations()).append(" |\n");
        sb.append("| 步骤数 | ").append(result.getSteps() == null ? 0 : result.getSteps().size()).append(" |\n\n");

        sb.append("## 最终答案\n\n");
        sb.append(result.getFinalAnswer()).append("\n\n");

        if (result.getSteps() != null && !result.getSteps().isEmpty()) {
            sb.append("## 步骤明细\n\n");
            sb.append("| 步骤 | 目标 | 状态 | 执行者 | 反思次数 | 结果摘要 |\n");
            sb.append("|------|------|------|--------|---------|---------|\n");
            for (AgentStep step : result.getSteps()) {
                sb.append("| ").append(step.getStep())
                        .append(" | ").append(escapeMd(step.getGoal()))
                        .append(" | ").append(step.getStatus())
                        .append(" | ").append(step.getWorker())
                        .append(" | ").append(step.getReflections())
                        .append(" | ").append(escapeMd(truncate(step.getResult(), 100)))
                        .append(" |\n");
            }
            sb.append("\n");
        }

        if (events != null && !events.isEmpty()) {
            sb.append("## 事件流\n\n");
            for (AgentEvent e : events) {
                sb.append("- **").append(e.getType()).append("**: ").append(e.getPayload()).append("\n");
            }
        }

        return sb.toString();
    }

    private String exportJson(String goal, AgentRunResult result, List<AgentEvent> events) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"goal\": ").append(jsonStr(goal)).append(",\n");
        sb.append("  \"termination\": ").append(jsonStr(result.getTermination())).append(",\n");
        sb.append("  \"iterations\": ").append(result.getIterations()).append(",\n");
        sb.append("  \"finalAnswer\": ").append(jsonStr(result.getFinalAnswer())).append(",\n");

        sb.append("  \"steps\": [");
        if (result.getSteps() != null) {
            for (int i = 0; i < result.getSteps().size(); i++) {
                AgentStep s = result.getSteps().get(i);
                if (i > 0) sb.append(",");
                sb.append("\n    {")
                        .append("\"step\": ").append(s.getStep()).append(", ")
                        .append("\"goal\": ").append(jsonStr(s.getGoal())).append(", ")
                        .append("\"status\": ").append(jsonStr(s.getStatus())).append(", ")
                        .append("\"worker\": ").append(jsonStr(s.getWorker())).append(", ")
                        .append("\"reflections\": ").append(s.getReflections()).append(", ")
                        .append("\"result\": ").append(jsonStr(s.getResult()))
                        .append("}");
            }
        }
        sb.append("\n  ],\n");

        sb.append("  \"events\": [");
        if (events != null) {
            for (int i = 0; i < events.size(); i++) {
                AgentEvent e = events.get(i);
                if (i > 0) sb.append(",");
                sb.append("\n    {\"type\": ").append(jsonStr(e.getType()))
                        .append(", \"payload\": ").append(jsonStr(e.getPayload().toString())).append("}");
            }
        }
        sb.append("\n  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String exportCsv(AgentRunResult result, List<AgentEvent> events) {
        StringBuilder sb = new StringBuilder();
        sb.append("步骤,目标,状态,执行者,反思次数,结果摘要\n");
        if (result.getSteps() != null) {
            for (AgentStep s : result.getSteps()) {
                sb.append(s.getStep()).append(",")
                        .append(csvEscape(s.getGoal())).append(",")
                        .append(s.getStatus()).append(",")
                        .append(s.getWorker()).append(",")
                        .append(s.getReflections()).append(",")
                        .append(csvEscape(truncate(s.getResult(), 200))).append("\n");
            }
        }
        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private String escapeMd(String s) {
        if (s == null) return "";
        return s.replace("|", "\\|").replace("\n", " ");
    }

    private String csvEscape(String s) {
        if (s == null) return "";
        String v = s.replace("\"", "\"\"");
        return "\"" + v.replace("\n", " ") + "\"";
    }

    private String jsonStr(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
