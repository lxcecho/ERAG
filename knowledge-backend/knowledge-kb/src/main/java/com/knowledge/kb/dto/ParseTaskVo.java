package com.knowledge.kb.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 解析任务视图对象
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class ParseTaskVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long documentId;
    private Long kbId;
    /** 文档原始名（联查） */
    private String originalName;
    private Integer status;
    private String errorMsg;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
}
