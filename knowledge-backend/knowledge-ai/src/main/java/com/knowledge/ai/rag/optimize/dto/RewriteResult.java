package com.knowledge.ai.rag.optimize.dto;

import java.util.List;

/**
 * 查询改写结果。
 *
 * @param primaryQuery 改写后的主查询（指代消解后的独立可检索查询）
 * @param subQueries   多查询子问题（multi-query 关闭或失败时为空列表）
 *
 * @author: lxcechoo@gmail.com
 */
public record RewriteResult(String primaryQuery, List<String> subQueries) {

    /** 改写失败时的回退：主查询=原问题，无子查询 */
    public static RewriteResult fallback(String question) {
        return new RewriteResult(question, List.of());
    }
}
