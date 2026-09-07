package com.knowledge.kb.storage;

import cn.hutool.crypto.digest.DigestUtil;
import com.knowledge.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * 本地文件存储实现
 * <p>存储规则：{basePath}/{yyyy/MM/dd}/{uuid}.{suffix}
 * <p>校验：文件后缀白名单 + 文件大小上限。
 * <p>当 {@code kb.storage.type=local}（或缺省）时生效。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kb.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final StorageProperties props;

    @Override
    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        if (file.getSize() > props.getMaxFileSize()) {
            throw new BizException("文件大小超过上限(" + (props.getMaxFileSize() / 1024 / 1024) + "MB)");
        }

        String originalName = file.getOriginalFilename();
        String suffix = resolveSuffix(originalName);
        if (!props.getAllowedTypes().contains(suffix)) {
            throw new BizException("不支持的文件类型，仅支持: " + props.getAllowedTypes());
        }

        try {
            // 按日期分目录，避免单目录文件过多
            String dateDir = LocalDate.now().format(DATE_DIR);
            String storedName = UUID.randomUUID().toString().replace("-", "") + "." + suffix;
            String relativePath = dateDir + "/" + storedName;

            Path base = Paths.get(props.getBasePath()).toAbsolutePath().normalize();
            Path target = base.resolve(relativePath).normalize();
            // 防止路径穿越：确保目标仍在根目录下
            if (!target.startsWith(base)) {
                throw new BizException("非法的文件路径");
            }
            Files.createDirectories(target.getParent());
            // 先计算 md5：transferTo 会 rename/删除 Tomcat 临时文件，之后再 getInputStream 将抛 NoSuchFileException
            String md5;
            try (InputStream in = file.getInputStream()) {
                md5 = DigestUtil.md5Hex(in);
            }
            file.transferTo(target.toFile());

            log.info("[文件存储] {} -> {} ({}B, md5={})", originalName, relativePath, file.getSize(), md5);

            return new StoredFile(originalName, storedName, relativePath, target.toString(),
                    file.getSize(), suffix, suffix, md5);
        } catch (IOException e) {
            log.error("[文件存储失败] {}", e.getMessage(), e);
            throw new BizException("文件存储失败: " + e.getMessage());
        }
    }

    @Override
    public InputStream open(String relativePath) {
        Path base = Paths.get(props.getBasePath()).toAbsolutePath().normalize();
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            throw new BizException("非法的文件路径");
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new BizException("文件读取失败: " + e.getMessage());
        }
    }

    @Override
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Path base = Paths.get(props.getBasePath()).toAbsolutePath().normalize();
            Path target = base.resolve(relativePath).normalize();
            if (!target.startsWith(base)) {
                return;
            }
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("[文件删除失败] {} : {}", relativePath, e.getMessage());
        }
    }

    /** 解析小写后缀（无点） */
    private String resolveSuffix(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
    }
}
