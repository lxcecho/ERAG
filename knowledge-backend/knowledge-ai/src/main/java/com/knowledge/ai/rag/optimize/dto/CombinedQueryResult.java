package com.knowledge.ai.rag.optimize.dto;

import java.util.List;

/**
 * 合并改写+扩展的 LLM 一次调用结果。
 *
 * @param primaryQuery  改写后的主查询（喂 embedding）
 * @param subQueries    多查询子问题（空=单查询模式）
 * @param keywordQuery  关键词扩展后的查询（原问题+同义词，喂 BM25）
 *
 * @author: lxcechoo@gmail.com
 */
public record CombinedQueryResult(String primaryQuery, List<String> subQueries, String keywordQuery) {

    /** LLM 调用失败时的回退：所有查询=原问题 */
    public static CombinedQueryResult fallback(String question) {
        return new CombinedQueryResult(question, List.of(), question);
    }
}
