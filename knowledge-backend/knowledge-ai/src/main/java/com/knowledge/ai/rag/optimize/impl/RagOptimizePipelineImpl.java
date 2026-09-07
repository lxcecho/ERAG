package com.knowledge.ai.rag.optimize.impl;

import com.knowledge.ai.config.AiProperties;
import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.rag.optimize.RagOptimizePipeline;
import com.knowledge.ai.rag.optimize.compress.ContextCompressor;
import com.knowledge.ai.rag.optimize.config.RagOptimizeProperties;
import com.knowledge.ai.rag.optimize.dto.AnswerEvaluation;
import com.knowledge.ai.rag.optimize.dto.CombinedQueryResult;
import com.knowledge.ai.rag.optimize.dto.QueryContext;
import com.knowledge.ai.rag.optimize.eval.AnswerEvaluator;
import com.knowledge.ai.rag.optimize.expansion.QueryExpander;
import com.knowledge.ai.rag.optimize.rewrite.QueryRewriter;
import com.knowledge.ai.search.ResultFusion;
import com.knowledge.ai.search.SearchService;
import com.knowledge.ai.service.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 优化流水线门面实现：编排五阶段子组件。
 * <p>三查询解耦：primaryQuery（改写后）喂 embedding → 向量路；keywordQuery（原问题+扩展词）喂 BM25；
 * rerankQuery（恒原始问题）喂 Cross-Encoder 精排。避免单一改写查询同时影响语义召回与精排基准。
 * <p>多查询：rewrite.multi-query-enabled 且有子查询时，主查询 + 各子查询分别 hybridRecall 召回，
 * rrfMulti 多路融合后用原始问题统一精排一次（避免 N 次精排且分数不可比）。
 * <p>best-effort：各阶段子组件已自含降级，本门面仅做编排，异常向上传递由调用方兜底。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagOptimizePipelineImpl implements RagOptimizePipeline {

    private final QueryRewriter queryRewriter;
    private final QueryExpander queryExpander;
    private final ContextCompressor contextCompressor;
    private final AnswerEvaluator answerEvaluator;
    private final SearchService searchService;
    private final EmbeddingService embeddingService;
    private final AiProperties aiProperties;
    private final RagOptimizeProperties optimizeProperties;

    @Override
    public QueryContext prepareQuery(String question, List<ChatMessage> history) {
        // 智能开关：简单问题（短、无歧义标记）跳过改写+扩展，直接用原问题检索，省 ~1-2s
        if (isSimpleQuery(question)) {
            log.info("[优化-查询] 简单问题跳过改写/扩展: {}", question);
            return QueryContext.ofOriginal(question);
        }

        // 合并改写+扩展为一次 LLM 调用（减少一次往返，延迟降低 ~50%）
        var combined = queryRewriter.combinedRewriteExpand(question, history);
        // rerankQuery 恒为原始问题，避免改写漂移影响精排基准
        return new QueryContext(combined.primaryQuery(), combined.keywordQuery(), question, combined.subQueries());
    }

    /**
     * 判断是否为简单问题：长度短且无歧义标记 → 跳过改写/扩展，省 ~1-2s LLM 延迟。
     * <p>规则：问题长度 < 10 字，且不含指代词、多意图连接词。
     */
    private boolean isSimpleQuery(String question) {
        if (question == null || question.length() > 10) {
            return false;
        }
        // 指代词 / 多意图标记 → 非简单问题
        String[] markers = {"它", "这个", "那个", "上述", "前面", "并且", "以及", "同时", "另外", "还有"};
        for (String m : markers) {
            if (question.contains(m)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<RetrievalResult> retrieve(QueryContext ctx, Embedding primaryEmbedding, Long kbId, int topK) {
        if (isMultiQueryActive(ctx)) {
            return retrieveMulti(ctx, primaryEmbedding, kbId, topK);
        }
        return searchService.hybridSearch(ctx, primaryEmbedding, kbId, topK);
    }

    /** 多查询检索：主查询 + 各子查询分别召回，rrfMulti 融合后统一精排 */
    private List<RetrievalResult> retrieveMulti(QueryContext ctx, Embedding primaryEmbedding, Long kbId, int topK) {
        int rrfK = aiProperties.getRag().getHybrid().getFusion().getRrfK();
        List<List<RetrievalResult>> lists = new ArrayList<>(ctx.subQueries().size() + 1);
        // 主查询一路（用三查询解耦 ctx）
        lists.add(searchService.hybridRecall(ctx, primaryEmbedding, kbId, topK));
        // 各子查询一路（独立检索，rerankQuery 留待最终统一精排）
        for (String sub : ctx.subQueries()) {
            try {
                Embedding subEmb = embeddingService.embed(sub);
                lists.add(searchService.hybridRecall(QueryContext.ofOriginal(sub), subEmb, kbId, topK));
            } catch (Exception e) {
                log.warn("[优化-检索] 子查询召回失败，跳过: sub={} err={}", sub, e.getMessage());
            }
        }
        List<RetrievalResult> fused = ResultFusion.rrfMulti(lists, rrfK);
        log.info("[优化-检索] 多查询 {} 路 → 融合 {} 条 → 精排 topN={}", lists.size(), fused.size(), topK);
        // 统一用原始问题精排一次
        return searchService.rerank(ctx.rerankQuery(), fused, topK);
    }

    /** 多查询是否生效：开关开启且子查询非空 */
    private boolean isMultiQueryActive(QueryContext ctx) {
        RagOptimizeProperties.Rewrite rw = optimizeProperties.getRewrite();
        return rw.isEnabled() && rw.isMultiQueryEnabled()
                && ctx.subQueries() != null && !ctx.subQueries().isEmpty();
    }

    @Override
    public List<RetrievalResult> compress(List<RetrievalResult> results, String question) {
        return contextCompressor.compress(results, question);
    }

    @Override
    public AnswerEvaluation evaluate(String question, List<RetrievalResult> results, String answer) {
        return answerEvaluator.evaluate(question, results, answer);
    }

    @Override
    public AnswerEvaluation evaluate(String question, List<RetrievalResult> results, String answer, boolean allowLlm) {
        return answerEvaluator.evaluate(question, results, answer, allowLlm);
    }
}
