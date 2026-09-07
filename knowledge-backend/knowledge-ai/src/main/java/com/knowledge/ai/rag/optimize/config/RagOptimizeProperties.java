package com.knowledge.ai.rag.optimize.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 优化流水线配置属性（读取 application.yml 中 ai.rag.optimize.* 配置）。
 * <p>承载 4 个优化阶段的开关与参数：查询改写 / 查询扩展 / 上下文压缩 / 回答评价。
 * <p>默认策略（已与用户确认）：轻量阶段（压缩 + 规则评价）默认开，LLM 重型阶段（改写 / 扩展 / 多查询 / LLM 评价）默认关，
 * 平衡效果与成本/延迟。各阶段 best-effort，开关关闭时零影响降级。
 * <p>注：Rerank 配置不在此处，仍在 {@code AiProperties.Rerank}（因 @ConditionalOnProperty 已读 ai.rag.hybrid.rerank.type）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.rag.optimize")
public class RagOptimizeProperties {

    /** 查询改写（LLM 指代消解 + 可选多查询） */
    private Rewrite rewrite = new Rewrite();

    /** 查询扩展（LLM 同义词扩展，增强 BM25 召回） */
    private Expansion expansion = new Expansion();

    /** 上下文压缩（去重 + char 预算截断 + 可选 LLM 抽取） */
    private Compress compress = new Compress();

    /** 回答评价（规则版 + 可选 LLM 版） */
    private Eval eval = new Eval();

    @Data
    public static class Rewrite {
        /** 是否启用查询改写（默认关：检索前 +1 LLM 往返） */
        private boolean enabled = false;
        /** 是否启用多查询（生成 N 个子查询多路召回，默认关：N× embedding+search 成本） */
        private boolean multiQueryEnabled = false;
        /** 子查询上限（multi-query-enabled=true 时生效） */
        private int maxSubQueries = 3;
        /** 是否带入对话历史做指代消解 */
        private boolean includeHistory = true;
    }

    @Data
    public static class Expansion {
        /** 是否启用查询扩展（默认关：+1 LLM 往返） */
        private boolean enabled = false;
        /** 扩展词上限 */
        private int maxTerms = 5;
    }

    @Data
    public static class Compress {
        /** 是否启用上下文压缩（默认开：廉价去重+截断，收益明确） */
        private boolean enabled = true;
        /** 上下文最大字符数预算 */
        private int maxContextChars = 6000;
        /** 近似重复切片 Jaccard 相似度阈值（≥该值去重） */
        private double dedupThreshold = 0.8;
        /** 是否启用 LLM 抽取式压缩（默认关：N 次 LLM 调用，成本重） */
        private boolean llmCompressEnabled = false;
    }

    @Data
    public static class Eval {
        /** 是否启用回答评价（默认开：规则版廉价，提供可观测性） */
        private boolean enabled = true;
        /** 是否启用 LLM 评价（默认关：每次回答 +1 LLM 调用） */
        private boolean llmEvalEnabled = false;
    }
}
