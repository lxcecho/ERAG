package com.knowledge.kb.storage;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 文件存储结果元信息
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@AllArgsConstructor
public class StoredFile {

    /** 原始文件名 */
    private String originalName;

    /** 存储文件名（uuid + 后缀） */
    private String storedName;

    /** 相对路径（yyyy/MM/dd/storedName），用于入库与读取 */
    private String relativePath;

    /** 绝对路径（仅本地实现使用） */
    private String absolutePath;

    /** 文件大小(字节) */
    private long size;

    /** 文件后缀（小写、无点） */
    private String suffix;

    /** 文件类型（与后缀一致：pdf/doc/docx/md） */
    private String fileType;

    /** MD5 校验值 */
    private String md5;
}
