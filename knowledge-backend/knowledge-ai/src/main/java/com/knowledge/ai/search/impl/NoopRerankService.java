package com.knowledge.ai.search.impl;

import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.search.RerankService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 空精排实现：不调用外部模型，直接按融合顺序截断 Top-N。
 * <p>当 ai.rag.hybrid.rerank.type=noop（默认，或配置缺失）时生效。
 * <p>后续可扩展 HttpRerankService（type=http）对接 bge-reranker / Cohere / 通义 gte-rerank，
 * 二者按 type 互斥装配。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "ai.rag.hybrid.rerank", name = "type", havingValue = "noop", matchIfMissing = true)
public class NoopRerankService implements RerankService {

    @Override
    public List<RetrievalResult> rerank(String question, List<RetrievalResult> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        log.debug("[Rerank] noop 直接截断 topN={} 候选数={}", topN, candidates.size());
        return candidates.size() <= topN ? candidates : candidates.subList(0, topN);
    }
}
