package com.knowledge.ai.rag.optimize.dto;

/**
 * 回答质量评价结果。
 *
 * @param faithfulness 忠实度 0~1：回答是否基于检索资料（1=完全忠于资料，0=全凭编造）
 * @param relevance    相关度 0~1：回答是否切中问题（1=完全切题，0=答非所问）
 * @param overallScore 综合分 0~1：0.6×忠实度 + 0.4×相关度
 * @param reason       简短中文说明
 * @param method       评价方式 RULE / LLM
 *
 * @author: lxcechoo@gmail.com
 */
public record AnswerEvaluation(double faithfulness, double relevance, double overallScore,
                               String reason, String method) {
}
