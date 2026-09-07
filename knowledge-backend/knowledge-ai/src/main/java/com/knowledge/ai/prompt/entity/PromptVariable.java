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
 * Prompt 变量定义实体（三表拆分后承载变量元信息，模板级）。
 * <p>关联 {@link PromptTemplate#getId()}，同 template_id 下 var_name 唯一。
 * <p>{@code varName} 对应模板内容中的 {@code {varName}} 占位符；
 * {@code required} 为 1 表示渲染时必填，{@code defaultValue} 为未传值时的兜底。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("prompt_variable")
public class PromptVariable implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属模板ID（prompt_template.id） */
    private Long templateId;

    /** 变量名（对应 {varName} 占位符） */
    private String varName;

    /** 变量描述 */
    private String description;

    /** 是否必填 0否 1是 */
    private Integer required;

    /** 默认值 */
    private String defaultValue;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
