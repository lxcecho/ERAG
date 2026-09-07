package com.knowledge.ai.search;

import com.knowledge.ai.dto.RetrievalResult;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

/**
 * Elasticsearch 服务：封装 ES 的索引管理、存储、检索、删除。
 * <p>设计原因：与 {@link com.knowledge.ai.service.MilvusService} 三方法（store/search/deleteByDocument）
 * 镜像对称，作为混合检索的"词法路"底层服务；屏蔽 elasticsearch-java 客户端细节，
 * 业务层（KeywordSearchService / HybridSearchService）只依赖此接口。
 *
 * @author: lxcechoo@gmail.com
 */
public interface ElasticsearchService {

    /**
     * 批量索引切片（ingest 双写用）。
     * <p>以 chunkId 作为 ES _id 实现幂等写入（重试/重建时覆盖而非重复）。
     *
     * @param segments 文本切片（携带 kbId/documentId/source/chunkIndex/chunkId 元数据）
     */
    void store(List<TextSegment> segments);

    /**
     * BM25 关键词检索：在指定知识库内按问题召回 Top-K。
     *
     * @param question 原始问题（分词后匹配 content 字段）
     * @param kbId     知识库ID（term 过滤，对标 Milvus 元数据 filter）
     * @param topK     返回条数
     * @return 检索结果（按 BM25 得分降序，scoreType=bm25）
     */
    List<RetrievalResult> search(String question, Long kbId, int topK);

    /**
     * 按文档删除其全部切片（文档删除时双删用）。
     *
     * @param documentId 文档ID
     */
    void deleteByDocument(Long documentId);

    /** 索引是否存在 */
    boolean indexExists();

    /** 创建索引（含 mapping；IK 分词不可用时自动降级 standard） */
    void createIndex();
}
