package com.knowledge.ai.rag.optimize.rewrite;

import com.knowledge.ai.rag.optimize.dto.CombinedQueryResult;
import com.knowledge.ai.rag.optimize.dto.RewriteResult;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 查询改写器：检索前对用户问题做指代消解 / 澄清改写，并可生成多查询子问题。
 * <p>核心价值：
 * <ul>
 *   <li>多轮对话中"它/这个/那个"等指代词在向量检索时无意义，改写为具体实体后召回更精准；</li>
 *   <li>multi-query 生成多个语义相关子查询，多路召回 + RRF 融合提升召回率（默认关，成本 N× 检索）。</li>
 * </ul>
 * best-effort：开关关闭或异常时回退为原问题（{@link RewriteResult#fallback}），不阻断检索。
 *
 * @author: lxcechoo@gmail.com
 */
public interface QueryRewriter {

    /**
     * 改写查询。
     *
     * @param question 用户原始问题
     * @param history  对话历史（指代消解依据；include-history=false 时忽略）
     * @return 改写结果（primaryQuery 为主查询，subQueries 为多查询子问题；失败回退原问题）
     */
    RewriteResult rewrite(String question, List<ChatMessage> history);

    /**
     * 合并改写+扩展：一次 LLM 调用同时输出 primary / subQueries / keywordQuery。
     * <p>相比分两步调用 rewrite() + expand()，减少一次 LLM 往返，延迟降低 ~50%。
     * <p>best-effort：异常回退 {@link CombinedQueryResult#fallback}。
     *
     * @param question 用户原始问题
     * @param history  对话历史
     * @return 合并结果（primaryQuery + subQueries + keywordQuery）
     */
    CombinedQueryResult combinedRewriteExpand(String question, List<ChatMessage> history);
}
