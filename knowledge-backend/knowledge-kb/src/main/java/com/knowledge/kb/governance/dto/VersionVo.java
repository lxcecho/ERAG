package com.knowledge.kb.governance.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档版本视图。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class VersionVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long docId;
    private Integer version;
    private String storedName;
    private String filePath;
    private Long fileSize;
    private String md5;
    private String changeLog;
    private Long creatorId;
    private LocalDateTime createTime;
}
