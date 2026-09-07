package com.knowledge.ai.rag.optimize.expansion;

/**
 * 查询扩展器：为原始问题生成同义词 / 近义词 / 专业术语，拼装成增强关键词查询喂给 BM25。
 * <p>核心价值：向量检索对语义近似敏感但可能漏掉精确术语命中，BM25 对词面精确匹配敏感但召回窄；
 * 扩展同义词后 BM25 召回率提升，与向量路 RRF 融合互补。
 * <p>返回的查询 = 原问题 + 扩展词（空格连接），仅作用于 BM25 词法路，不影响向量路与精排基准。
 * best-effort：开关关闭或异常时返回原问题，不阻断检索。
 *
 * @author: lxcechoo@gmail.com
 */
public interface QueryExpander {

    /**
     * 扩展查询为关键词增强查询。
     *
     * @param question 用户原始问题
     * @return 原问题 + 同义词拼装的关键词查询（失败 / 关闭时返回原问题）
     */
    String expand(String question);
}
