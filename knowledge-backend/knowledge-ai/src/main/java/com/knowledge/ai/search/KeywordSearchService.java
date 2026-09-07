package com.knowledge.ai.search;

import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.ops.collector.InfraMetric;
import com.knowledge.ai.ops.collector.MetricResource;
import com.knowledge.ai.ops.trace.Span;
import com.knowledge.ai.ops.trace.SpanType;
import com.knowledge.ai.ops.trace.TraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 关键词检索服务：封装 Elasticsearch BM25 召回，作为混合检索的"词法路"。
 * <p>设计原因：与 {@link VectorSearchService} 对称，专攻精确关键词、专业名词、数字编号等
 * 纯向量检索薄弱的场景；ES 故障时由 {@link HybridSearchService} 自动降级为纯向量。
 * 检索结果 scoreType=bm25。
 * <p>{@code @InfraMetric} 埋点：AOP 自动计时 + 异常标记失败 → infra_metric（运维指标 #5）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordSearchService {

    private final ElasticsearchService elasticsearchService;
    private final TraceService traceService;

    /**
     * BM25 关键词检索。
     *
     * @param question 原始问题（分词后匹配 content）
     * @param kbId     知识库ID（term 过滤）
     * @param topK     返回条数
     * @return 按 BM25 降序的检索结果
     */
    @InfraMetric(resource = MetricResource.ES_SEARCH)
    public List<RetrievalResult> search(String question, Long kbId, int topK) {
        Span span = traceService.startSpan("es.search", SpanType.SEARCH);
        span.attribute("kbId", kbId).attribute("topK", topK);
        try {
            List<RetrievalResult> results = elasticsearchService.search(question, kbId, topK);
            span.attribute("hits", results.size());
            span.success();
            return results;
        } catch (RuntimeException e) {
            span.error(e);
            throw e;
        } finally {
            span.close();
        }
    }
}
