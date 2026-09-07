package com.knowledge.kb.storage;

import cn.hutool.crypto.digest.DigestUtil;
import com.knowledge.common.exception.BizException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * MinIO 对象存储实现
 * <p>存储规则：{bucket}/{yyyy/MM/dd}/{uuid}.{suffix}，relativePath 即对象名。
 * <p>当 {@code kb.storage.type=minio} 时生效，docker 部署场景使用。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kb.storage", name = "type", havingValue = "minio")
public class MinioStorageService implements StorageService {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final MinioClient minioClient;
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

        String dateDir = LocalDate.now().format(DATE_DIR);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + suffix;
        String relativePath = dateDir + "/" + storedName;
        String bucket = props.getMinio().getBucket();

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(relativePath)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("[MinIO存储失败] {}", e.getMessage(), e);
            throw new BizException("MinIO 文件存储失败: " + e.getMessage());
        }

        String md5;
        try (InputStream is = file.getInputStream()) {
            md5 = DigestUtil.md5Hex(is);
        } catch (IOException e) {
            md5 = "";
        }
        log.info("[MinIO存储] {} -> {}/{} ({}B)", originalName, bucket, relativePath, file.getSize());
        // MinIO 无本地绝对路径，absolutePath 置空
        return new StoredFile(originalName, storedName, relativePath, null,
                file.getSize(), suffix, suffix, md5);
    }

    @Override
    public InputStream open(String relativePath) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(props.getMinio().getBucket())
                    .object(relativePath)
                    .build());
        } catch (Exception e) {
            throw new BizException("MinIO 文件读取失败: " + e.getMessage());
        }
    }

    @Override
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(props.getMinio().getBucket())
                    .object(relativePath)
                    .build());
        } catch (Exception e) {
            log.warn("[MinIO删除失败] {} : {}", relativePath, e.getMessage());
        }
    }

    private String resolveSuffix(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
    }
}
