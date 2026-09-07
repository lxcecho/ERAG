package com.knowledge.kb.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件存储服务抽象
 * <p>提供本地 {@link LocalStorageService} 与 MinIO {@link MinioStorageService} 两种实现，
 * 通过 {@code kb.storage.type} 配置切换，业务层无感。文档解析（RAG 入库）通过
 * {@link #open(String)} 读取文件流，统一适配两种存储后端。
 *
 * @author: lxcechoo@gmail.com
 */
public interface StorageService {

    /**
     * 存储上传文件，返回存储元信息。
     *
     * @param file 上传文件
     * @return 存储结果
     */
    StoredFile store(MultipartFile file);

    /**
     * 读取指定相对路径的文件输入流（供文档解析读取原始文件）。
     *
     * @param relativePath 相对路径
     * @return 文件输入流
     */
    InputStream open(String relativePath);

    /**
     * 删除指定相对路径的文件。
     *
     * @param relativePath 相对路径
     */
    void delete(String relativePath);
}
