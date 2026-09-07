package com.knowledge.agent.custom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 自定义 Agent 运行请求（单步流式 chat / 多步异步 run 共用）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Schema(description = "自定义 Agent 运行请求")
public class ChatStartRequest {

    @Schema(description = "用户问题/分析诉求", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "问题不能为空")
    private String question;

    /** 会话ID（跨轮记忆，首轮为空由后端生成，SSE 通过 session 事件回传；后续轮次必须携带） */
    @Schema(description = "会话ID（首轮可空，后端生成后经 session 事件回传）")
    private Long sessionId;

    /** 输入类型：kb / content / log / plain（默认按定义 source_mode；plain=纯模型回答不注入上下文） */
    @Schema(description = "输入类型 kb/content/log/plain，默认按定义 sourceMode；plain 表示关闭知识库检索的纯模型回答")
    private String inputType;

    /** 实际检索知识库ID（inputType=kb 时，可覆盖定义绑定） */
    @Schema(description = "知识库ID（inputType=kb 时使用，默认取定义绑定）")
    private Long kbId;

    /** 自定义内容文本（inputType=content 时必填） */
    @Schema(description = "自定义内容文本（inputType=content 时必填）")
    private String content;

    /** 日志文件引用（inputType=log 时必填，来自 /custom-agent/upload） */
    @Schema(description = "日志文件引用（inputType=log 时必填）")
    private String fileRef;
}
