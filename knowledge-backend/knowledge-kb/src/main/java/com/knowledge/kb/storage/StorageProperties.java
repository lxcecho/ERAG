package com.knowledge.kb.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件存储配置属性（读取 application.yml 中 kb.storage.* 配置）
 * <p>type=local 使用本地文件系统；type=minio 使用 MinIO 对象存储（docker 部署）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Component
@ConfigurationProperties(prefix = "kb.storage")
public class StorageProperties {

    /** 存储类型 local/minio */
    private String type = "local";

    /** 本地存储根目录 */
    private String basePath = "./data/files";

    /** 允许上传的文件后缀（小写、不含点） */
    private List<String> allowedTypes = List.of("pdf", "doc", "docx", "md");

    /** 单文件最大大小（字节），默认 100MB */
    private long maxFileSize = 100L * 1024 * 1024;

    /** 静态资源访问前缀（预留，对接 Controller 暴露下载时使用） */
    private String urlPrefix = "/files";

    /** MinIO 配置（type=minio 时生效） */
    private Minio minio = new Minio();

    @Data
    public static class Minio {
        /** MinIO 服务地址，如 http://localhost:9000 */
        private String endpoint = "http://localhost:9000";
        /** 访问密钥 */
        private String accessKey = "minioadmin";
        /** 秘密密钥 */
        private String secretKey = "minioadmin";
        /** 存储桶名 */
        private String bucket = "knowledge-files";
    }
}
