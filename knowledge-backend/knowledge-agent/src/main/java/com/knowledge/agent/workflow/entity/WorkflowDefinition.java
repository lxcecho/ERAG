package com.knowledge.agent.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Workflow 流程定义实体（模板，一对 code+version 不可变）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("workflow_definition")
public class WorkflowDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    /** 流程编码（如 policy_analysis） */
    private String code;

    private String name;

    /** 版本号（同 code 递增） */
    private Integer version;

    /** ENABLED/DISABLED */
    private String status;

    /** 流程定义 JSON（WorkflowDefinitionModel 序列化） */
    private String definition;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
