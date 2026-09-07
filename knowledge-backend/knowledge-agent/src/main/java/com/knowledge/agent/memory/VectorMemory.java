package com.knowledge.agent.memory;

import com.knowledge.agent.memory.dto.MemoryRecall;
import com.knowledge.common.context.TenantContext;
import com.knowledge.ai.service.EmbeddingService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 向量记忆：将摘要/长期事实向量化后存入独立 Milvus 集合 {@code agent_memory_vec}，支持按用户语义召回。
 * <p>
 * 与 {@link com.knowledge.ai.service.impl.MilvusServiceImpl}（文档库）物理隔离：
 * <ul>
 *   <li>集合不同：本类用 {@code agent_memory_vec}，文档库用 {@code knowledge_chunks}；</li>
 *   <li>元数据不同：本类携带 tenantId/userId/memoryType/memoryId/sourceSessionId，
 *       召回时按 tenantId + userId 过滤（跨会话语义召回，召回该用户的全部历史记忆）。</li>
 * </ul>
 * <p>
 * 多租户：存储时强制写入 tenantId（与 MilvusServiceImpl 一致），检索时强制 tenantId + userId 双过滤，
 * 防跨租户/跨用户召回。minScore 取 {@link MemoryProperties#getVectorMinScore()}。
 * <p>
 * best-effort：索引失败仅告警不抛（避免阻断事实抽取主链路）；召回失败降级返回空列表。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
public class VectorMemory {

    /** Milvus 元数据 key（与文档库元数据 key 风格对齐，但本集合独立） */
    public static final String FIELD_TENANT_ID = "tenantId";
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_MEMORY_TYPE = "memoryType";
    public static final String FIELD_MEMORY_ID = "memoryId";
    public static final String FIELD_SOURCE_SESSION_ID = "sourceSessionId";

    private final EmbeddingStore<TextSegment> memoryEmbeddingStore;
    private final EmbeddingService embeddingService;
    private final MemoryProperties props;

    public VectorMemory(@Qualifier("memoryEmbeddingStore") EmbeddingStore<TextSegment> memoryEmbeddingStore,
                        EmbeddingService embeddingService,
                        MemoryProperties props) {
        this.memoryEmbeddingStore = memoryEmbeddingStore;
        this.embeddingService = embeddingService;
        this.props = props;
    }

    /**
     * 索引单条记忆向量。
     *
     * @param memoryId       agent_memory.id（用于回溯定位）
     * @param text           记忆文本（摘要/单条事实）
     * @param userId         归属用户
     * @param memoryType     SUMMARY / LONG_TERM
     * @param sourceSessionId 来源会话（LONG_TERM 必填，SUMMARY 可空）
     */
    public void index(Long memoryId, String text, Long userId, MemoryType memoryType, Long sourceSessionId) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            Long tid = TenantContext.requiredTenantId();
            Embedding embedding = embeddingService.embed(text);
            Metadata md = new Metadata()
                    .put(FIELD_TENANT_ID, String.valueOf(tid))
                    .put(FIELD_USER_ID, String.valueOf(userId))
                    .put(FIELD_MEMORY_TYPE, memoryType.name())
                    .put(FIELD_MEMORY_ID, String.valueOf(memoryId));
            if (sourceSessionId != null) {
                md.put(FIELD_SOURCE_SESSION_ID, String.valueOf(sourceSessionId));
            }
            TextSegment segment = TextSegment.from(text, md);
            memoryEmbeddingStore.add(embedding, segment);
            log.info("[Memory:Vec] 索引成功 memoryId={} type={} userId={} tid={}",
                    memoryId, memoryType, userId, tid);
        } catch (Exception e) {
            // 索引失败不阻断主流程（事实已落 MySQL，仅丢失语义召回能力）
            log.warn("[Memory:Vec] 索引失败 memoryId={} type={} err={}", memoryId, memoryType, e.getMessage());
        }
    }

    /**
     * 语义召回当前用户的记忆（跨会话）。
     *
     * @param query  查询文本（当前用户目标/问题）
     * @param userId 用户ID
     * @param topK   召回条数（<=0 用配置默认）
     * @return 召回项列表（按相似度降序），失败返回空列表
     */
    public List<MemoryRecall> recall(String query, Long userId, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int limit = topK > 0 ? topK : props.getRecallTopK();
        try {
            Long tid = TenantContext.requiredTenantId();
            Embedding queryEmbedding = embeddingService.embed(query);
            // 强制 tenantId + userId 双过滤：跨会话语义召回该用户全部记忆，防跨租户/跨用户
            Filter filter = metadataKey(FIELD_TENANT_ID).isEqualTo(String.valueOf(tid))
                    .and(metadataKey(FIELD_USER_ID).isEqualTo(String.valueOf(userId)));
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(limit)
                    .minScore(props.getVectorMinScore())
                    .filter(filter)
                    .build();
            EmbeddingSearchResult<TextSegment> result = memoryEmbeddingStore.search(request);
            List<MemoryRecall> recalls = result.matches().stream()
                    .map(this::toRecall)
                    .toList();
            log.info("[Memory:Vec] 召回 query.len={} userId={} tid={} 命中 {} 条 topK={}",
                    query.length(), userId, tid, recalls.size(), limit);
            return recalls;
        } catch (Exception e) {
            log.warn("[Memory:Vec] 召回失败 userId={} err={}", userId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 按 memoryId 删除记忆向量（删库时调用，避免召回孤儿向量）。
     * <p>强制带 tenantId 过滤（防御式：避免跨租户误删同 memoryId 向量）。best-effort：失败仅告警。
     *
     * @param memoryId agent_memory.id
     */
    public void removeByMemoryId(Long memoryId) {
        if (memoryId == null) {
            return;
        }
        try {
            Long tid = TenantContext.requiredTenantId();
            Filter filter = metadataKey(FIELD_TENANT_ID).isEqualTo(String.valueOf(tid))
                    .and(metadataKey(FIELD_MEMORY_ID).isEqualTo(String.valueOf(memoryId)));
            memoryEmbeddingStore.removeAll(filter);
            log.info("[Memory:Vec] 删除向量 memoryId={} tid={}", memoryId, tid);
        } catch (Exception e) {
            log.warn("[Memory:Vec] 删除向量失败 memoryId={} err={}", memoryId, e.getMessage());
        }
    }

    private MemoryRecall toRecall(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        Metadata md = segment.metadata();
        String memoryType = md.getString(FIELD_MEMORY_TYPE);
        Long sourceSessionId = parseLong(md.getString(FIELD_SOURCE_SESSION_ID));
        Double score = match.score();
        return new MemoryRecall(segment.text(), score, memoryType, sourceSessionId);
    }

    private Long parseLong(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
