package com.example.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;

/**
 * 图片识别服务：将上传的图片通过 LLM 视觉模型进行描述。
 * <p>嵌套回调链路：
 * <pre>
 * FileUploadController → ImageRecognitionService.recognize()
 *   → 提取图片元信息（格式、尺寸）
 *   → 调用视觉 LLM 生成图片描述
 *   → 回调 FileContextService 存储识别结果
 *   → ManagerAgent 读取文件上下文 → 注入规划
 *   → Worker 通过 AgentContext.getFileContext() 获取图片描述
 * </pre>
 * <p>配置项（local/secret.env）：
 * <ul>
 *   <li>AI_VISION_API_KEY — 视觉模型 API Key</li>
 *   <li>AI_VISION_BASE_URL — 视觉模型 API 地址</li>
 *   <li>AI_VISION_MODEL — 视觉模型名称（如 gpt-4o-mini）</li>
 * </ul>
 * 无视觉模型时降级为元信息提取。
 */
@Service
public class ImageRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(ImageRecognitionService.class);

    @Value("${ai.vision.api-key:}")
    private String visionApiKey;

    @Value("${ai.vision.base-url:}")
    private String visionBaseUrl;

    @Value("${ai.vision.model:gpt-4o-mini}")
    private String visionModel;

    private final RestClient restClient = RestClient.create();

    /**
     * 识别图片内容，返回文字描述。
     *
     * @param imageBytes 图片字节数组
     * @param fileName   文件名
     * @return 图片描述文本
     */
    public String recognize(byte[] imageBytes, String fileName) {
        String metaInfo = extractMetaInfo(imageBytes, fileName);

        if (visionApiKey == null || visionApiKey.isBlank()) {
            log.info("未配置视觉模型，图片识别降级为元信息提取: {}", fileName);
            return metaInfo;
        }

        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = getMimeType(fileName);

            String prompt = "请详细描述这张图片的内容，包括：1) 图片类型（截图/照片/图表/文档等）"
                    + " 2) 主要内容 3) 关键文字（如有） 4) 布局结构。用简洁中文回答。";

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(visionBaseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + visionApiKey)
                    .header("Content-Type", "application/json")
                    .body(Map.of(
                            "model", visionModel,
                            "messages", new Object[]{
                                    Map.of("role", "user", "content", new Object[]{
                                            Map.of("type", "text", "text", prompt),
                                            Map.of("type", "image_url",
                                                    "image_url", Map.of("url", "data:" + mimeType + ";base64," + base64Image))
                                    })
                            },
                            "max_tokens", 500
                    ))
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("choices")) {
                var choices = (java.util.List<Map<String, Object>>) response.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    var message = (Map<String, Object>) choices.get(0).get("message");
                    if (message != null && message.containsKey("content")) {
                        String description = (String) message.get("content");
                        return metaInfo + "\n【图片描述】\n" + description;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("视觉模型调用失败，降级为元信息: {}", e.getMessage());
        }

        return metaInfo;
    }

    /** 提取图片元信息（不依赖外部 API） */
    private String extractMetaInfo(byte[] bytes, String fileName) {
        StringBuilder sb = new StringBuilder();
        sb.append("【图片元信息】\n");
        sb.append("文件名: ").append(fileName).append("\n");
        sb.append("大小: ").append(bytes.length).append(" 字节 (")
                .append(String.format("%.1f", bytes.length / 1024.0)).append(" KB)\n");

        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img != null) {
                sb.append("尺寸: ").append(img.getWidth()).append("x").append(img.getHeight()).append("\n");
                sb.append("色彩模式: ").append(img.getColorModel().getColorSpace().getType()).append("\n");

                int dominantRgb = getDominantColor(img);
                sb.append(String.format("主色调: #%06X\n", dominantRgb));
            }
        } catch (Exception e) {
            sb.append("（无法读取图片尺寸信息）\n");
        }

        sb.append("格式: ").append(getMimeType(fileName)).append("\n");
        return sb.toString();
    }

    /** 获取主色调（采样） */
    private int getDominantColor(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        long r = 0, g = 0, b = 0;
        int sampleStep = Math.max(1, (w * h) / 1000);
        int count = 0;
        for (int y = 0; y < h; y += sampleStep) {
            for (int x = 0; x < w; x += sampleStep) {
                int rgb = img.getRGB(x, y);
                r += (rgb >> 16) & 0xFF;
                g += (rgb >> 8) & 0xFF;
                b += rgb & 0xFF;
                count++;
            }
        }
        if (count == 0) return 0x808080;
        return ((int)(r/count) << 16) | ((int)(g/count) << 8) | (int)(b/count);
    }

    private String getMimeType(String fileName) {
        String ext = fileName.toLowerCase();
        if (ext.endsWith(".png")) return "image/png";
        if (ext.endsWith(".jpg") || ext.endsWith(".jpeg")) return "image/jpeg";
        if (ext.endsWith(".gif")) return "image/gif";
        if (ext.endsWith(".bmp")) return "image/bmp";
        if (ext.endsWith(".webp")) return "image/webp";
        return "image/png";
    }

    /** 判断文件是否为图片 */
    public static boolean isImage(String fileName) {
        if (fileName == null) return false;
        String ext = fileName.toLowerCase();
        return ext.endsWith(".png") || ext.endsWith(".jpg") || ext.endsWith(".jpeg")
                || ext.endsWith(".gif") || ext.endsWith(".bmp") || ext.endsWith(".webp");
    }
}
