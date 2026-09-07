package com.knowledge.ai.rag.optimize.dto;

import java.util.List;

/**
 * 检索前查询上下文（QueryRewrite + QueryExpansion 阶段产出）。
 * <p>三查询解耦——不同检索环节用不同形式的查询，各取所长：
 * <ul>
 *   <li>{@code primaryQuery}：改写后的主查询，用于 embedding（语义召回更精准）</li>
 *   <li>{@code keywordQuery}：扩展后的查询（原词+同义词），用于 BM25 关键词召回（提升召回率）</li>
 *   <li>{@code rerankQuery}：始终为原始问题，用于 Cross-Encoder 精排（避免改写漂移影响精排基准）</li>
 *   <li>{@code subQueries}：多查询子问题（为空表示单查询模式）</li>
 * </ul>
 * 任一阶段关闭/失败时，对应字段回退为原问题，保证可用。
 *
 * @param primaryQuery 主查询（喂 embedding）
 * @param keywordQuery 关键词查询（喂 BM25）
 * @param rerankQuery  精排查询（喂 Cross-Encoder，恒为原始问题）
 * @param subQueries   多查询子问题（空=单查询模式）
 *
 * @author: lxcechoo@gmail.com
 */
public record QueryContext(String primaryQuery, String keywordQuery, String rerankQuery,
                           List<String> subQueries) {

    /** 构造无改写/扩展的默认上下文（所有查询均为原问题） */
    public static QueryContext ofOriginal(String question) {
        return new QueryContext(question, question, question, List.of());
    }
}
