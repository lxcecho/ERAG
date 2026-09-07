/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.search;

import com.knowledge.ai.config.AiProperties;
import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.search.impl.HybridSearchService;
import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 混合检索服务单元测试：验证总开关降级、双路融合、单路异常降级。
 * 通过 mock VectorSearchService / KeywordSearchService / RerankService 隔离底层依赖。
 */
@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

    @Mock
    private VectorSearchService vectorSearchService;
    @Mock
    private KeywordSearchService keywordSearchService;
    @Mock
    private RerankService rerankService;

    private AiProperties props;
    private HybridSearchService service;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        AiProperties.Hybrid h = props.getRag().getHybrid();
        h.setEnabled(true);
        h.setEsEnabled(true);
        h.setVectorTopK(20);
        h.setEsTopK(20);
        h.getFusion().setRrfK(60);
        service = new HybridSearchService(vectorSearchService, keywordSearchService, rerankService, props);
    }

    @Test
    void hybrid_总开关关闭时走纯向量不调关键词路() {
        props.getRag().getHybrid().setEnabled(false);
        Embedding emb = Embedding.from(new float[]{1.0f});
        when(vectorSearchService.search(any(), anyLong(), anyInt())).thenReturn(List.of());

        service.hybridSearch("问题", emb, 1L, 5);

        verify(keywordSearchService, never()).search(any(), anyLong(), anyInt());
        verify(rerankService, never()).rerank(any(), any(), anyInt());
    }

    @Test
    void hybrid_双路都有时执行RRF融合并rerank() {
        Embedding emb = Embedding.from(new float[]{1.0f});
        RetrievalResult v1 = new RetrievalResult("向量切片", 0.9, "s.pdf", 1L, 0, "c1", SearchConstants.SCORE_VECTOR);
        RetrievalResult k1 = new RetrievalResult("关键词切片", 5.0, "s.pdf", 1L, 1, "c2", SearchConstants.SCORE_BM25);
        when(vectorSearchService.search(any(), eq(1L), anyInt())).thenReturn(List.of(v1));
        when(keywordSearchService.search(eq("问题"), eq(1L), anyInt())).thenReturn(List.of(k1));
        // rerank 直接返回候选（noop 行为模拟）
        when(rerankService.rerank(eq("问题"), any(), eq(5))).thenAnswer(inv -> inv.getArgument(1));

        List<RetrievalResult> results = service.hybridSearch("问题", emb, 1L, 5);

        assertFalse(results.isEmpty());
        verify(rerankService).rerank(eq("问题"), any(), eq(5));
    }

    @Test
    void hybrid_ES路异常时降级纯向量不中断() {
        Embedding emb = Embedding.from(new float[]{1.0f});
        RetrievalResult v1 = new RetrievalResult("向量切片", 0.9, "s.pdf", 1L, 0, "c1", SearchConstants.SCORE_VECTOR);
        when(vectorSearchService.search(any(), eq(1L), anyInt())).thenReturn(List.of(v1));
        when(keywordSearchService.search(any(), anyLong(), anyInt())).thenThrow(new RuntimeException("ES 不可用"));
        when(rerankService.rerank(any(), any(), anyInt())).thenAnswer(inv -> inv.getArgument(1));

        List<RetrievalResult> results = service.hybridSearch("问题", emb, 1L, 5);

        assertFalse(results.isEmpty(), "ES 异常应降级向量路，仍有结果");
    }

    @Test
    void hybrid_向量路异常时降级关键词路() {
        Embedding emb = Embedding.from(new float[]{1.0f});
        RetrievalResult k1 = new RetrievalResult("关键词切片", 5.0, "s.pdf", 1L, 0, "c1", SearchConstants.SCORE_BM25);
        when(vectorSearchService.search(any(), anyLong(), anyInt())).thenThrow(new RuntimeException("Milvus 不可用"));
        when(keywordSearchService.search(any(), eq(1L), anyInt())).thenReturn(List.of(k1));
        when(rerankService.rerank(any(), any(), anyInt())).thenAnswer(inv -> inv.getArgument(1));

        List<RetrievalResult> results = service.hybridSearch("问题", emb, 1L, 5);

        assertFalse(results.isEmpty(), "向量异常应降级关键词路，仍有结果");
    }

    @Test
    void hybrid_双路均无命中时返回空() {
        Embedding emb = Embedding.from(new float[]{1.0f});
        when(vectorSearchService.search(any(), anyLong(), anyInt())).thenReturn(List.of());
        when(keywordSearchService.search(any(), anyLong(), anyInt())).thenReturn(List.of());

        List<RetrievalResult> results = service.hybridSearch("问题", emb, 1L, 5);

        assertTrue(results.isEmpty());
        verify(rerankService, never()).rerank(any(), any(), anyInt());
    }

    @Test
    void hybrid_esDisabled时不调关键词路() {
        props.getRag().getHybrid().setEsEnabled(false);
        Embedding emb = Embedding.from(new float[]{1.0f});
        RetrievalResult v1 = new RetrievalResult("向量切片", 0.9, "s.pdf", 1L, 0, "c1", SearchConstants.SCORE_VECTOR);
        when(vectorSearchService.search(any(), eq(1L), anyInt())).thenReturn(List.of(v1));
        when(rerankService.rerank(any(), any(), anyInt())).thenAnswer(inv -> inv.getArgument(1));

        service.hybridSearch("问题", emb, 1L, 5);

        verify(keywordSearchService, never()).search(any(), anyLong(), anyInt());
    }
}
