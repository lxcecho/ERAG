package com.knowledge.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库成员关系实体
 * <p>不带逻辑删除：成员移除直接物理删除（uk_kb_user 唯一键约束需要清理后才能重新添加）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("kb_member")
public class KbMember implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属租户ID */
    private Long tenantId;

    /** 知识库ID */
    private Long kbId;

    /** 用户ID */
    private Long userId;

    /** 角色：owner/editor/viewer */
    private String role;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
