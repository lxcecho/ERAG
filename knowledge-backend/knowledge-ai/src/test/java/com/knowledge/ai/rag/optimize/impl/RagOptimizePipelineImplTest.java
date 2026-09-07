/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.rag.optimize.impl;

import com.knowledge.ai.config.AiProperties;
import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.rag.optimize.compress.ContextCompressor;
import com.knowledge.ai.rag.optimize.config.RagOptimizeProperties;
import com.knowledge.ai.rag.optimize.dto.AnswerEvaluation;
import com.knowledge.ai.rag.optimize.dto.QueryContext;
import com.knowledge.ai.rag.optimize.eval.AnswerEvaluator;
import com.knowledge.ai.rag.optimize.expansion.QueryExpander;
import com.knowledge.ai.rag.optimize.rewrite.QueryRewriter;
import com.knowledge.ai.rag.optimize.dto.RewriteResult;
import com.knowledge.ai.search.SearchService;
import com.knowledge.ai.service.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RAG 优化流水线门面单元测试：编排验证（prepareQuery 构建 / 单查询 vs 多查询检索 / 压缩评价委托）。
 */
class RagOptimizePipelineImplTest {

    private QueryRewriter queryRewriter;
    private QueryExpander queryExpander;
    private ContextCompressor contextCompressor;
    private AnswerEvaluator answerEvaluator;
    private SearchService searchService;
    private EmbeddingService embeddingService;
    private AiProperties aiProperties;
    private RagOptimizeProperties optimizeProperties;
    private RagOptimizePipelineImpl pipeline;

    @BeforeEach
    void setUp() {
        queryRewriter = mock(QueryRewriter.class);
        queryExpander = mock(QueryExpander.class);
        contextCompressor = mock(ContextCompressor.class);
        answerEvaluator = mock(AnswerEvaluator.class);
        searchService = mock(SearchService.class);
        embeddingService = mock(EmbeddingService.class);
        aiProperties = new AiProperties();
        optimizeProperties = new RagOptimizeProperties();
        pipeline = new RagOptimizePipelineImpl(queryRewriter, queryExpander, contextCompressor,
                answerEvaluator, searchService, embeddingService, aiProperties, optimizeProperties);
    }

    @Test
    void prepareQuery_构建三查询解耦上下文() {
        String question = "它是什么";
        when(queryRewriter.rewrite(eq(question), any())).thenReturn(
                new RewriteResult("Spring Boot 是什么", List.of("子查询1")));
        when(queryExpander.expand("Spring Boot 是什么")).thenReturn("Spring Boot 是什么 同义词");

        QueryContext ctx = pipeline.prepareQuery(question, List.of());

        assertEquals("Spring Boot 是什么", ctx.primaryQuery());
        assertEquals("Spring Boot 是什么 同义词", ctx.keywordQuery());
        assertEquals(question, ctx.rerankQuery(), "rerankQuery 应恒为原始问题");
        assertEquals(1, ctx.subQueries().size());
    }

    @Test
    void retrieve_无子查询走单查询三解耦检索() {
        QueryContext ctx = new QueryContext("主", "主 关键词", "原始问题", List.of());
        Embedding emb = mock(Embedding.class);
        List<RetrievalResult> expected = List.of(result("c1"));
        when(searchService.hybridSearch(eq(ctx), eq(emb), anyLong(), anyInt())).thenReturn(expected);

        List<RetrievalResult> out = pipeline.retrieve(ctx, emb, 1L, 5);

        assertSame(expected, out);
        verify(searchService).hybridSearch(eq(ctx), eq(emb), eq(1L), eq(5));
        verify(searchService, never()).hybridRecall(any(), any(), anyLong(), anyInt());
        verify(searchService, never()).rerank(anyString(), any(), anyInt());
    }

    @Test
    void retrieve_多查询走多路召回融合后统一精排() {
        optimizeProperties.getRewrite().setEnabled(true);
        optimizeProperties.getRewrite().setMultiQueryEnabled(true);
        QueryContext ctx = new QueryContext("主", "主 关键词", "原始问题", List.of("子1", "子2"));
        Embedding primaryEmb = mock(Embedding.class);
        when(embeddingService.embed(anyString())).thenReturn(mock(Embedding.class));
        when(searchService.hybridRecall(any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(result("c1")))
                .thenReturn(List.of(result("c2")))
                .thenReturn(List.of(result("c3")));
        List<RetrievalResult> ranked = List.of(result("c1"), result("c2"));
        when(searchService.rerank(eq("原始问题"), any(), eq(5))).thenReturn(ranked);

        List<RetrievalResult> out = pipeline.retrieve(ctx, primaryEmb, 1L, 5);

        assertSame(ranked, out);
        // 主查询 1 路 + 2 子查询 = 3 次 hybridRecall
        verify(searchService, times(3)).hybridRecall(any(), any(), anyLong(), anyInt());
        // 统一精排一次，基准为原始问题
        verify(searchService).rerank(eq("原始问题"), any(), eq(5));
        verify(searchService, never()).hybridSearch(any(QueryContext.class), any(Embedding.class), anyLong(), anyInt());
    }

    @Test
    void compress_委托给压缩器() {
        List<RetrievalResult> input = List.of(result("c1"));
        List<RetrievalResult> expected = List.of(result("c1"));
        when(contextCompressor.compress(input, "问题")).thenReturn(expected);
        assertSame(expected, pipeline.compress(input, "问题"));
    }

    @Test
    void evaluate_委托给评价器() {
        AnswerEvaluation eval = new AnswerEvaluation(0.9, 0.8, 0.86, "ok", "RULE");
        when(answerEvaluator.evaluate("问题", List.of(), "回答")).thenReturn(eval);
        assertSame(eval, pipeline.evaluate("问题", List.of(), "回答"));
    }

    @Test
    void evaluate_allowLlm委托给评价器() {
        AnswerEvaluation eval = new AnswerEvaluation(0.5, 0.5, 0.5, "rule", "RULE");
        when(answerEvaluator.evaluate("问题", List.of(), "回答", false)).thenReturn(eval);
        assertSame(eval, pipeline.evaluate("问题", List.of(), "回答", false));
    }

    private RetrievalResult result(String chunkId) {
        return new RetrievalResult("文本", 1.0, "src", 1L, 0, chunkId, "fused");
    }
}
