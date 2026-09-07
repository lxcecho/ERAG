package com.knowledge.agent.custom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 自定义 Agent 运行记录出参。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Schema(description = "自定义 Agent 运行记录")
public class AgentRunVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long userId;
    private Long agentId;
    private String agentName;
    private Long sessionId;
    private String inputType;
    private Long kbId;
    private String question;
    private String contextRef;
    private String result;
    /** 多步各步输出 JSON（multi 模式） */
    private String stepsResult;
    private Integer tokenUsage;
    private String status;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime finishedTime;
}
