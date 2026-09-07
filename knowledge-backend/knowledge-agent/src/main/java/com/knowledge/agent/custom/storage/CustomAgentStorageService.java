package com.knowledge.agent.custom.storage;

import com.knowledge.agent.custom.config.CustomAgentProperties;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.storage.StoredFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 自定义 Agent 上传文件独立本地存储。
 * <p>自定义 Agent 日志/文档上传不依赖知识库文件存储模块（其 {@code kb.storage.allowed-types}
 * 白名单仅允许 pdf/doc/docx/md，会拦截 .log 等文本文件），独立落盘到
 * {@code custom-agent.data-path} 配置目录（默认 {@code ./data/agent-uploads}，基于 user.dir 的绝对路径）。
 * <p>存储规则：{basePath}/{yyyy/MM/dd}/{uuid}.{suffix}，与知识库本地存储保持一致。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
public class CustomAgentStorageService {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final Path baseDir;

    public CustomAgentStorageService(CustomAgentProperties props) {
        this.baseDir = Paths.get(props.getDataPath()).toAbsolutePath().normalize();
    }

    /**
     * 存储上传文件，返回存储元信息。
     *
     * @param file 上传文件
     * @return 存储结果
     */
    public StoredFile store(MultipartFile file) {
        try {
            String originalName = file.getOriginalFilename();
            String suffix = resolveSuffix(originalName);
            String dateDir = LocalDate.now().format(DATE_DIR);
            String storedName = UUID.randomUUID().toString().replace("-", "") + (suffix.isEmpty() ? "" : "." + suffix);
            String relativePath = dateDir + "/" + storedName;

            Path target = baseDir.resolve(relativePath).normalize();
            if (!target.startsWith(baseDir)) {
                throw new BizException("非法的文件路径");
            }
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
            log.info("[自定义Agent存储] {} -> {} ({}B)", originalName, relativePath, file.getSize());
            return new StoredFile(originalName, storedName, relativePath, target.toString(),
                    file.getSize(), suffix, suffix, null);
        } catch (IOException e) {
            log.error("[自定义Agent存储失败] {}", e.getMessage(), e);
            throw new BizException("文件存储失败: " + e.getMessage());
        }
    }

    /**
     * 读取指定相对路径的文件输入流。
     *
     * @param relativePath 相对路径
     * @return 文件输入流
     */
    public InputStream open(String relativePath) {
        Path target = baseDir.resolve(relativePath).normalize();
        if (!target.startsWith(baseDir)) {
            throw new BizException("非法的文件路径");
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new BizException("文件读取失败: " + e.getMessage());
        }
    }

    /**
     * 删除指定相对路径的文件。
     *
     * @param relativePath 相对路径
     */
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Path target = baseDir.resolve(relativePath).normalize();
            if (!target.startsWith(baseDir)) {
                return;
            }
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("[自定义Agent文件删除失败] {} : {}", relativePath, e.getMessage());
        }
    }

    private String resolveSuffix(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
    }
}
