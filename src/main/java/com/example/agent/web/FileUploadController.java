package com.example.agent.web;

import com.example.agent.service.FileContextService;
import com.example.agent.service.FileParseService;
import com.example.agent.service.ImageRecognitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文件上传 REST 接口：接收文件并提取文本内容，供 Agent 作为任务上下文使用。
 * <p>嵌套回调链路：
 * <pre>
 * 上传文件 → 识别类型
 *   → 图片：ImageRecognitionService.recognize() → 存 FileContextService → 返回 fileContextId
 *   → 文档：FileParseService.parse() → 存 FileContextService → 返回 fileContextId
 * → AgentStreamController 用 fileContextId 引用 → ManagerAgent 注入规划
 * → Worker 通过 AgentContext.getFileContext() 读取
 * </pre>
 */
@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    private final FileParseService parseService;
    private final FileContextService contextService;
    private final ImageRecognitionService imageRecognitionService;

    public FileUploadController(FileParseService parseService, FileContextService contextService,
                               ImageRecognitionService imageRecognitionService) {
        this.parseService = parseService;
        this.contextService = contextService;
        this.imageRecognitionService = imageRecognitionService;
    }

    /**
     * POST /api/files/upload (multipart/form-data)
     * 解析上传的文件并返回提取的文本内容 + fileContextId（供 SSE 端点引用）。
     * 图片文件自动触发图片识别。
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return badRequest("文件为空");
        }
        String fileName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();

        if (fileName.contains("~$")) {
            return badRequest("这是 Excel 临时锁定文件（含 ~$ 标记），不是实际数据文件。请关闭 Excel 后上传原始文件。");
        }

        try {
            byte[] bytes = file.getBytes();

            // 图片文件：走图片识别管线（嵌套回调入口）
            if (ImageRecognitionService.isImage(fileName)) {
                log.info("图片上传，触发图片识别: {}", fileName);
                String recognitionResult = imageRecognitionService.recognize(bytes, fileName);
                String contextId = contextService.store(
                        fileName, recognitionResult, "image", file.getSize());

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("fileContextId", contextId);
                resp.put("fileName", fileName);
                resp.put("extension", "image");
                resp.put("parser", "ImageRecognition");
                resp.put("fileSize", file.getSize());
                resp.put("textLength", recognitionResult.length());
                resp.put("content", recognitionResult);
                return ResponseEntity.ok(resp);
            }

            // 文档文件：走文本解析管线
            FileParseService.ParseResult result = parseService.parse(bytes, fileName);
            String contextId = contextService.store(
                    fileName, result.content(), result.extension(), result.fileSize());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("fileContextId", contextId);
            resp.put("fileName", fileName);
            resp.put("extension", result.extension());
            resp.put("parser", result.parser());
            resp.put("fileSize", result.fileSize());
            resp.put("textLength", result.textLength());
            resp.put("content", result.content());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return badRequest("文件解析失败: " + e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
