package com.knowledge.kb.governance.lifecycle.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档生命周期状态视图。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class LifecycleVo {

    private Long docId;
    private Long kbId;
    private String docName;

    /** 生命周期状态 DRAFT/REVIEW/PUBLISHED/ARCHIVED */
    private String lifecycleStatus;

    /** 审核状态 PENDING/APPROVED/REJECTED（桥接同步） */
    private String reviewStatus;

    private Integer version;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private LocalDateTime archivedAt;
    private LocalDateTime effectiveFrom;
    private LocalDateTime expireAt;
}
