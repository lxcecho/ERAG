package com.knowledge.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 消息实体（LLM/Tool 完整 IO，审计与回放用）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("agent_message")
public class AgentMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long taskId;

    private Long stepId;

    /** system/user/assistant/tool */
    private String role;

    private String content;

    private String toolName;

    private Integer tokenUsage;

    private LocalDateTime createTime;
}
