package com.example.agent.web;

import com.example.agent.agent.AgentEvent;
import com.example.agent.agent.AgentRunResult;
import com.example.agent.agent.ManagerAgent;
import com.example.agent.memory.ShortTermMemory;
import com.example.agent.service.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 导出控制器：将 Agent 执行结果导出为可下载文件。
 * <p>上游：ManagerAgent 执行结果 + 事件流
 * <p>下游：前端 run.html 一键导出按钮
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService exportService;
    private final ManagerAgent managerAgent;
    private final ShortTermMemory shortTermMemory;

    public ExportController(ExportService exportService, ManagerAgent managerAgent,
                           ShortTermMemory shortTermMemory) {
        this.exportService = exportService;
        this.managerAgent = managerAgent;
        this.shortTermMemory = shortTermMemory;
    }

    /**
     * GET /api/export?goal=...&format=txt|md|json|csv&conversationId=...
     * 执行任务并导出结果为文件。
     */
    @GetMapping
    public ResponseEntity<byte[]> export(@RequestParam String goal,
                                          @RequestParam(defaultValue = "txt") String format,
                                          @RequestParam(required = false) String conversationId) {
        String history = conversationId != null ? shortTermMemory.recent(conversationId, 6) : "";
        AgentRunResult result = managerAgent.execute(goal, history, conversationId);
        List<AgentEvent> events = result.getEvents() != null ? result.getEvents() : List.of();

        String content = exportService.export(format, goal, result, events);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        String ext = exportService.extension(format);
        String contentType = exportService.contentType(format);
        String filename = "agent-report-" + System.currentTimeMillis() + "." + ext;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes);
    }

    /**
     * POST /api/export/last?format=txt&goal=...&result=...&events=...
     * 导出已执行的结果（前端传入结果和事件，不重新执行）。
     */
    @GetMapping("/preview")
    public Map<String, Object> preview(@RequestParam String goal,
                                         @RequestParam(defaultValue = "txt") String format,
                                         @RequestParam String result,
                                         @RequestParam(required = false) String termination) {
        AgentRunResult r = new AgentRunResult();
        r.setFinalAnswer(result);
        r.setTermination(termination != null ? termination : "DONE");
        r.setIterations(1);
        String content = exportService.export(format, goal, r, List.of());
        return Map.of(
                "format", format,
                "content", content,
                "extension", exportService.extension(format)
        );
    }
}
