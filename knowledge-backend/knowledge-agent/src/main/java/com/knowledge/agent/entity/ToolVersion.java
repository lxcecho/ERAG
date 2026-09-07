package com.knowledge.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工具版本历史实体（Tool Registry 三表之一）。
 * <p>同工具多版本，状态机 DRAFT→PUBLISHED→ARCHIVED；
 * {@code tool_metadata.current_version} 指向当前生效版本。
 * <p>{@code schema_snapshot} 存该版本 parametersJsonSchema 快照，供审计与回放。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("tool_version")
public class ToolVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属租户ID（0=平台预置） */
    private Long tenantId;

    /** 工具名 */
    private String toolName;

    /** 版本号（语义化 1.0.0） */
    private String version;

    /** 状态 DRAFT草稿/PUBLISHED已发布/ARCHIVED已归档 */
    private String status;

    /** 版本变更说明 */
    private String changelog;

    /** 该版本 parametersJsonSchema 快照（审计） */
    private String schemaSnapshot;

    /** 创建人ID */
    private Long creatorId;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
