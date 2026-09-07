package com.knowledge.agent.custom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 自定义 Agent 运行记录实体（一次对话 / 一次流程执行 = 1 行）。
 * <p>执行记录属审计数据，不加软删字段（对齐 agent_step / node_run 约定）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("agent_run")
public class AgentRun implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    /** 发起用户ID */
    private Long userId;

    /** 关联 agent_definition.id */
    private Long agentId;

    /** 会话ID（跨轮记忆，同一对话的多轮 run 共享） */
    private Long sessionId;

    /** 输入类型：kb / content / log */
    private String inputType;

    /** 实际检索知识库ID（input_type=kb） */
    private Long kbId;

    /** 用户问题/分析诉求 */
    private String question;

    /** content 原文摘要或 log 文件引用（fileRef） */
    private String contextRef;

    /** 最终输出（single=回答；multi=末步报告） */
    private String result;

    /** 多步各步输出 JSON（multi 模式） */
    private String stepsResult;

    /** token 总消耗 */
    private Integer tokenUsage;

    /** CREATED/RUNNING/COMPLETED/FAILED/CANCELED */
    private String status;

    /** 失败原因 */
    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private LocalDateTime finishedTime;
}
