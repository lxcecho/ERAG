package com.knowledge.kb.governance.engine;

/**
 * 文档质量评分结果（规则评分器产出，4 维度各 0~25，总分 0~100）。
 *
 * @param score        总分
 * @param completeness 完整性（内容长度维度）
 * @param freshness    时效性（创建时间新鲜度）
 * @param structure    结构性（标题/列表/段落等结构标记）
 * @param coverage     覆盖度（切片数量维度）
 * @param summary      评分说明
 * @param evaluator    评分方式 RULE / LLM
 *
 * @author: lxcechoo@gmail.com
 */
public record QualityResult(int score, int completeness, int freshness, int structure,
                            int coverage, String summary, String evaluator) {
}
