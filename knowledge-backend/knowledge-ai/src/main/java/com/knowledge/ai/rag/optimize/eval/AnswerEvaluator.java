package com.knowledge.ai.rag.optimize.eval;

import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.rag.optimize.dto.AnswerEvaluation;

import java.util.List;

/**
 * 回答质量评价器：对 LLM 生成的回答做忠实度 / 相关度评估，用于监控 RAG 幻觉与回答质量。
 * <p>两种实现路径（由配置切换，规则版为基线，LLM 版为高精度可选）：
 * <ul>
 *   <li><b>规则版</b>（默认）：faithfulness = 回答词汇被上下文覆盖比例；
 *       relevance = 问题与回答的 Jaccard 词汇重叠。廉价、零延迟。</li>
 *   <li><b>LLM 版</b>（可选，默认关）：调用 LLM 评判忠实度/相关度，精度更高但 +1 LLM 调用。</li>
 * </ul>
 * best-effort：失败返回 null（评价为可选元数据，不阻断回答）。
 *
 * @author: lxcechoo@gmail.com
 */
public interface AnswerEvaluator {

    /**
     * 评价回答质量。
     *
     * @param question 原始问题
     * @param results  检索命中的参考资料
     * @param answer   LLM 回答
     * @return 评价结果；开关关闭或失败时返回 null
     */
    AnswerEvaluation evaluate(String question, List<RetrievalResult> results, String answer);

    /**
     * 评价回答质量（可控制是否允许 LLM 评价）。
     * <p>流式场景传 {@code allowLlm=false}，仅跑规则版，避免 LLM 评价阻塞 done 事件。
     *
     * @param allowLlm 是否允许调用 LLM 评价（false=仅规则版）
     */
    AnswerEvaluation evaluate(String question, List<RetrievalResult> results, String answer, boolean allowLlm);
}
