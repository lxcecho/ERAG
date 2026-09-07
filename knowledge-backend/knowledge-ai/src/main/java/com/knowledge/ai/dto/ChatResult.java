package com.knowledge.ai.dto;

import com.knowledge.ai.rag.optimize.dto.AnswerEvaluation;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * RAG 问答结果
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class ChatResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** LLM 回答 */
    private String answer;

    /** 命中的参考资料 */
    private List<RetrievalResult> sources;

    /** 会话ID（用于前端续接多轮对话与历史记录） */
    private Long sessionId;

    /** 回答质量评价（可选，ai.rag.optimize.eval 关闭或失败时为 null，non_null 序列化自动忽略） */
    private AnswerEvaluation evaluation;

    public ChatResult(String answer, List<RetrievalResult> sources) {
        this.answer = answer;
        this.sources = sources;
    }

    public ChatResult(String answer, List<RetrievalResult> sources, Long sessionId) {
        this.answer = answer;
        this.sources = sources;
        this.sessionId = sessionId;
    }

    public ChatResult(String answer, List<RetrievalResult> sources, Long sessionId, AnswerEvaluation evaluation) {
        this.answer = answer;
        this.sources = sources;
        this.sessionId = sessionId;
        this.evaluation = evaluation;
    }
}
