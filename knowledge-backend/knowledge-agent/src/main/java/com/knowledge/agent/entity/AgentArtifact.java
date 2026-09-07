package com.knowledge.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 产物实体（结构化产物，跨步引用：plan/evidences/analysis/report）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("agent_artifact")
public class AgentArtifact implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long taskId;

    private Long stepId;

    /** PLAN/EVIDENCES/ANALYSIS/REPORT */
    private String artifactType;

    /** JSON 产物 */
    private String payload;

    private LocalDateTime createTime;
}
