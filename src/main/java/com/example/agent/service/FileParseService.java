package com.example.agent.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 主流文件文本提取服务：从上传文件中提取纯文本内容。
 * <p>支持格式：
 * <ul>
 *   <li>PDF (.pdf) — Apache PDFBox</li>
 *   <li>Word (.docx) — Apache POI XWPF</li>
 *   <li>Excel (.xlsx, 多 sheet) — Apache POI XSSF</li>
 *   <li>分隔文本 (.csv, .tsv) — 按分隔符提取</li>
 *   <li>纯文本 (.txt, .md, .json, .xml, .yaml, .log 等) — UTF-8 直读</li>
 * </ul>
 */
@Service
public class FileParseService {

    static {
        ZipSecureFile.setMinInflateRatio(0.0001);
        ZipSecureFile.setMaxEntrySize(0xFFFFFFFFL);
    }

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "json", "xml", "yaml", "yml", "log", "csv", "tsv", "html", "htm", "sql", "java", "py", "js", "ts"
    );

    private static final int MAX_TEXT_LENGTH = 100_000;

    /**
     * 解析文件并提取文本内容。
     *
     * @param bytes       文件字节数据
     * @param fileName    原始文件名（用于扩展名判断）
     * @return 提取的文本内容
     */
    public ParseResult parse(byte[] bytes, String fileName) {
        String ext = getExtension(fileName).toLowerCase();
        String content;
        String parser;

        if (bytes.length < 4) {
            throw new IllegalArgumentException("文件内容过小（" + bytes.length + " 字节），可能不是有效文件");
        }

        try {
            switch (ext) {
                case "pdf" -> { content = parsePdf(bytes); parser = "PDFBox"; }
                case "docx" -> { content = parseDocx(bytes); parser = "POI-XWPF"; }
                case "xlsx" -> { content = parseXlsx(bytes); parser = "POI-XSSF"; }
                case "csv", "tsv" -> { content = parseCsv(bytes, ext); parser = "Delimiter"; }
                default -> {
                    if (TEXT_EXTENSIONS.contains(ext)) {
                        content = decodeText(bytes);
                        parser = "UTF-8";
                    } else {
                        content = decodeText(bytes);
                        parser = "UTF-8(fallback)";
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(ext + " 格式解析失败，文件可能已损坏或非标准格式: " + e.getMessage(), e);
        }

        if (content.length() > MAX_TEXT_LENGTH) {
            content = content.substring(0, MAX_TEXT_LENGTH) + "\n\n[... 文本已截断，原始长度 " + content.length() + " 字符 ...]";
        }

        return new ParseResult(content, ext, parser, bytes.length, content.length());
    }

    private String parsePdf(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            return text.strip();
        } catch (Exception e) {
            throw new IllegalArgumentException("PDF 解析失败: " + e.getMessage(), e);
        }
    }

    private String parseDocx(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (!text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }
            for (XWPFTable table : doc.getTables()) {
                sb.append("\n");
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder rowSb = new StringBuilder();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText().strip();
                        if (rowSb.length() > 0) rowSb.append(" | ");
                        rowSb.append(cellText);
                    }
                    if (!rowSb.isEmpty()) {
                        sb.append(rowSb).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Word(.docx) 解析失败: " + e.getMessage(), e);
        }
        return sb.toString().strip();
    }

    private String parseXlsx(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            DataFormatter fmt = new DataFormatter();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                if (sb.length() > 0) sb.append("\n\n");
                sb.append("[Sheet: ").append(sheet.getSheetName()).append("]\n");
                for (Row row : sheet) {
                    StringBuilder rowSb = new StringBuilder();
                    for (Cell cell : row) {
                        String val = fmt.formatCellValue(cell);
                        if (rowSb.length() > 0) rowSb.append("\t");
                        rowSb.append(val);
                    }
                    if (!rowSb.isEmpty()) {
                        sb.append(rowSb).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Excel(.xlsx) 解析失败: " + e.getMessage(), e);
        }
        return sb.toString().strip();
    }

    private String parseCsv(byte[] bytes, String ext) {
        String text = decodeText(bytes);
        return text.strip();
    }

    static String decodeText(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (utf8.indexOf('\uFFFD') >= 0) {
            return new String(bytes, java.nio.charset.Charset.forName("GBK"));
        }
        return utf8;
    }

    private String getExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "";
    }

    public record ParseResult(String content, String extension, String parser,
                               long fileSize, int textLength) {
    }
}
