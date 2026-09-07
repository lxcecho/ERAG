package com.knowledge.ai.search;

/**
 * 混合检索常量：ES 索引名、字段名、得分类型。
 * <p>设计原因：统一管理 ES mapping 字段名与元数据 key，避免硬编码字符串散落各处；
 * 同时与 Milvus 切片元数据 key（kbId/documentId/source/chunkIndex）保持一致，便于两路对齐。
 *
 * @author: lxcechoo@gmail.com
 */
public final class SearchConstants {

    private SearchConstants() {
    }

    /** ES 索引名（与 Milvus collection 同名对齐） */
    public static final String INDEX_NAME = "knowledge_chunks";

    // ---- 字段名（同时作为 Milvus metadata key） ----
    public static final String FIELD_CHUNK_ID = "chunkId";
    public static final String FIELD_TENANT_ID = "tenantId";
    public static final String FIELD_KB_ID = "kbId";
    public static final String FIELD_DOCUMENT_ID = "documentId";
    public static final String FIELD_SOURCE = "source";
    public static final String FIELD_CHUNK_INDEX = "chunkIndex";
    public static final String FIELD_CONTENT = "content";
    public static final String FIELD_CREATE_TIME = "createTime";

    // ---- Parent-Child 分块（小召大上下文） ----
    /** 父段文本：连续 N 个小切片合并后的完整段落，检索命中小切片后用此文本喂 LLM */
    public static final String FIELD_PARENT_TEXT = "parentText";
    /** 父段ID：同组小切片共享，用于去重与调试 */
    public static final String FIELD_PARENT_ID = "parentId";

    // ---- 得分类型（写入 RetrievalResult.scoreType，用于可观测与调试） ----
    /** 向量路得分（Milvus 余弦相似度） */
    public static final String SCORE_VECTOR = "vector";
    /** 词法路得分（ES BM25） */
    public static final String SCORE_BM25 = "bm25";
    /** 融合后得分（RRF） */
    public static final String SCORE_FUSED = "fused";
    /** 精排后得分（Cross-Encoder relevance_score） */
    public static final String SCORE_RERANK = "rerank";
}
