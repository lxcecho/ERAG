package com.knowledge.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 对话请求（支持普通对话与 RAG 对话两种模式）。
 * <p>useRag=false（默认）：普通对话，直接调用大模型，不检索知识库；kbId 可空（仅作会话归属）。
 * <p>useRag=true：RAG 对话，先检索知识库再回答；kbId 必填（限定检索范围）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class ChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "问题不能为空")
    private String question;

    /**
     * 知识库ID。
     * <p>RAG 模式下必填（限定检索范围，需 viewer 及以上权限）；
     * 普通模式下可空，会话归属记为 0（平台保留）。
     */
    private Long kbId;

    /** 会话ID（可选，为空则新建会话；多轮对话与历史记录依据此字段） */
    private Long sessionId;

    /**
     * 是否启用 RAG 检索。
     * <p>false（默认）= 普通对话，直接与大模型多轮对话；
     * true = RAG 对话，先检索知识库再基于资料回答。
     */
    private Boolean useRag = Boolean.FALSE;
}
