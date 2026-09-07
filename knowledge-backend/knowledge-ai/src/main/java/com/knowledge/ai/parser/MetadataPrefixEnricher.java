package com.knowledge.ai.parser;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 元数据前缀注入器
 * <p>
 * 为每个chunk注入前缀元数据：
 * 【文档名 > 章节路径】
 * <p>
 * 作用：
 * 1. 提升向量检索的命中率
 * 2. 让LLM更好地理解上下文来源
 */
@Slf4j
@Component
public class MetadataPrefixEnricher {

    /**
     * 为segments注入增强元数据
     *
     * @param segments   切片列表
     * @param documentName 文档名称
     * @return 增强后的切片列表
     */
    public List<TextSegment> enrich(List<TextSegment> segments, String documentName) {
        String cleanDocName = cleanDocumentName(documentName);

        for (TextSegment segment : segments) {
            Metadata metadata = segment.metadata();

            // 构建前缀
            String prefix = buildPrefix(cleanDocName, metadata);

            // 注入前缀到文本内容
            String originalText = segment.text();
            String enrichedText = prefix + "\n\n" + originalText;

            // 更新元数据
            metadata.put("documentName", cleanDocName);
            metadata.put("enrichedPrefix", prefix);

            // 创建新的TextSegment（因为TextSegment是不可变的）
            TextSegment enrichedSegment = TextSegment.from(enrichedText, metadata);

            // 替换原segment（需要在调用方处理）
            int index = segments.indexOf(segment);
            if (index >= 0) {
                segments.set(index, enrichedSegment);
            }
        }

        return segments;
    }

    /**
     * 构建前缀文本
     * <p>
     * 格式：【文档名 > 章节路径】
     */
    private String buildPrefix(String documentName, Metadata metadata) {
        StringBuilder prefix = new StringBuilder();
        prefix.append("【").append(documentName);

        // 从metadata中提取标题路径
        // MarkdownHeaderSplitter会注入 header_1, header_2 等字段
        String headingPath = buildHeadingPath(metadata);

        if (!headingPath.isEmpty()) {
            prefix.append(" > ").append(headingPath);
        }

        prefix.append("】");

        return prefix.toString();
    }

    /**
     * 从Metadata中构建标题路径
     * <p>
     * MarkdownHeaderSplitter会在metadata中注入：
     * - header_1: h1标题
     * - header_2: h2标题
     * - header_3: h3标题
     * 等等
     */
    private String buildHeadingPath(Metadata metadata) {
        StringBuilder path = new StringBuilder();

        for (int i = 1; i <= 6; i++) {
            String headerKey = "header_" + i;
            String headerValue = metadata.getString(headerKey);
            if (headerValue != null && !headerValue.isEmpty()) {
                if (path.length() > 0) {
                    path.append(" > ");
                }
                path.append(headerValue);
            }
        }

        return path.toString();
    }

    /**
     * 清理文档名（去掉扩展名）
     */
    private String cleanDocumentName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "未知文档";
        }
        // 去掉常见文档扩展名
        return fileName.replaceAll("\\.(pdf|PDF|doc|docx|DOC|DOCX|md|MD|txt|TXT)$", "");
    }
}
