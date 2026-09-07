package com.knowledge.ai.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.knowledge.ai.config.AiProperties;
import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.health.AiCallHealthTracker;
import com.knowledge.ai.search.SearchConstants;
import com.knowledge.ai.service.MilvusService;
import com.knowledge.common.alert.AlertLevel;
import com.knowledge.common.alert.AlertService;
import com.knowledge.common.context.TenantContext;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * Milvus 服务实现：封装 LangChain4j {@link EmbeddingStore}。
 * <p>切片元数据（tenantId / kbId / documentId / source / chunkIndex）随向量一同存储，
 * 检索/删除时强制追加 tenantId 过滤（保证多租户隔离），再按 kbId / documentId 细化。
 * <p><b>生产能力</b>：检索路径 {@link #search} 加 {@link SentinelResource}（资源名 {@code milvus:search}），
 * 连续失败触发熔断后由 blockHandler 返回空列表（RAG 召回 0 结果降级，不抛异常打断问答）。
 * 调用成功/失败上报 {@link AiCallHealthTracker}（"milvus" 资源，供 {@code MilvusHealthIndicator} 被动判定健康）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusServiceImpl implements MilvusService {

    private static final String RESOURCE = "milvus:search";

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final AiProperties props;
    private final AiCallHealthTracker healthTracker;
    private final AlertService alertService;

    @Override
    public List<String> store(List<Embedding> embeddings, List<TextSegment> segments) {
        // 【多租户关键】存储时将当前 tenantId 写入每个切片 Metadata。
        // 即使上游组装切片时没带（只带 kbId / documentId），这里也强制补一次，避免"漏 metadata"导致 search 过滤为 0。
        Long tid = TenantContext.getTenantId();
        List<TextSegment> rewritten = segments;
        if (tid != null) {
            rewritten = new ArrayList<>(segments.size());
            String tidStr = String.valueOf(tid);
            for (TextSegment seg : segments) {
                Metadata md = seg.metadata();
                if (md.containsKey(SearchConstants.FIELD_TENANT_ID)) {
                    // 已显式设置：直接保留，避免重复包装（上游已经注入的场景）
                    rewritten.add(seg);
                } else {
                    Metadata next = md.copy().put(SearchConstants.FIELD_TENANT_ID, tidStr);
                    rewritten.add(TextSegment.from(seg.text(), next));
                }
            }
        }
        List<String> ids = embeddingStore.addAll(embeddings, rewritten);
        log.info("[Milvus存储] 写入 {} 条向量 tid={}", ids.size(), tid);
        return ids;
    }

    @Override
    @SentinelResource(value = RESOURCE, blockHandler = "searchBlockHandler")
    public List<RetrievalResult> search(Embedding queryEmbedding, Long kbId, int topK) {
        // 【多租户关键】先过滤 tenantId，再过滤 kbId（两层过滤，防跨租户召回其他租户同 ID 知识库的切片）
        Long tid = TenantContext.requiredTenantId();
        String tidStr = String.valueOf(tid);
        Filter filter = metadataKey(SearchConstants.FIELD_TENANT_ID).isEqualTo(tidStr)
                .and(metadataKey(SearchConstants.FIELD_KB_ID).isEqualTo(String.valueOf(kbId)));
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(props.getRag().getMinScore())
                .filter(filter)
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        log.info("[Milvus检索] kb={} tid={} 命中 {} 条 topK={}", kbId, tid, result.matches().size(), topK);
        healthTracker.recordSuccess("milvus");
        return result.matches().stream()
                .map(this::toRetrievalResult)
                .toList();
    }

    /**
     * Milvus 检索降级：熔断/限流时返回空列表（RAG 召回 0 结果降级，不抛异常打断问答）。
     * <p>注意：此处无法捕获 search 方法体内部异常（由上层 try-catch 处理），
     * blockHandler 仅在 Sentinel 主动拦截（熔断打开/限流）时触发。
     */
    public List<RetrievalResult> searchBlockHandler(Embedding queryEmbedding, Long kbId, int topK,
                                                    BlockException ex) {
        alertService.alert(AlertLevel.CRITICAL, RESOURCE, "Milvus检索降级",
                "kbId=" + kbId + ", blockType=" + ex.getClass().getSimpleName(), ex);
        // 记录失败用于健康检查统计（search 方法体正常返回时已 recordSuccess，此处补 failure）
        healthTracker.recordFailure("milvus");
        log.warn("[Milvus熔断降级] kb={} 返回空结果（blockType={}）", kbId, ex.getClass().getSimpleName());
        return List.of();
    }

    @Override
    public void deleteByDocument(Long documentId) {
        // 【多租户关键】删向量也要带 tenantId（防御式编程：避免其他租户同 documentId 误删）
        Long tid = TenantContext.getTenantId();
        Filter filter;
        if (tid != null) {
            filter = metadataKey(SearchConstants.FIELD_TENANT_ID).isEqualTo(String.valueOf(tid))
                    .and(metadataKey(SearchConstants.FIELD_DOCUMENT_ID).isEqualTo(String.valueOf(documentId)));
        } else {
            // 兼容：单租户模式下 tid 可能为空，或异步任务 @Async 发布事件线程继承了上下文但被清除
            filter = metadataKey(SearchConstants.FIELD_DOCUMENT_ID).isEqualTo(String.valueOf(documentId));
        }
        embeddingStore.removeAll(filter);
        log.info("[Milvus删除] documentId={} tid={} 的向量已清理", documentId, tid);
    }

    @Override
    public List<RetrievalResult> queryByDocument(Long documentId, int limit) {
        // 【多租户关键】强制 tenantId 过滤，避免跨租户读取同 documentId 的切片
        Long tid = TenantContext.requiredTenantId();
        Filter filter = metadataKey(SearchConstants.FIELD_TENANT_ID).isEqualTo(String.valueOf(tid))
                .and(metadataKey(SearchConstants.FIELD_DOCUMENT_ID).isEqualTo(String.valueOf(documentId)));
        // 单位向量探针：非零向量绕过 COSINE 度量零向量 NaN；minScore=-1.0 放弃得分过滤，
        // 使 documentId 过滤命中的切片全部返回（排序无意义，由调用方按 chunkIndex 重排）。
        int dim = Math.max(1, props.getMilvus().getDimension());
        float[] probe = new float[dim];
        probe[0] = 1.0f;
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(probe))
                .maxResults(Math.max(1, limit))
                .minScore(-1.0)
                .filter(filter)
                .build();
        try {
            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
            List<RetrievalResult> chunks = result.matches().stream()
                    .map(m -> toRetrievalResult(m, false))
                    .toList();
            // 按 chunkIndex 升序稳定排序（null 视为最大值排末尾）
            List<RetrievalResult> sorted = new ArrayList<>(chunks);
            sorted.sort((a, b) -> {
                int ia = a.getChunkIndex() == null ? Integer.MAX_VALUE : a.getChunkIndex();
                int ib = b.getChunkIndex() == null ? Integer.MAX_VALUE : b.getChunkIndex();
                return Integer.compare(ia, ib);
            });
            log.info("[Milvus按文档查询] documentId={} tid={} 命中 {} 条 limit={}",
                    documentId, tid, sorted.size(), limit);
            return sorted;
        } catch (Exception e) {
            // 查询失败降级为空列表（不阻断 DocumentTool，元数据仍可返回）
            log.warn("[Milvus按文档查询] documentId={} 查询失败，降级返回空: {}", documentId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 将 Milvus 匹配结果转为 RetrievalResult。
     * <p>Parent-Child：当切片 metadata 含 parentText 时，用父段文本替代小切片文本，
     * 使喂给 LLM 的上下文更完整（小召大上下文策略）。
     *
     * @param match Milvus 嵌入匹配
     * @return 检索结果（text 已替换为父段文本，若存在）
     */
    private RetrievalResult toRetrievalResult(EmbeddingMatch<TextSegment> match) {
        return toRetrievalResult(match, true);
    }

    /**
     * 将 Milvus 匹配结果转为 RetrievalResult（可控是否使用父段文本）。
     *
     * @param match        Milvus 嵌入匹配
     * @param useParentText true=检索场景（用父段喂 LLM）；false=按文档查询场景（保留小切片原文）
     */
    private RetrievalResult toRetrievalResult(EmbeddingMatch<TextSegment> match, boolean useParentText) {
        TextSegment segment = match.embedded();
        Metadata md = segment.metadata();
        String parentText = md.getString(SearchConstants.FIELD_PARENT_TEXT);
        String text = (useParentText && parentText != null && !parentText.isBlank())
                ? parentText : segment.text();
        return new RetrievalResult(
                text,
                match.score(),
                md.getString(SearchConstants.FIELD_SOURCE),
                parseLong(md.getString(SearchConstants.FIELD_DOCUMENT_ID)),
                parseInt(md.getString(SearchConstants.FIELD_CHUNK_INDEX)),
                md.getString(SearchConstants.FIELD_CHUNK_ID),
                SearchConstants.SCORE_VECTOR
        );
    }

    private Long parseLong(String val) {
        return val == null ? null : Long.valueOf(val);
    }

    private Integer parseInt(String val) {
        return val == null ? null : Integer.valueOf(val);
    }
}
