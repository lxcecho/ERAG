package com.knowledge.agent.custom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 自定义 Agent 定义出参。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Schema(description = "自定义 Agent 定义")
public class AgentDefinitionVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long userId;
    private String name;
    private String description;
    private String avatar;
    private String systemPrompt;
    private String sourceMode;
    private Long kbId;
    private String kbName;
    private String execMode;
    private String steps;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
