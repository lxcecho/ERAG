package com.knowledge.ai.search;

import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.ops.collector.InfraMetric;
import com.knowledge.ai.ops.collector.MetricResource;
import com.knowledge.ai.ops.trace.Span;
import com.knowledge.ai.ops.trace.SpanType;
import com.knowledge.ai.ops.trace.TraceService;
import com.knowledge.ai.service.MilvusService;
import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量检索服务：封装 Milvus 语义召回，作为混合检索的"向量路"。
 * <p>设计原因：与 {@link KeywordSearchService} 对称，让 {@link HybridSearchService} 以统一方式
 * 编排两路；MilvusService 零改动，仅作为本服务的下游。检索结果 scoreType=vector。
 * <p>{@code @InfraMetric} 埋点：AOP 自动计时 + 异常标记失败 → infra_metric（运维指标 #4）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final MilvusService milvusService;
    private final TraceService traceService;

    /**
     * 向量相似度检索。
     *
     * @param queryEmbedding 查询向量
     * @param kbId           知识库ID（元数据过滤）
     * @param topK           返回条数
     * @return 按相似度降序的检索结果
     */
    @InfraMetric(resource = MetricResource.MILVUS_SEARCH)
    public List<RetrievalResult> search(Embedding queryEmbedding, Long kbId, int topK) {
        Span span = traceService.startSpan("milvus.search", SpanType.SEARCH);
        span.attribute("kbId", kbId).attribute("topK", topK);
        try {
            List<RetrievalResult> results = milvusService.search(queryEmbedding, kbId, topK);
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
