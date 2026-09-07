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
 * Prompt 模板元数据实体（三表拆分后仅承载元数据）。
 * <p>一个 {@code (tenant_id, prompt_code)} 唯一对应一行元数据；
 * 版本内容见 {@link PromptVersion}，变量定义见 {@link PromptVariable}。
 * <p>{@code type} 取 system/rag/agent；{@code tenant_id=0} 表示平台预置，对所有租户可见。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("prompt_template")
public class PromptTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属租户ID（0=平台预置，对所有租户可见） */
    private Long tenantId;

    /** Prompt 逻辑编码（同租户内唯一，如 rag_system_prompt） */
    private String promptCode;

    /** 模板名称 */
    private String name;

    /** 类型 system/rag/agent */
    private String type;

    /** 创建人ID */
    private Long creatorId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
