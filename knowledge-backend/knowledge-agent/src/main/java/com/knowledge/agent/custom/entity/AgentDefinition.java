package com.knowledge.agent.custom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 自定义 Agent 定义实体（用户创建的"助手"配置）。
 * <p>核心字段：系统提示词（含 {context}/{question} 占位符）、数据源模式（kb/content/log）、
 * 执行模型（single 单步流式 / multi 多步骤流程）、多步骤流程定义（steps JSON）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("agent_definition")
public class AgentDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    /** 创建者用户ID（权限归属：仅创建者可编辑/运行） */
    private Long userId;

    /** Agent 名称 */
    private String name;

    /** 功能描述 */
    private String description;

    /** 图标/头像 URL */
    private String avatar;

    /** 自定义系统提示词（支持 {context}/{question} 占位符） */
    private String systemPrompt;

    /** 数据源模式：kb 知识库 / content 自定义内容 / log 日志上传 */
    private String sourceMode;

    /** 绑定知识库ID（source_mode=kb 时必填） */
    private Long kbId;

    /** 执行模型：single 单步流式 / multi 多步骤流程 */
    private String execMode;

    /** 多步骤流程定义 JSON（exec_mode=multi 时必填） */
    private String steps;

    /** DRAFT/PUBLISHED/ARCHIVED */
    private String status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
