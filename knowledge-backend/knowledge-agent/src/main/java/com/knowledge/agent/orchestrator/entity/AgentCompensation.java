package com.knowledge.agent.orchestrator.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 编排补偿记录实体（回滚时每个已 SUCCESS 节点的补偿动作 = 1 行）。
 * <p>对应表 agent_compensation。best-effort：status=FAILED 不阻断回滚链。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("agent_compensation")
public class AgentCompensation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long taskId;

    private String nodeId;

    /** 对应节点执行记录ID（agent_node_run.id） */
    private Long runId;

    private String description;

    /** PENDING/RUNNING/SUCCESS/FAILED */
    private String status;

    private String errorMsg;

    private Long durationMs;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createTime;
}
