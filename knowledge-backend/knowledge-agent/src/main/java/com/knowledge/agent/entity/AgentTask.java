package com.knowledge.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 任务实体（一次工作流执行）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("agent_task")
public class AgentTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long userId;

    /** 知识库ID（检索范围） */
    private Long kbId;

    /** 关联会话ID（可空） */
    private Long sessionId;

    /** 用户原始目标 */
    private String goal;

    /** CREATED/EXECUTING/COMPLETED/FAILED/CANCELED */
    private String status;

    /** DAG 计划 JSON（PlannerAgent 产出） */
    private String plan;

    /** 最终报告（ReportAgent 产出） */
    private String result;

    private Integer stepCount;

    private Integer toolCallCount;

    private Integer tokenUsage;

    private String errorCode;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private LocalDateTime finishedTime;

    @TableLogic
    private Integer deleted;
}
