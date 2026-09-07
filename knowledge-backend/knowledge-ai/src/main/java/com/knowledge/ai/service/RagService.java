package com.knowledge.ai.service;

import com.knowledge.ai.dto.ChatResult;
import com.knowledge.ai.dto.IngestResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG 编排服务：串联"解析→切片→Embedding→存储"与"检索→组装→回答"两条主流程。
 * <p>设计原因：作为门面（Facade）协调 LLMService / EmbeddingService / MilvusService /
 * PromptTemplateService / ChatContextService，上层（事件监听器 / Controller）只依赖此接口。
 *
 * @author: lxcechoo@gmail.com
 */
public interface RagService {

    /**
     * 文档入库流程：解析文档 → 文本切片 → 向量化 → 存入 Milvus。
     * <p>纯 RAG 流水线，不负责解析任务状态回写（由调用方编排）。
     *
     * @param documentId 文档ID
     * @return 入库结果（切片数 / 向量数）
     */
    IngestResult ingest(Long documentId);

    /**
     * 同步问答流程：问题向量化 → Milvus 相似搜索 → 组装 Prompt（DB模板优先）→ LLM 回答。
     * <p>同时持久化用户问题与回答到聊天记录，并带入历史消息实现多轮对话。
     *
     * @param question  用户问题
     * @param kbId      知识库ID（限定检索范围，需 viewer 及以上权限）
     * @param sessionId 会话ID（为空则新建会话）
     * @return 回答、引用来源、会话ID
     */
    ChatResult ask(String question, Long kbId, Long sessionId);

    /**
     * 流式问答流程：检索后将系统提示与历史组装，逐 token 通过 SSE 推送至前端。
     * <p>事件类型：sources（引用来源）→ token（逐段回答，可多次）→ done（完成，携带 sessionId）/ error。
     *
     * @param question  用户问题
     * @param kbId      知识库ID
     * @param sessionId 会话ID（为空则新建）
     * @param emitter   SSE 发射器
     */
    void askStream(String question, Long kbId, Long sessionId, SseEmitter emitter);

    /**
     * 普通对话（同步）：不检索知识库，直接调用大模型多轮对话。
     * <p>加载会话历史保证多轮上下文连续，回答后持久化问答记录。
     *
     * @param question  用户问题
     * @param kbId      知识库ID（可空，仅作会话归属；为空记 0）
     * @param sessionId 会话ID（为空则新建）
     * @return 回答、会话ID（sources 为空列表）
     */
    ChatResult plainChat(String question, Long kbId, Long sessionId);

    /**
     * 普通对话流式：不检索知识库，直接调用大模型流式多轮对话。
     * <p>事件类型：token（逐段回答，可多次）→ done（完成，携带 sessionId）/ error。
     * 不推送 sources 事件（无检索来源）。
     *
     * @param question  用户问题
     * @param kbId      知识库ID（可空）
     * @param sessionId 会话ID（为空则新建）
     * @param emitter   SSE 发射器
     */
    void plainChatStream(String question, Long kbId, Long sessionId, SseEmitter emitter);
}
