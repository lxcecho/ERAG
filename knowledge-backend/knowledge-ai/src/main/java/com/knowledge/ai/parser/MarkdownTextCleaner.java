package com.knowledge.ai.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Markdown文本清洗器
 * <p>
 * 功能：
 * 1. 去除页眉页脚
 * 2. 去除页码
 * 3. 修复断句（段落内的换行）
 * 4. 合并连续空行
 * 5. 清理特殊字符
 */
@Slf4j
@Component
public class MarkdownTextCleaner {

    /** 页码模式：单独一行的数字 */
    private static final Pattern PAGE_NUMBER = Pattern.compile("^\\s*-?\\s*\\d+\\s*-?\\s*$", Pattern.MULTILINE);

    /** 页眉页脚模式 */
    private static final Pattern HEADER_FOOTER = Pattern.compile(
            "^.{0,60}(版权所有|机密|Confidential|第\\d+页|Copyright).*$",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    /** 连续空行 */
    private static final Pattern MULTIPLE_BLANK_LINES = Pattern.compile("\\n{3,}");

    /** 特殊控制字符 */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");

    /** 多余空格 */
    private static final Pattern MULTIPLE_SPACES = Pattern.compile(" {3,}");

    /**
     * 清洗Markdown文本
     *
     * @param markdown 原始Markdown
     * @return 清洗后的Markdown
     */
    public String clean(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }

        String cleaned = markdown;

        // 1. 去除控制字符
        cleaned = CONTROL_CHARS.matcher(cleaned).replaceAll("");

        // 2. 去除页码
        cleaned = PAGE_NUMBER.matcher(cleaned).replaceAll("");

        // 3. 去除页眉页脚
        cleaned = HEADER_FOOTER.matcher(cleaned).replaceAll("");

        // 4. 修复断句
        cleaned = fixLineBreaks(cleaned);

        // 5. 合并连续空行
        cleaned = MULTIPLE_BLANK_LINES.matcher(cleaned).replaceAll("\n\n");

        // 6. 清理多余空格
        cleaned = MULTIPLE_SPACES.matcher(cleaned).replaceAll("  ");

        // 7. 去除首尾空白
        cleaned = cleaned.trim();

        int originalLength = markdown.length();
        int cleanedLength = cleaned.length();
        if (originalLength - cleanedLength > 100) {
            log.info("文本清洗: {} -> {} 字符 (减少 {})", originalLength, cleanedLength, originalLength - cleanedLength);
        }

        return cleaned;
    }

    /**
     * 修复断句：段落内的单个换行替换为空格
     * <p>
     * 规则：
     * - 标题行后保持换行
     * - 表格行保持换行
     * - 列表项保持换行
     * - 普通文本行合并（如果不是句子结尾）
     */
    private String fixLineBreaks(String text) {
        StringBuilder result = new StringBuilder();
        String[] lines = text.split("\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // 空行：保留
            if (line.isEmpty()) {
                if (result.length() > 0 && !result.toString().endsWith("\n\n")) {
                    result.append("\n\n");
                }
                continue;
            }

            // 标题行：保留换行
            if (line.startsWith("#")) {
                ensureDoubleNewline(result);
                result.append(line).append("\n\n");
                continue;
            }

            // 表格行：保留换行
            if (line.startsWith("|")) {
                result.append(line).append("\n");
                continue;
            }

            // 列表项：保留换行
            if (line.matches("^\\d+\\.\\s.*") || line.matches("^[-*]\\s.*")) {
                result.append(line).append("\n");
                continue;
            }

            // 代码块标记：保留换行
            if (line.startsWith("```")) {
                result.append(line).append("\n");
                continue;
            }

            // 引用块：保留换行
            if (line.startsWith(">")) {
                result.append(line).append("\n");
                continue;
            }

            // 普通文本：判断是否需要合并
            if (result.length() > 0) {
                String resultStr = result.toString();
                String lastLine = getLastLine(resultStr);

                // 如果上一行是句子结尾，保持换行
                if (isSentenceEnd(lastLine)) {
                    result.append("\n");
                }
                // 否则用空格连接（修复PDF断句）
                else if (!resultStr.endsWith("\n\n") && !resultStr.endsWith("\n")) {
                    result.append(" ");
                }
            }

            result.append(line);
        }

        return result.toString();
    }

    /**
     * 确保文本以双换行结尾
     */
    private void ensureDoubleNewline(StringBuilder sb) {
        if (sb.length() == 0) return;

        String text = sb.toString();
        if (!text.endsWith("\n\n")) {
            if (text.endsWith("\n")) {
                sb.append("\n");
            } else {
                sb.append("\n\n");
            }
        }
    }

    /**
     * 获取最后一行文本
     */
    private String getLastLine(String text) {
        int lastNewline = text.lastIndexOf('\n');
        if (lastNewline < 0) return text.trim();
        return text.substring(lastNewline + 1).trim();
    }

    /**
     * 判断是否是句子结尾
     */
    private boolean isSentenceEnd(String line) {
        if (line.isEmpty()) return true;
        char lastChar = line.charAt(line.length() - 1);
        return lastChar == '。' || lastChar == '！' || lastChar == '？' ||
               lastChar == '.' || lastChar == '!' || lastChar == '?' ||
               lastChar == '」' || lastChar == '）' || lastChar == ')';
    }
}
