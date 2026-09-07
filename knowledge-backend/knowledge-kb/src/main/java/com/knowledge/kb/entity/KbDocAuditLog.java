package com.knowledge.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档访问审计日志实体（合规用，写多读少，不走行级租户逻辑过滤——查询时手动加 tenant_id，写入必带）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("kb_doc_audit_log")
public class KbDocAuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long userId;

    private Long docId;

    /** VIEW/DOWNLOAD/EDIT/DELETE/SHARE/SEARCH_HIT */
    private String action;

    /** A=放行 D=拒绝 */
    private String result;

    /** 放行原因：ACL_ALLOW/KB_OWNER/KB_EDITOR/CREATOR/TENANT_ADMIN */
    private String passReason;

    /** 拒绝原因：DENY_RULE/NO_INHERIT/OUT_OF_KB_ROLE/PRIVATE_DOC */
    private String denyReason;

    private String ip;

    private String userAgent;

    private LocalDateTime createTime;
}
