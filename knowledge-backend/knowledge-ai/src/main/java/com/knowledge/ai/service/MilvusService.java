package com.knowledge.ai.service;

import com.knowledge.ai.dto.RetrievalResult;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

/**
 * 向量库服务：封装 Milvus 的存储、检索、删除。
 * <p>设计原因：屏蔽 LangChain4j EmbeddingStore 与 Milvus 细节，对外暴露面向业务语义的
 * "存切片 / 按知识库检索 / 按文档删除"接口；切片元数据携带 kbId、documentId 用于过滤。
 *
 * @author: lxcechoo@gmail.com
 */
public interface MilvusService {

    /**
     * 批量存储向量与切片。
     *
     * @param embeddings 向量（与 segments 一一对应）
     * @param segments   文本切片（携带 kbId / documentId / source 元数据）
     * @return Milvus 分配的主键列表
     */
    List<String> store(List<Embedding> embeddings, List<TextSegment> segments);

    /**
     * 相似度检索：在指定知识库范围内按查询向量检索 Top-K。
     *
     * @param queryEmbedding 查询向量
     * @param kbId           知识库ID（元数据过滤，避免跨库召回）
     * @param topK           返回条数
     * @return 检索结果（按相关度降序）
     */
    List<RetrievalResult> search(Embedding queryEmbedding, Long kbId, int topK);

    /**
     * 按文档删除其全部切片（文档被删除时清理向量库）。
     *
     * @param documentId 文档ID
     */
    void deleteByDocument(Long documentId);

    /**
     * 按文档ID查询其内容切片（无语义排序，按 chunkIndex 升序）。
     * <p>用于 {@code DocumentTool} 精确读取指定文档的原文切片（区别于 {@link #search} 的语义检索）。
     * <p>实现采用「Filter 过滤 + 单位向量探针 + minScore=-1 接受全部」的方式：
     * LangChain4j {@code EmbeddingStore} 无纯过滤读取接口（仅有 {@code removeAll(Filter)}），
     * 故以一个非零单位向量作为查询向量绕过 COSINE 度量的零向量 NaN 问题，
     * 配合 {@code minScore=-1.0} 放弃得分过滤，使过滤命中的切片全部返回；最终按 chunkIndex 重排。
     * 租户隔离：强制追加 tenantId 过滤（{@link com.knowledge.common.context.TenantContext#requiredTenantId}）。
     *
     * @param documentId 文档ID
     * @param limit      返回切片上限（按 chunkIndex 升序截断）
     * @return 该文档的内容切片（text + chunkIndex + chunkId + source），可能为空（文档未解析或无切片）
     */
    List<RetrievalResult> queryByDocument(Long documentId, int limit);
}
