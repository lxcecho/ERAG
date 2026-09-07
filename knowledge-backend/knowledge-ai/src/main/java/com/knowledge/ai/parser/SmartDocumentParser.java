package com.knowledge.ai.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 智能文档解析器
 * <p>
 * 根据文件类型和配置选择合适的解析器：
 * - PDF文件：优先使用MinerU，失败时可回退到Tika
 * - 其他文件：使用Tika
 */
@Slf4j
@Component
public class SmartDocumentParser {

    @Autowired(required = false)
    private MinerUClient minerUClient;

    @Autowired
    private MarkdownTextCleaner markdownCleaner;

    @Value("${ai.parser.type:tika}")
    private String parserType;

    @Value("${ai.parser.mineru.fallback-to-tika:true}")
    private boolean fallbackToTika;

    /**
     * 解析文档
     *
     * @param inputStream 文档输入流
     * @param filename    文件名
     * @return 解析后的Document
     */
    public Document parse(InputStream inputStream, String filename) throws IOException {
        boolean isPdf = isPdfFile(filename);

        // PDF文件且配置了MinerU
        if (isPdf && "mineru".equals(parserType) && minerUClient != null) {
            return parseWithMinerU(inputStream, filename);
        }

        // 其他情况使用Tika
        return parseWithTika(inputStream);
    }

    /**
     * 使用MinerU解析PDF
     */
    private Document parseWithMinerU(InputStream inputStream, String filename) {
        try {
            log.info("使用MinerU解析: {}", filename);

            // 读取全部字节
            byte[] pdfBytes = inputStream.readAllBytes();

            // 调用MinerU服务
            MinerUClient.MinerUResponse response = minerUClient.parsePdf(pdfBytes, filename);

            if (response.isSuccess() && response.getMarkdown() != null) {
                // 清洗Markdown
                String cleanedMarkdown = markdownCleaner.clean(response.getMarkdown());

                // 构建元数据
                Metadata metadata = new Metadata();
                metadata.put("parser", "mineru");
                metadata.put("title", response.getTitle() != null ? response.getTitle() : "");
                metadata.put("totalPages", String.valueOf(response.getTotalPages()));
                if (filename != null) {
                    metadata.put("originalFilename", filename);
                }

                return Document.from(cleanedMarkdown, metadata);
            } else {
                throw new RuntimeException("MinerU返回失败: " + response.getMessage());
            }

        } catch (Exception e) {
            log.error("MinerU解析失败: {}", e.getMessage());

            if (fallbackToTika) {
                log.info("回退到Tika解析: {}", filename);
                try {
                    // 重新创建输入流
                    byte[] pdfBytes = inputStream.readAllBytes();
                    return parseWithTika(new ByteArrayInputStream(pdfBytes));
                } catch (Exception tikaEx) {
                    log.error("Tika回退也失败: {}", tikaEx.getMessage());
                    throw new RuntimeException("MinerU和Tika都解析失败", tikaEx);
                }
            }

            throw new RuntimeException("MinerU解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用Tika解析文档
     */
    private Document parseWithTika(InputStream inputStream) throws IOException {
        log.info("使用Tika解析");
        DocumentParser parser = new ApacheTikaDocumentParser();
        Document doc = parser.parse(inputStream);

        // 添加解析器标记
        doc.metadata().put("parser", "tika");

        return doc;
    }

    /**
     * 判断是否是PDF文件
     */
    private boolean isPdfFile(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    /**
     * 获取当前使用的解析器类型
     */
    public String getParserType() {
        if ("mineru".equals(parserType) && minerUClient != null) {
            return "mineru";
        }
        return "tika";
    }

    /**
     * 检查MinerU服务是否可用
     */
    public boolean isMinerUAvailable() {
        return minerUClient != null && minerUClient.isHealthy();
    }
}
