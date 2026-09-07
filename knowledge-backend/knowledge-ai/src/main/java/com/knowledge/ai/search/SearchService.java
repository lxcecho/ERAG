package com.knowledge.ai.search;

import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.rag.optimize.dto.QueryContext;
import dev.langchain4j.data.embedding.Embedding;

import java.util.List;

/**
 * 检索服务门面：统一混合检索 / 纯向量 / 纯关键词三种检索入口。
 * <p>设计原因：取代 RagServiceImpl 原对 MilvusService.search 的直接调用，将"检索源选择"
 * 从 RAG 编排中解耦；上层只依赖此接口，hybrid 开关与降级策略全部内聚在实现类。
 *
 * @author: lxcechoo@gmail.com
 */
public interface SearchService {

    /**
     * 混合检索：ES BM25 + Milvus 向量双路召回 → RRF 融合 → Rerank 精排。
     * <p>hybrid.enabled=false 时等价于 {@link #vectorSearch}；任一路异常自动降级单路。
     *
     * @param question       原始问题（ES BM25 与 Rerank 使用）
     * @param queryEmbedding 问题向量（Milvus 语义检索使用）
     * @param kbId           知识库ID（双路均按此过滤）
     * @param topK           最终返回条数
     * @return 融合精排后的检索结果
     */
    List<RetrievalResult> hybridSearch(String question, Embedding queryEmbedding, Long kbId, int topK);

    /**
     * 三查询解耦混合检索（RAG 优化流水线专用）。
     * <p>不同检索环节用不同形式的查询，各取所长：
     * <ul>
     *   <li>{@code ctx.primaryQuery()} 的向量 → 向量路召回（语义精准）</li>
     *   <li>{@code ctx.keywordQuery()} → BM25 关键词路召回（同义词扩展提升召回率）</li>
     *   <li>双路 RRF 融合 → Rerank 用 {@code ctx.rerankQuery()}（恒原始问题，避免改写漂移）精排</li>
     * </ul>
     * hybrid.enabled=false 时退化为按 primaryQuery 向量检索 + rerankQuery 精排。
     *
     * @param ctx            检索前查询上下文（含三查询解耦）
     * @param queryEmbedding 主查询向量（由 primaryQuery 计算，调用方负责）
     * @param kbId           知识库ID
     * @param topK           最终返回条数
     * @return 融合精排后的检索结果
     */
    List<RetrievalResult> hybridSearch(QueryContext ctx, Embedding queryEmbedding, Long kbId, int topK);

    /**
     * 三查询解耦召回（向量 + BM25 + RRF 融合，<b>不含精排</b>）。
     * <p>多查询模式（multi-query）的单路召回：主查询与各子查询分别调用本方法得到候选列表，
     * 再由 {@link ResultFusion#rrfMulti} 多路融合，最后统一精排一次。
     * 不在此精排是为了避免对每个子查询各做一次 rerank（N 次精排且分数不可比）。
     *
     * @param ctx            查询上下文
     * @param queryEmbedding 该查询的向量
     * @param kbId           知识库ID
     * @param topK           召回条数（不含精排，应大于最终 topN 给融合留余量）
     * @return 融合后候选（未精排）
     */
    List<RetrievalResult> hybridRecall(QueryContext ctx, Embedding queryEmbedding, Long kbId, int topK);

    /**
     * 独立精排：对已融合的候选集用 Cross-Encoder 重打分并截断。
     * <p>多查询融合后统一精排一次的入口；单查询模式下由 {@link #hybridSearch(QueryContext, Embedding, Long, int)} 内部完成。
     *
     * @param question   精排基准查询（通常为原始问题）
     * @param candidates 融合后候选
     * @param topN       精排后保留条数
     * @return 精排结果
     */
    List<RetrievalResult> rerank(String question, List<RetrievalResult> candidates, int topN);

    /**
     * 纯向量检索（降级 / 向后兼容）。
     */
    List<RetrievalResult> vectorSearch(Embedding queryEmbedding, Long kbId, int topK);

    /**
     * 纯关键词检索（管理 / 调试用，如精确查某编号所在文档）。
     */
    List<RetrievalResult> keywordSearch(String question, Long kbId, int topK);
}
