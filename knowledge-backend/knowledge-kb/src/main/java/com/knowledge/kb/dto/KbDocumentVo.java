package com.knowledge.kb.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档视图对象（分页联查携带知识库名称）
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class KbDocumentVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long kbId;
    private String kbName;
    private String originalName;
    private String storedName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private String fileSuffix;
    private String md5;
    private Integer status;
    private Integer chunkCount;
    private Long creatorId;
    private LocalDateTime createTime;
}
