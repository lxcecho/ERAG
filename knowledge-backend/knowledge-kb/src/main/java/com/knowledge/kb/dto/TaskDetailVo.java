package com.knowledge.kb.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 解析任务详情视图（含文档信息）
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class TaskDetailVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long documentId;
    private Long kbId;
    private Integer status;
    private String errorMsg;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;

    /** 关联文档信息 */
    private String originalName;
    private Long fileSize;
    private String fileType;
    private Integer docStatus;
    private Integer chunkCount;
}
