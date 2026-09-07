package com.knowledge.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Agent 任务启动请求。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Schema(description = "Agent 任务启动请求")
public class AgentStartRequest {

    /** 知识库ID（检索范围） */
    @Schema(description = "知识库ID（检索范围）", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "知识库ID不能为空")
    private Long kbId;

    /** 用户目标（如：分析2025销售政策相比2024的变化） */
    @Schema(description = "用户目标（自然语言描述分析诉求）", example = "分析2025销售政策相比2024的变化",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "任务目标不能为空")
    private String goal;

    /** 关联会话ID（可空，便于关联对话上下文） */
    @Schema(description = "关联会话ID（可空，便于关联对话上下文）", example = "2001")
    private Long sessionId;
}
