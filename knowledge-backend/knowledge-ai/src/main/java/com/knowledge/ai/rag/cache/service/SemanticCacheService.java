package com.knowledge.ai.rag.cache.service;

import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.rag.cache.dto.CacheHit;
import dev.langchain4j.data.embedding.Embedding;

import java.util.List;
import java.util.Optional;

/**
 * 语义缓存服务：按 embedding 余弦相似度命中缓存，跳过检索+LLM 生成。
 * <p>所有方法均为 best-effort：异常时返回 empty / 静默跳过，不阻断主流程。
 *
 * @author: lxcechoo@gmail.com
 */
public interface SemanticCacheService {

    /**
     * 查找语义缓存命中。
     *
     * @param tenantId       租户ID
     * @param kbId           知识库ID
     * @param queryEmbedding 问题向量
     * @return 命中时返回 CacheHit（回答+来源+相似度），未命中返回 empty
     */
    Optional<CacheHit> lookup(Long tenantId, Long kbId, Embedding queryEmbedding);

    /**
     * MD5 精确匹配（无需向量计算，O(1) 数据库查询）。
     *
     * @param tenantId 租户ID
     * @param kbId     知识库ID
     * @param question 原始问题
     * @return 命中时返回 CacheHit，未命中返回 empty
     */
    Optional<CacheHit> exactLookup(Long tenantId, Long kbId, String question);

    /**
     * 写入语义缓存。
     *
     * @param tenantId       租户ID
     * @param kbId           知识库ID
     * @param question       原始问题
     * @param embedding      问题向量
     * @param answer         LLM 回答
     * @param sources        引用来源
     */
    void save(Long tenantId, Long kbId, String question, Embedding embedding,
              String answer, List<RetrievalResult> sources);
}
