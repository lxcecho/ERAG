package com.knowledge.kb.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端装配
 * <p>仅当 {@code kb.storage.type=minio} 时生效。启动时自动创建存储桶（若不存在）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kb.storage", name = "type", havingValue = "minio")
public class MinioConfig {

    private final StorageProperties props;

    @Bean
    public MinioClient minioClient() {
        StorageProperties.Minio c = props.getMinio();
        MinioClient client = MinioClient.builder()
                .endpoint(c.getEndpoint())
                .credentials(c.getAccessKey(), c.getSecretKey())
                .build();
        ensureBucket(client, c.getBucket());
        return client;
    }

    /** 启动时确保存储桶存在，避免上传时才创建导致首个请求失败 */
    private void ensureBucket(MinioClient client, String bucket) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("[MinIO] 创建存储桶: {}", bucket);
            } else {
                log.info("[MinIO] 存储桶已存在: {}", bucket);
            }
        } catch (Exception e) {
            // 桶初始化失败不阻断启动，上传时会再次尝试/抛出明确错误
            log.error("[MinIO] 存储桶初始化失败: {}", e.getMessage(), e);
        }
    }
}
