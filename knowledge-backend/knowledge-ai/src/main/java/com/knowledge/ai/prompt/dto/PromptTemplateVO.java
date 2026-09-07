package com.knowledge.ai.prompt.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Prompt 模板聚合 VO（三表聚合，兼容前端原 {@code PromptTemplate} 类型）。
 * <p>聚合 {@code prompt_template}（元数据）+ {@code prompt_version}（版本内容）+
 * {@code prompt_variable}（变量定义，聚合为逗号分隔字符串）。
 * <p><b>关键：{@code id} = 版本行 ID</b>（前端 {@code row.id} 语义不变，
 * edit/publish/rollback/delete 均以版本 ID 为准），{@code templateId} 为新增辅助字段。
 * 字段集与原单表 {@code PromptTemplate} 完全一致，保证前端零改动。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class PromptTemplateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 版本行ID（前端 row.id 语义，edit/publish/rollback/delete 准） */
    private Long id;

    /** 所属模板ID（prompt_template.id，辅助字段） */
    private Long templateId;

    /** 所属租户ID（0=平台预置） */
    private Long tenantId;

    /** Prompt 逻辑编码 */
    private String promptCode;

    /** 模板名称 */
    private String name;

    /** 类型 system/rag/agent */
    private String type;

    /** 版本号 */
    private Integer version;

    /** 模板内容（含 {var} 命名占位符） */
    private String content;

    /** 变量列表（逗号分隔，由 prompt_variable 表聚合） */
    private String variables;

    /** 状态：DRAFT/PUBLISHED/ARCHIVED */
    private String status;

    /** 版本说明 */
    private String remark;

    /** 创建人ID */
    private Long creatorId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
