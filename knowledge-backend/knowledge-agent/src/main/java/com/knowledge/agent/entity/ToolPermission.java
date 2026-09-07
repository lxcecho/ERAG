package com.knowledge.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工具权限授权实体（Tool Registry 三表之一）。
 * <p>显式授权/拒绝记录；{@code subject_type} T=租户全局 / R=角色级，
 * {@code subject_id} 对应 tenant_id 或 role_id。
 * <p><b>默认开放</b>：无记录时放行，避免破坏现有行为；有记录才生效授权/拒绝。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("tool_permission")
public class ToolPermission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属租户ID */
    private Long tenantId;

    /** 工具名 */
    private String toolName;

    /** 主体类型 T=租户全局 R=角色级 */
    private String subjectType;

    /** 主体ID（T=tenant_id / R=role_id） */
    private Long subjectId;

    /** 授权 0拒绝 1允许 */
    private Integer enabled;

    /** 创建人ID */
    private Long creatorId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
