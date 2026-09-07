package com.knowledge.agent.custom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日志/文档上传结果。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "日志上传结果")
public class UploadResultVo {

    /** 文件引用（运行请求 fileRef 使用） */
    private String fileRef;

    /** 原始文件名 */
    private String originalName;

    /** 上下文注入模式：full 全文注入 / chunked 切片检索 */
    private String mode;

    /** 文本字符数 */
    private int charCount;

    /** 切片数（mode=chunked 时 > 0） */
    private int chunkCount;
}
