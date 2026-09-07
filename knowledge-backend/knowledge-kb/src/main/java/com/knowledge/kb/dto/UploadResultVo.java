package com.knowledge.kb.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 文档上传结果
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class UploadResultVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文档ID */
    private Long documentId;

    /** 解析任务ID */
    private Long taskId;

    /** 原始文件名 */
    private String originalName;

    /** 文件大小(字节) */
    private Long fileSize;

    /** 文件类型 */
    private String fileType;

    /** 解析状态 */
    private Integer status;
}
