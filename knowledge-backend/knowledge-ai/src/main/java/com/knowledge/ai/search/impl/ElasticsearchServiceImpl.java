package com.knowledge.ai.search.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.knowledge.ai.config.EsProperties;
import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.search.ElasticsearchService;
import com.knowledge.ai.search.SearchConstants;
import com.knowledge.common.context.TenantContext;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 服务实现：基于 elasticsearch-java 客户端。
 * <p>所有切片均强制写入 tenantId（存储层保证），搜索/删除时同样强制按 tenantId 过滤（检索层保证）。
 * <p>store：bulk 批量索引，chunkId 作 _id 幂等写入；
 * <p>search：bool(filter tenantId + filter kbId + must match content) BM25 召回；
 * <p>deleteByDocument：deleteByQuery 按 (tenantId, documentId) 清理；
 * <p>createIndex：mapping 新增 tenantId(long) 字段，供 filter 高效 term 过滤。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchServiceImpl implements ElasticsearchService {

    private final ElasticsearchClient client;
    private final EsProperties props;

    @Override
    public void store(List<TextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return;
        }
        Long tenantId = TenantContext.getTenantId();
        String index = props.getIndexName();
        BulkRequest.Builder bulk = new BulkRequest.Builder();
        for (TextSegment seg : segments) {
            Metadata md = seg.metadata();
            String chunkId = md.getString(SearchConstants.FIELD_CHUNK_ID);
            if (chunkId == null || chunkId.isBlank()) {
                chunkId = "doc" + md.getString(SearchConstants.FIELD_DOCUMENT_ID)
                        + "_chunk" + md.getString(SearchConstants.FIELD_CHUNK_INDEX);
            }
            Map<String, Object> doc = new HashMap<>();
            doc.put(SearchConstants.FIELD_CHUNK_ID, chunkId);
            // 优先取 metadata 显式值，其次取上下文 tid，再为 null 就不写（单租户）
            Long mdTenant = toLong(md.getString(SearchConstants.FIELD_TENANT_ID));
            if (mdTenant != null) {
                doc.put(SearchConstants.FIELD_TENANT_ID, mdTenant);
            } else if (tenantId != null) {
                doc.put(SearchConstants.FIELD_TENANT_ID, tenantId);
            }
            doc.put(SearchConstants.FIELD_KB_ID, toLong(md.getString(SearchConstants.FIELD_KB_ID)));
            doc.put(SearchConstants.FIELD_DOCUMENT_ID, toLong(md.getString(SearchConstants.FIELD_DOCUMENT_ID)));
            doc.put(SearchConstants.FIELD_SOURCE, md.getString(SearchConstants.FIELD_SOURCE));
            doc.put(SearchConstants.FIELD_CHUNK_INDEX, toInt(md.getString(SearchConstants.FIELD_CHUNK_INDEX)));
            doc.put(SearchConstants.FIELD_CONTENT, seg.text());
            // Parent-Child：父段文本一同写入 ES，BM25 召回后用父段文本喂 LLM
            String parentText = md.getString(SearchConstants.FIELD_PARENT_TEXT);
            if (parentText != null && !parentText.isBlank()) {
                doc.put(SearchConstants.FIELD_PARENT_TEXT, parentText);
            }
            doc.put(SearchConstants.FIELD_CREATE_TIME, Instant.now().toString());

            final String id = chunkId;
            bulk.operations(op -> op.index(i -> i.index(index).id(id).document(doc)));
        }
        try {
            BulkResponse resp = client.bulk(bulk.build());
            if (resp.errors()) {
                long failed = resp.items().stream()
                        .filter(i -> i.error() != null)
                        .count();
                log.warn("[ES存储] 批量写入存在失败项 index={} failed={} tid={}", index, failed, tenantId);
            } else {
                log.info("[ES存储] 写入 {} 条切片 index={} tid={}", segments.size(), index, tenantId);
            }
        } catch (IOException e) {
            // 包装为运行时异常上抛，由 ingest 捕获并标记 es_indexed=2（best-effort：不阻断 Milvus 主流程）
            throw new RuntimeException("ES批量写入IO异常: " + e.getMessage(), e);
        }
    }

    @Override
    public List<RetrievalResult> search(String question, Long kbId, int topK) {
        String index = props.getIndexName();
        Long tid = TenantContext.requiredTenantId();
        try {
            SearchResponse<Map> resp = client.search(s -> s
                            .index(index)
                            .size(topK)
                            .query(q -> q.bool(b -> b
                                    .filter(f -> f.term(t -> t.field(SearchConstants.FIELD_TENANT_ID).value(tid)))
                                    .filter(f -> f.term(t -> t.field(SearchConstants.FIELD_KB_ID).value(kbId)))
                                    .must(m -> m.match(mm -> mm.field(SearchConstants.FIELD_CONTENT).query(question))))),
                    Map.class);
            List<RetrievalResult> results = new ArrayList<>();
            for (var hit : resp.hits().hits()) {
                Map src = hit.source();
                if (src == null) {
                    continue;
                }
                // Parent-Child：优先使用父段文本（上下文更完整），回退小切片原文
                String parentText = (String) src.get(SearchConstants.FIELD_PARENT_TEXT);
                String content = (String) src.get(SearchConstants.FIELD_CONTENT);
                String text = (parentText != null && !parentText.isBlank()) ? parentText : content;
                results.add(new RetrievalResult(
                        text,
                        hit.score() == null ? 0d : hit.score(),
                        (String) src.get(SearchConstants.FIELD_SOURCE),
                        toLongObj(src.get(SearchConstants.FIELD_DOCUMENT_ID)),
                        toIntObj(src.get(SearchConstants.FIELD_CHUNK_INDEX)),
                        hit.id() != null ? hit.id() : (String) src.get(SearchConstants.FIELD_CHUNK_ID),
                        SearchConstants.SCORE_BM25
                ));
            }
            log.info("[ES检索] kb={} tid={} 命中 {} 条", kbId, tid, results.size());
            return results;
        } catch (Exception e) {
            log.warn("[ES检索] 查询异常 kb={} tid={}: {}", kbId, tid, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void deleteByDocument(Long documentId) {
        String index = props.getIndexName();
        Long tid = TenantContext.getTenantId();
        try {
            if (tid != null) {
                client.deleteByQuery(d -> d.index(index)
                        .query(q -> q.bool(b -> b
                                .filter(f -> f.term(t -> t.field(SearchConstants.FIELD_TENANT_ID).value(tid)))
                                .filter(f -> f.term(t -> t.field(SearchConstants.FIELD_DOCUMENT_ID).value(documentId))))));
                log.info("[ES删除] documentId={} tid={} 的切片已清理", documentId, tid);
            } else {
                client.deleteByQuery(d -> d.index(index)
                        .query(q -> q.term(t -> t.field(SearchConstants.FIELD_DOCUMENT_ID).value(documentId))));
                log.info("[ES删除] documentId={} (无租户上下文) 的切片已清理", documentId);
            }
        } catch (Exception e) {
            log.warn("[ES删除] 清理异常 documentId={}: {}", documentId, e.getMessage());
        }
    }

    @Override
    public boolean indexExists() {
        try {
            return client.indices().exists(e -> e.index(props.getIndexName())).value();
        } catch (Exception e) {
            log.warn("[ES索引] 存在性检查失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void createIndex() {
        String index = props.getIndexName();
        // 优先 IK 中文分词（专业名词匹配更优），不可用则降级 standard
        try {
            client.indices().create(c -> c.index(index).mappings(ikMapping()));
            log.info("[ES索引] 创建成功(ik分词) index={}", index);
        } catch (Exception e) {
            log.warn("[ES索引] ik 分词创建失败，降级 standard: {}", e.getMessage());
            try {
                client.indices().create(c -> c.index(index).mappings(standardMapping()));
                log.info("[ES索引] 创建成功(standard分词) index={}", index);
            } catch (Exception ex) {
                log.error("[ES索引] 创建失败 index={}", index, ex);
                throw new RuntimeException("ES索引创建失败: " + ex.getMessage(), ex);
            }
        }
    }

    /** IK 分词 mapping：content 索引 ik_max_word（细粒度召回）/ 查询 ik_smart（粗粒度） */
    private TypeMapping ikMapping() {
        return TypeMapping.of(m -> m
                .properties(SearchConstants.FIELD_CHUNK_ID, p -> p.keyword(k -> k))
                .properties(SearchConstants.FIELD_TENANT_ID, p -> p.long_(l -> l))
                .properties(SearchConstants.FIELD_KB_ID, p -> p.long_(l -> l))
                .properties(SearchConstants.FIELD_DOCUMENT_ID, p -> p.long_(l -> l))
                .properties(SearchConstants.FIELD_SOURCE, p -> p.keyword(k -> k))
                .properties(SearchConstants.FIELD_CHUNK_INDEX, p -> p.integer(i -> i))
                .properties(SearchConstants.FIELD_CONTENT, p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                .properties(SearchConstants.FIELD_PARENT_TEXT, p -> p.text(t -> t.index(false)))
                .properties(SearchConstants.FIELD_CREATE_TIME, p -> p.date(d -> d)));
    }

    /** standard 分词 mapping：兜底方案（ES 内置，无需插件） */
    private TypeMapping standardMapping() {
        return TypeMapping.of(m -> m
                .properties(SearchConstants.FIELD_CHUNK_ID, p -> p.keyword(k -> k))
                .properties(SearchConstants.FIELD_TENANT_ID, p -> p.long_(l -> l))
                .properties(SearchConstants.FIELD_KB_ID, p -> p.long_(l -> l))
                .properties(SearchConstants.FIELD_DOCUMENT_ID, p -> p.long_(l -> l))
                .properties(SearchConstants.FIELD_SOURCE, p -> p.keyword(k -> k))
                .properties(SearchConstants.FIELD_CHUNK_INDEX, p -> p.integer(i -> i))
                .properties(SearchConstants.FIELD_CONTENT, p -> p.text(t -> t.analyzer("standard").searchAnalyzer("standard")))
                .properties(SearchConstants.FIELD_PARENT_TEXT, p -> p.text(t -> t.index(false)))
                .properties(SearchConstants.FIELD_CREATE_TIME, p -> p.date(d -> d)));
    }

    // ---- 元数据/字段类型转换 ----
    private static Long toLong(String v) {
        return v == null ? null : Long.valueOf(v);
    }

    private static Integer toInt(String v) {
        return v == null ? null : Integer.valueOf(v);
    }

    private static Long toLongObj(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    private static Integer toIntObj(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }
}
