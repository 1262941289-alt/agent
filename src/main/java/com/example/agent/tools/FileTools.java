package com.example.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * 文件系统工具：暴露给 agent 的 readFile / writeFile / listFiles。
 * <p>写路径 fence（参考 deepseek-harness fs-sandbox）：读放行，写限制在 workspace root 内。
 * 防止 agent 向 workspace 外写入或覆盖系统文件。
 */
@Component
public class FileTools {

    private static final Logger log = LoggerFactory.getLogger(FileTools.class);

    @Value("${sk-agent.fs.workspace-root:${user.dir}}")
    private String workspaceRoot;

    @Value("${sk-agent.fs.mode:workspace-write}")
    private String mode;

    @Tool(description = "读取本地文件内容（文本文件）。可读取 workspace 内外的文件。maxChars 控制截断长度。")
    public String readFile(@ToolParam(description = "文件路径（相对或绝对）") String filePath,
                           @ToolParam(description = "最多返回字符数，默认 8000，0 表示不截断") Integer maxChars) {
        int limit = maxChars == null ? 8000 : maxChars;
        try {
            Path p = resolveSafe(filePath);
            if (!Files.exists(p)) {
                return "文件不存在: " + filePath;
            }
            if (Files.isDirectory(p)) {
                return "路径是目录，不是文件: " + filePath;
            }
            String content = Files.readString(p, StandardCharsets.UTF_8);
            if (limit > 0 && content.length() > limit) {
                return content.substring(0, limit) + "\n…（已截断，共 " + content.length() + " 字符）";
            }
            return content;
        } catch (IOException e) {
            return "读取失败: " + e.getMessage();
        }
    }

    @Tool(description = "写入本地文件（覆盖或新建）。仅允许写入 workspace 目录内的文件。")
    public String writeFile(@ToolParam(description = "文件路径（相对或绝对）") String filePath,
                            @ToolParam(description = "要写入的文本内容") String content) {
        try {
            Path p = resolveSafe(filePath);
            String denial = checkWriteFence(p);
            if (denial != null) {
                return denial;
            }
            Files.createDirectories(p.getParent() != null ? p.getParent() : Path.of("."));
            Files.writeString(p, content == null ? "" : content, StandardCharsets.UTF_8);
            return "已写入 " + p + "（" + (content == null ? 0 : content.length()) + " 字符）";
        } catch (IOException e) {
            return "写入失败: " + e.getMessage();
        }
    }

    @Tool(description = "列出目录下的文件和子目录。返回每项的类型（文件/目录）、名称、大小。")
    public String listFiles(@ToolParam(description = "目录路径（相对或绝对），默认 workspace 根目录") String dirPath) {
        try {
            Path p = resolveSafe(dirPath == null || dirPath.isBlank() ? "." : dirPath);
            if (!Files.exists(p)) {
                return "目录不存在: " + dirPath;
            }
            if (!Files.isDirectory(p)) {
                return "路径不是目录: " + dirPath;
            }
            StringBuilder sb = new StringBuilder("目录: " + p + "\n");
            try (Stream<Path> stream = Files.list(p)) {
                List<Path> items = stream.limit(100).toList();
                for (Path item : items) {
                    String type = Files.isDirectory(item) ? "目录" : "文件";
                    long size = Files.isRegularFile(item) ? Files.size(item) : 0;
                    sb.append(type).append("  ").append(item.getFileName());
                    if (size > 0) {
                        sb.append("  (").append(size).append(" bytes)");
                    }
                    sb.append("\n");
                }
            }
            return sb.toString().strip();
        } catch (IOException e) {
            return "列目录失败: " + e.getMessage();
        }
    }

    /** 写路径 fence：read-only 模式拒绝所有写；workspace-write 模式限制在 workspace 内；full-access 不限制。 */
    private String checkWriteFence(Path target) {
        if ("read-only".equals(mode)) {
            return "【文件系统保护】当前为只读模式，禁止写入: " + target;
        }
        if ("full-access".equals(mode)) {
            return null;
        }
        // workspace-write 模式：检查目标是否在 workspace root 内
        try {
            Path workspace = Path.of(workspaceRoot).toAbsolutePath().toRealPath();
            Path resolved = target.toAbsolutePath().toRealPath();
            if (!resolved.startsWith(workspace)) {
                return "【文件系统保护】写入路径 " + resolved + " 不在 workspace 目录 " + workspace + " 内，已拒绝。请使用 workspace 内的路径。";
            }
        } catch (IOException e) {
            // 路径尚不存在（新建文件），用 lexical 判断
            Path workspace = Path.of(workspaceRoot).toAbsolutePath().normalize();
            Path resolved = target.toAbsolutePath().normalize();
            if (!resolved.startsWith(workspace)) {
                return "【文件系统保护】写入路径 " + resolved + " 不在 workspace 目录 " + workspace + " 内，已拒绝。请使用 workspace 内的路径。";
            }
        }
        return null;
    }

    private Path resolveSafe(String filePath) {
        Path base = Path.of(workspaceRoot).toAbsolutePath();
        Path p = Paths.get(filePath);
        if (p.isAbsolute()) {
            return p;
        }
        return base.resolve(p).normalize();
    }
}
