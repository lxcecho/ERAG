package com.knowledge.auth.service;

import com.knowledge.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * 头像存储服务：本地目录落盘 + 返回可访问 URL 路径。
 * <p>头像属轻量资源（≤2MB），不依赖知识库文件存储模块（避免 auth→kb 循环依赖），
 * 直接写入 {@code ./data/avatars/}，由 {@code WebMvcConfig} 静态资源映射 {@code /avatars/**} 提供访问。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
public class AvatarStorageService {

    /** 头像访问前缀（静态资源映射 /avatars/** 指向 data/avatars/） */
    public static final String AVATAR_PREFIX = "/avatars/";

    private static final long MAX_SIZE = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("png", "jpg", "jpeg", "webp", "gif");

    private final Path baseDir = Paths.get(System.getProperty("user.dir"), "data", "avatars").toAbsolutePath().normalize();

    /**
     * 保存头像文件，返回可访问 URL 路径（如 /avatars/uuid.png）。
     */
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("头像文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException("头像文件过大，上限 2MB");
        }
        String suffix = extractSuffix(file.getOriginalFilename());
        if (!ALLOWED_TYPES.contains(suffix)) {
            throw new BizException("头像仅支持 png/jpg/jpeg/webp/gif");
        }
        try {
            Files.createDirectories(baseDir);
            String storedName = UUID.randomUUID().toString().replace("-", "") + "." + suffix;
            Path target = baseDir.resolve(storedName);
            file.transferTo(target.toFile());
            log.info("[头像] 上传成功 name={} size={}", storedName, file.getSize());
            return AVATAR_PREFIX + storedName;
        } catch (IOException e) {
            log.error("[头像] 存储失败", e);
            throw new BizException("头像存储失败: " + e.getMessage());
        }
    }

    /** 删除旧头像文件（best-effort，仅处理本站 /avatars/ 前缀的资源） */
    public void deleteQuietly(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(AVATAR_PREFIX)) {
            return;
        }
        try {
            String storedName = avatarUrl.substring(AVATAR_PREFIX.length());
            // 防目录穿越：仅删除纯文件名
            if (storedName.contains("/") || storedName.contains("\\") || storedName.contains("..")) {
                return;
            }
            Files.deleteIfExists(baseDir.resolve(storedName));
        } catch (IOException e) {
            log.warn("[头像] 删除失败 avatar={}: {}", avatarUrl, e.getMessage());
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
