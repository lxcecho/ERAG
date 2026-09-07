package com.knowledge.ai.prompt.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Prompt 版本内容实体（三表拆分后承载版本内容与状态）。
 * <p>关联 {@link PromptTemplate#getId()}，同 template_id 下 version 递增、唯一。
 * <p>状态机 DRAFT→PUBLISHED→ARCHIVED（取 {@link com.knowledge.ai.prompt.enums.PromptStatus} 枚举名），
 * 同 template 同租户下至多一个 PUBLISHED（由 Service 层保证互斥）。
 * <p>content 使用 {@code {varName}} 命名占位符，由 Service 层
 * {@code render(content, variableMap)} 完成插值。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("prompt_version")
public class PromptVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属模板ID（prompt_template.id） */
    private Long templateId;

    /** 版本号（同模板内递增） */
    private Integer version;

    /** 模板内容（含 {var} 命名占位符） */
    private String content;

    /** 状态：DRAFT/PUBLISHED/ARCHIVED（存枚举名） */
    private String status;

    /** 版本说明 */
    private String remark;

    /** 创建人ID */
    private Long creatorId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
