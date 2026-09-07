package com.knowledge.agent.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Workflow 节点执行记录实体（每个节点每次执行 = 1 行，审计/回放/重试依据）。
 * <p>HUMAN 节点：{@link #approved}/{@link #approverUserId}/{@link #approvalComment} 记录审批结果；
 * 失败重试：{@link #attempt} 递增，同节点可多行。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("workflow_node_run")
public class WorkflowNodeRun implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long taskId;

    private String nodeId;

    private String nodeName;

    /** START/TOOL/LLM/HUMAN/END */
    private String nodeType;

    /** 执行序号（从1开始，同任务递增） */
    private Integer runIndex;

    /** 重试尝试次数（第几次执行该节点） */
    private Integer attempt;

    /** PENDING/RUNNING/SUCCESS/FAILED/WAITING_HUMAN/SKIPPED/CANCELED */
    private String status;

    private String inputJson;

    private String outputJson;

    /** 审批结果（HUMAN 节点）：1 通过 / 0 驳回 */
    private Integer approved;

    private Long approverUserId;

    private String approvalComment;

    private Integer tokenUsage;

    private Long durationMs;

    private String errorMsg;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createTime;
}
