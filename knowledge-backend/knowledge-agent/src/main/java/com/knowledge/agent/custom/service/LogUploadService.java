package com.knowledge.agent.custom.service;

import com.knowledge.agent.custom.config.CustomAgentProperties;
import com.knowledge.agent.custom.dto.UploadResultVo;
import com.knowledge.agent.custom.storage.CustomAgentStorageService;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.storage.StoredFile;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;

/**
 * 自定义 Agent 日志/文档上传服务。
 * <p>流程：校验（大小/后缀）→ 落盘 → Tika 解析文本 → 返回 {@code full} 模式。
 * 上传文件不再切片向量化入库（Milvus），运行阶段由执行器读取全文注入提示词，
 * 超长按 {@link CustomAgentProperties#getLogContextLimit()} 截断。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogUploadService {

    /** 允许的文本类后缀（小写、无点） */
    private static final Set<String> ALLOWED_SUFFIXES = Set.of(
            "txt", "log", "md", "json", "csv", "xml", "yml", "yaml", "properties", "conf", "ini", "html");

    private final CustomAgentStorageService fileStorage;
    private final CustomAgentProperties props;

    /**
     * 上传并解析日志/文档文件（不切片、不向量化）。
     *
     * @param file 上传文件
     * @return 上传结果（fileRef + 注入模式）
     */
    public UploadResultVo upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        if (file.getSize() > props.getMaxUploadBytes()) {
            throw new BizException("文件过大，上限 " + props.getMaxUploadBytes() / 1024 / 1024 + "MB");
        }
        String suffix = extractSuffix(file.getOriginalFilename());
        if (!ALLOWED_SUFFIXES.contains(suffix)) {
            throw new BizException("仅支持文本类文件：" + String.join("/", ALLOWED_SUFFIXES));
        }

        // 1. 落盘
        StoredFile stored = fileStorage.store(file);
        String fileRef = stored.getRelativePath();

        // 2. 解析文本
        String text;
        try (InputStream is = fileStorage.open(fileRef)) {
            DocumentParser parser = new ApacheTikaDocumentParser();
            text = parser.parse(is).text();
        } catch (Exception e) {
            // 解析失败清理文件后抛出
            fileStorage.delete(fileRef);
            throw new BizException("文件解析失败: " + e.getMessage());
        }
        if (text == null || text.isBlank()) {
            fileStorage.delete(fileRef);
            throw new BizException("文件内容为空，无法解析");
        }

        log.info("[自定义Agent上传] {} mode=full chars={} ref={}", stored.getOriginalName(), text.length(), fileRef);
        return new UploadResultVo(fileRef, stored.getOriginalName(), "full", text.length(), 0);
    }

    /**
     * 读取上传文件文本（运行时全文注入）。
     *
     * @param fileRef 上传文件引用
     * @return 解析后的全文文本
     */
    public String readText(String fileRef) {
        try (InputStream is = fileStorage.open(fileRef)) {
            DocumentParser parser = new ApacheTikaDocumentParser();
            return parser.parse(is).text();
        } catch (Exception e) {
            throw new BizException("日志文件读取失败: " + e.getMessage());
        }
    }

    /**
     * 运行结束后删除上传文件（best-effort）。
     *
     * @param fileRef 上传文件引用
     */
    public void cleanupUpload(String fileRef) {
        if (fileRef == null) {
            return;
        }
        try {
            fileStorage.delete(fileRef);
        } catch (Exception e) {
            log.warn("[自定义Agent清理] fileRef={} 清理失败（best-effort）: {}", fileRef, e.getMessage());
        }
    }

    private String extractSuffix(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }
}
