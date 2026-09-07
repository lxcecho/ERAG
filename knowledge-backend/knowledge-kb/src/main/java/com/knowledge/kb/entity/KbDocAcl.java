package com.knowledge.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档级 ACL 实体。
 * <p>唯一键：(tenant_id, doc_id, subject_type, subject_id, permission, deleted)
 * 保证同一主体对同一动作只有 1 条规则（消除 allow+deny 歧义）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("kb_doc_acl")
public class KbDocAcl implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    /** 文档 ID（KB 内唯一） */
    private Long docId;

    /** 主体类型 U/R/D */
    private String subjectType;

    /** 主体 ID（user_id / role_id / dept_id） */
    private Long subjectId;

    /** 权限：VIEW/EDIT/DELETE/DOWNLOAD/SHARE */
    private String permission;

    /** 效果：A=ALLOW / D=DENY */
    private String effect;

    /** 授权人 user_id（审计） */
    private Long grantBy;

    /** 到期时间（NULL=永久） */
    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
