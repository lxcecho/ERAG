package com.knowledge.ai.rag.optimize.prompt;

/**
 * RAG 优化阶段 LLM Prompt 内置模板。
 * <p>查询改写 / 查询扩展 / 回答评价三阶段的提示词常量，统一用 {varName} 命名占位符，
 * 由 {@link com.knowledge.ai.prompt.PromptTemplates#render} 渲染。
 * <p>后续可迁移至 prompt_template 表（prompt_code: rag_rewrite / rag_expansion / rag_eval），
 * 与 rag_system_prompt 同模式管理；当前以常量保持特性自包含，避免 DB 迁移。
 *
 * @author: lxcechoo@gmail.com
 */
public final class OptimizePrompts {

    private OptimizePrompts() {
    }

    /** 查询改写模板：{history} 对话历史 / {question} 用户问题 / {multi_query_directive} 多查询指令 */
    public static final String REWRITE_TEMPLATE = """
            你是查询改写助手。请基于对话历史，将用户最后的问题改写为一个清晰、独立、可检索的查询；
            必要时进行指代消解（把"它/这个/那个"替换为具体实体），但不要改变问题原意。
            {multi_query_directive}

            【对话历史】
            {history}

            【用户问题】
            {question}

            输出严格的 JSON，不要任何解释或 Markdown 包裹：
            {"primary": "改写后的主查询", "subQueries": ["备选查询1", "备选查询2"]}
            """;

    /** 查询扩展模板：{question} 用户问题 / {max_terms} 扩展词上限 */
    public static final String EXPANSION_TEMPLATE = """
            你是关键词扩展助手。请为下方问题生成至多 {max_terms} 个同义词、近义词或专业术语，
            用于增强 BM25 关键词检索的召回率。不要生成整句，只输出词或短语，与原问题语义相关。

            【问题】
            {question}

            输出严格的 JSON，不要任何解释或 Markdown 包裹：
            {"terms": ["词1", "词2", "词3"]}
            """;

    /**
     * 合并改写+扩展模板（一次 LLM 调用完成两件事，减少 ~1s 延迟）。
     * <p>{history} 对话历史 / {question} 用户问题 / {multi_query_directive} 多查询指令 / {max_terms} 扩展词上限
     */
    public static final String REWRITE_EXPANSION_TEMPLATE = """
            你是查询优化助手。请一次性完成以下两项任务：

            任务一（查询改写）：基于对话历史，将用户最后的问题改写为清晰、独立、可检索的查询；
            必要时进行指代消解（把"它/这个/那个"替换为具体实体），但不要改变问题原意。
            {multi_query_directive}

            任务二（关键词扩展）：为改写后的主查询生成至多 {max_terms} 个同义词、近义词或专业术语，
            用于增强 BM25 关键词检索的召回率。只输出词或短语，不要整句。

            【对话历史】
            {history}

            【用户问题】
            {question}

            输出严格的 JSON，不要任何解释或 Markdown 包裹：
            {"primary": "改写后的主查询", "subQueries": ["备选查询1"], "keywordQuery": "原问题 扩展词1 扩展词2"}
            """;

    /** 回答评价模板：{question} 问题 / {context} 参考资料 / {answer} 回答 */
    public static final String EVAL_TEMPLATE = """
            你是 RAG 回答质量评估员。请基于【参考资料】评估【回答】对【问题】的质量。

            【问题】
            {question}

            【参考资料】
            {context}

            【回答】
            {answer}

            评估维度：
            - faithfulness（0-1）：回答是否完全基于参考资料，有无编造（1=完全忠于资料，0=全凭编造）
            - relevance（0-1）：回答是否切中问题（1=完全切题，0=答非所问）

            输出严格的 JSON，不要任何解释或 Markdown 包裹：
            {"faithfulness": 0.85, "relevance": 0.9, "reason": "简短中文说明"}
            """;
}
