package com.knowledge.ai.search.impl;

import com.knowledge.ai.config.AiProperties;
import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.rag.optimize.dto.QueryContext;
import com.knowledge.ai.search.KeywordSearchService;
import com.knowledge.ai.search.RerankService;
import com.knowledge.ai.search.ResultFusion;
import com.knowledge.ai.search.SearchService;
import com.knowledge.ai.search.VectorSearchService;
import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 混合检索服务实现：编排向量路 + 词法路 + RRF 融合 + Rerank 精排。
 * <p>降级策略：
 * <ul>
 *   <li>hybrid.enabled=false → 纯向量（零影响回退原架构）</li>
 *   <li>向量路异常 → 仅用关键词路</li>
 *   <li>ES 路异常或 es-enabled=false → 仅用向量路</li>
 *   <li>两路均空 → 返回空（上层走"无资料"提示）</li>
 * </ul>
 * 任一路异常都不阻断整体检索，保证可用性。
 * <p>检索与精排解耦：{@link #recall} 只做双路召回 + RRF 融合（不含精排），
 * 供多查询场景单路召回后多路融合再统一精排；{@link #hybridSearch} 在 recall 基础上追加精排。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService implements SearchService {

    private final VectorSearchService vectorSearchService;
    private final KeywordSearchService keywordSearchService;
    private final RerankService rerankService;
    private final AiProperties props;

    @Override
    @Cacheable(value = "search", key = "'hybrid:' + #question + ':' + #kbId + ':' + #topK", unless = "#result.isEmpty()")
    public List<RetrievalResult> hybridSearch(String question, Embedding queryEmbedding, Long kbId, int topK) {
        AiProperties.Hybrid h = props.getRag().getHybrid();
        if (!h.isEnabled()) {
            return vectorSearch(queryEmbedding, kbId, topK);
        }
        List<RetrievalResult> candidates = recall(question, queryEmbedding, kbId);
        // 双路均空 → 直接返回空，跳过精排（与文档约定一致，避免无意义 rerank 调用）
        if (candidates.isEmpty()) {
            return List.of();
        }
        return rerankService.rerank(question, candidates, topK);
    }

    @Override
    public List<RetrievalResult> hybridSearch(QueryContext ctx, Embedding queryEmbedding, Long kbId, int topK) {
        AiProperties.Hybrid h = props.getRag().getHybrid();
        if (!h.isEnabled()) {
            // hybrid 关闭：退化为按 primaryQuery 向量检索 + rerankQuery 精排（noop 时等价截断）
            List<RetrievalResult> v = vectorSearch(queryEmbedding, kbId, topK);
            return rerankService.rerank(ctx.rerankQuery(), v, topK);
        }
        // 三查询解耦：keywordQuery 喂 BM25，rerankQuery 喂 Cross-Encoder
        List<RetrievalResult> candidates = recall(ctx.keywordQuery(), queryEmbedding, kbId);
        // 双路均空 → 直接返回空，跳过精排
        if (candidates.isEmpty()) {
            return List.of();
        }
        return rerankService.rerank(ctx.rerankQuery(), candidates, topK);
    }

    @Override
    public List<RetrievalResult> hybridRecall(QueryContext ctx, Embedding queryEmbedding, Long kbId, int topK) {
        AiProperties.Hybrid h = props.getRag().getHybrid();
        if (!h.isEnabled()) {
            return vectorSearch(queryEmbedding, kbId, topK);
        }
        return recall(ctx.keywordQuery(), queryEmbedding, kbId);
    }

    @Override
    public List<RetrievalResult> rerank(String question, List<RetrievalResult> candidates, int topN) {
        return rerankService.rerank(question, candidates, topN);
    }

    /**
     * 双路召回 + RRF 融合（不含精排）。
     * <p>向量路用 queryEmbedding，词法路用 keywordQuery；两路各自容错，任一路异常不阻断另一路。
     *
     * @param keywordQuery   BM25 关键词查询（三查询解耦时为扩展后查询，普通模式为原问题）
     * @param queryEmbedding 向量路查询向量
     * @param kbId           知识库ID
     * @return 融合后候选（未精排）；双路均空返回空列表
     */
    private List<RetrievalResult> recall(String keywordQuery, Embedding queryEmbedding, Long kbId) {
        AiProperties.Hybrid h = props.getRag().getHybrid();

        List<RetrievalResult> vectorResults = List.of();
        try {
            vectorResults = vectorSearch(queryEmbedding, kbId, h.getVectorTopK());
        } catch (Exception e) {
            log.warn("[混合检索] 向量路异常，降级仅用关键词路: {}", e.getMessage());
        }

        List<RetrievalResult> keywordResults = List.of();
        if (h.isEsEnabled()) {
            try {
                keywordResults = keywordSearch(keywordQuery, kbId, h.getEsTopK());
            } catch (Exception e) {
                log.warn("[混合检索] ES路异常，降级仅用向量路: {}", e.getMessage());
            }
        }

        if (keywordResults.isEmpty() && vectorResults.isEmpty()) {
            log.info("[混合检索] kb={} 双路均无命中", kbId);
            return List.of();
        }
        if (keywordResults.isEmpty()) {
            return vectorResults;
        }
        if (vectorResults.isEmpty()) {
            return keywordResults;
        }
        List<RetrievalResult> fused = ResultFusion.rrf(vectorResults, keywordResults, h.getFusion().getRrfK());
        log.info("[混合检索] kb={} 向量{}条 关键词{}条 融合{}条",
                kbId, vectorResults.size(), keywordResults.size(), fused.size());
        return fused;
    }

    @Override
    public List<RetrievalResult> vectorSearch(Embedding queryEmbedding, Long kbId, int topK) {
        return vectorSearchService.search(queryEmbedding, kbId, topK);
    }

    @Override
    public List<RetrievalResult> keywordSearch(String question, Long kbId, int topK) {
        return keywordSearchService.search(question, kbId, topK);
    }
}
