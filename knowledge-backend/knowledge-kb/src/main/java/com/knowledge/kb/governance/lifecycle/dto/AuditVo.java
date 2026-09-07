package com.knowledge.kb.governance.lifecycle.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识审计记录视图（联表携带文档名）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class AuditVo {

    private Long id;
    private Long tenantId;
    private Long userId;
    private Long docId;
    private String docName;
    private String action;
    private String result;
    private String passReason;
    private String denyReason;
    private String ip;
    private String userAgent;
    private LocalDateTime createTime;
}
