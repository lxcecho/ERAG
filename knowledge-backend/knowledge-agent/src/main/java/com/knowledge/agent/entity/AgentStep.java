package com.knowledge.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 步骤实体（每个 Agent 执行 = 1 行，审计/回放用）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("agent_step")
public class AgentStep implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long taskId;

    /** 执行顺序（从1开始） */
    private Integer stepIndex;

    /** PLANNER/KNOWLEDGE/ANALYSIS/REPORT */
    private String agentType;

    /** PENDING/RUNNING/SUCCESS/FAILED */
    private String status;

    private String inputSummary;

    private String outputSummary;

    private Integer tokenUsage;

    private Long durationMs;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
