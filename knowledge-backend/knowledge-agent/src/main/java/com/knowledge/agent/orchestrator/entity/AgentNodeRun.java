package com.knowledge.agent.orchestrator.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 编排节点执行记录实体（每个节点每次尝试 = 1 行，审计/回放/重试依据）。
 * <p>对应表 agent_node_run，状态由 {@link com.knowledge.agent.orchestrator.NodeRunStatus} 管理。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("agent_node_run")
public class AgentNodeRun implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long taskId;

    private String nodeId;

    private String nodeName;

    /** PLANNER/KNOWLEDGE/ANALYSIS/REPORT */
    private String agentType;

    /** 执行序号（从1开始，同任务递增） */
    private Integer runIndex;

    /** 重试尝试次数（第几次执行该节点） */
    private Integer attempt;

    /** PENDING/RUNNING/SUCCESS/RETRYING/FAILED/TIMEOUT/SKIPPED/CANCELED */
    private String status;

    private String inputSummary;

    private String outputSummary;

    private Long timeoutMs;

    private Integer tokenUsage;

    private Long durationMs;

    private String errorMsg;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createTime;
}
