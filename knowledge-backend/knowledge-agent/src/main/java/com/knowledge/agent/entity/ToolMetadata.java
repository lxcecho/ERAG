package com.knowledge.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工具元数据实体（Tool Registry 三表之一）。
 * <p>一个 {@code (tenant_id, tool_name)} 唯一对应一行元数据；版本历史见 {@link ToolVersion}，
 * 权限授权见 {@link ToolPermission}。
 * <p>{@code tenant_id=0} 表示平台预置默认，对所有租户可见；租户行覆盖平台默认。
 * <p>设计：代码层 {@code Tool} 接口为可执行行为真源(name/schema/execute)，
 * 本表为运行时可配置叠加层(启用/超时/版本/分类/租户开关)。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("tool_metadata")
public class ToolMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属租户ID（0=平台预置，对所有租户可见） */
    private Long tenantId;

    /** 工具唯一标识（snake_case，对应 Tool.name()） */
    private String toolName;

    /** 展示名称 */
    private String displayName;

    /** 工具描述（供 LLM 理解用途） */
    private String description;

    /** 分类 knowledge/document/communication/http/data/other */
    private String category;

    /** 当前生效版本号（指向 tool_version.version） */
    private String currentVersion;

    /** 是否需 KB 权限（0否 1是，与代码层 authRequired 镜像） */
    private Integer authRequired;

    /** 单次执行超时毫秒（NULL=用全局默认 agent.tool.default-timeout-ms） */
    private Integer timeoutMs;

    /** 是否启用（0禁用 1启用，租户级开关） */
    private Integer enabled;

    /** 前端展示图标 */
    private String icon;

    /** 排序（升序） */
    private Integer sortOrder;

    /** 创建人ID */
    private Long creatorId;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
