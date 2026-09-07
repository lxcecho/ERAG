package com.knowledge.ai.rag.optimize;

import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.rag.optimize.dto.AnswerEvaluation;
import com.knowledge.ai.rag.optimize.dto.QueryContext;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * RAG 优化流水线门面：将五个优化阶段封装为统一入口，保持 {@code RagServiceImpl} 编排简洁。
 * <p>五阶段（均 best-effort，失败不阻断主流程）：
 * <ol>
 *   <li>{@link #prepareQuery} —— Query Rewrite（指代消解 + 多查询）+ Query Expansion（同义词扩展）</li>
 *   <li>{@link #retrieve} —— 三查询解耦检索 / 多查询召回 + RRF 多路融合 + Rerank 精排</li>
 *   <li>{@link #compress} —— Context 压缩（去重 + 预算截断 + 可选 LLM 抽取）</li>
 *   <li>（LLM 生成回答 —— 由 RagServiceImpl 负责，非本门面职责）</li>
 *   <li>{@link #evaluate} —— Answer 评价（忠实度 / 相关度）</li>
 * </ol>
 * <p>调用方只需按 prepareQuery → retrieve → [权限/治理过滤] → compress → [LLM] → evaluate 顺序编排，
 * 各阶段开关与降级策略全部内聚在门面与子组件中。
 *
 * @author: lxcechoo@gmail.com
 */
public interface RagOptimizePipeline {

    /**
     * 阶段 1：检索前查询准备（改写 + 扩展）。
     *
     * @param question 用户原始问题
     * @param history  对话历史（指代消解依据）
     * @return 查询上下文（含三查询解耦 + 多查询子问题；各阶段关闭时回退原问题）
     */
    QueryContext prepareQuery(String question, List<ChatMessage> history);

    /**
     * 阶段 2：优化检索（三查询解耦 / 多查询融合 + 精排）。
     * <p>多查询关闭或无子查询时走单查询三解耦检索；否则多路召回 + rrfMulti 融合 + 统一精排。
     *
     * @param ctx             查询上下文
     * @param primaryEmbedding 主查询向量（由 primaryQuery 计算，调用方负责）
     * @param kbId            知识库ID
     * @param topK            精排后返回条数（应留余量给后续权限过滤）
     * @return 精排后检索结果
     */
    List<RetrievalResult> retrieve(QueryContext ctx, Embedding primaryEmbedding, Long kbId, int topK);

    /**
     * 阶段 3：上下文压缩。
     *
     * @param results  检索结果（按相关性降序，已完成权限/治理过滤）
     * @param question 原始问题（LLM 抽取压缩时聚焦用）
     * @return 压缩后结果
     */
    List<RetrievalResult> compress(List<RetrievalResult> results, String question);

    /**
     * 阶段 5：回答评价。
     *
     * @param question 原始问题
     * @param results  喂给 LLM 的检索资料
     * @param answer   LLM 回答
     * @return 评价结果；开关关闭或失败返回 null
     */
    AnswerEvaluation evaluate(String question, List<RetrievalResult> results, String answer);

    /**
     * 阶段 5：回答评价（可控制是否允许 LLM 评价）。
     * <p>流式场景传 {@code allowLlm=false}，仅跑规则版，避免 LLM 评价阻塞 SSE done 事件。
     *
     * @param allowLlm 是否允许调用 LLM 评价（false=仅规则版）
     */
    AnswerEvaluation evaluate(String question, List<RetrievalResult> results, String answer, boolean allowLlm);
}
