package com.knowledge.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 能线配置属性（读取 application.yml 中 ai.* 配置）。
 * <p>设计原因：将 LLM / Embedding / Milvus / RAG 调参集中管理，
 * 切换模型供应商或调优切片参数只需改配置，无需改代码。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 大语言模型配置（OpenAI 兼容协议） */
    private Llm llm = new Llm();

    /** Embedding 模型配置 */
    private Embedding embedding = new Embedding();

    /** Milvus 向量库配置 */
    private Milvus milvus = new Milvus();

    /** RAG 流程调参 */
    private Rag rag = new Rag();

    /** 模型计费配置（用于 AI 调用日志的费用计算） */
    private Pricing pricing = new Pricing();

    @Data
    public static class Llm {
        /** OpenAI 兼容 base-url，如 DeepSeek: https://api.deepseek.com/v1 */
        private String baseUrl;
        /** API Key（建议通过环境变量注入） */
        private String apiKey;
        /** 对话模型名，如 deepseek-chat / qwen-plus / gpt-4o-mini */
        private String modelName = "deepseek-chat";
        /** 采样温度，越高越发散 */
        private double temperature = 0.7;
        /** 单次最大生成 token */
        private int maxTokens = 2048;
        /** 流式输出超时（秒），SSE 连接最长保持时间 */
        private int streamTimeout = 120;
    }

    @Data
    public static class Embedding {
        /** Embedding 服务 base-url（OpenAI 兼容协议，如 Ollama: http://localhost:11434/v1） */
        private String baseUrl;
        /** API Key */
        private String apiKey;
        /** Embedding 模型名，如 text-embedding-3-small / text-embedding-v3 */
        private String modelName = "text-embedding-3-small";
        /** 向量维度，必须与 Milvus 集合维度一致 */
        private int dimension = 1536;
        /** 单次请求超时（秒）：本地 Ollama CPU 批量向量化大文档较慢，默认 180s */
        private int timeoutSeconds = 180;
        /**
         * 批量向量化分批大小：单次请求最多处理的切片数。
         * <p>不同供应商上限不同：dashscope ≤25，OpenAI ≤2048，Ollama 无硬限制。
         * 默认 64 是本地 Ollama + 远端 API 的安全折中值，较原 10 提升约 6x 吞吐。
         * <p>如遇超时可适当调小（如 32），大文档场景可调大（如 128）。
         */
        private int batchSize = 64;
    }

    @Data
    public static class Milvus {
        private String host = "localhost";
        private int port = 19530;
        /** 完整 uri（设置后优先于 host/port），如 http://localhost:19530 */
        private String uri;
        /** 集合名 */
        private String collectionName = "knowledge_chunks";
        /** 集合维度（须与 embedding.dimension 一致） */
        private int dimension = 1536;
    }

    @Data
    public static class Rag {
        /** 切片最大字符数 */
        private int chunkSize = 800;
        /** 切片重叠字符数（提升上下文连续性） */
        private int chunkOverlap = 200;
        /** 检索 Top K */
        private int topK = 5;
        /** 相似度下限（0~1），过滤低质命中 */
        private double minScore = 0.6;
        /** 多轮对话历史窗口大小（最近 N 轮 = 2N 条消息） */
        private int historyWindowSize = 5;
        /** 是否启用 DB 模板（false 时使用内置默认模板） */
        private boolean useDbTemplate = false;
        /** 混合检索配置（ES BM25 + Milvus 向量） */
        private Hybrid hybrid = new Hybrid();
        /** Parent-Child 分块配置（小召大上下文） */
        private ParentChild parentChild = new ParentChild();
        /** 语义缓存配置 */
        private SemanticCache semanticCache = new SemanticCache();
    }

    /**
     * 混合检索配置：控制 ES 词法路 + 向量路 + 融合 + Rerank。
     * <p>总开关 enabled=false 时完全退回原纯向量架构（零影响降级）。
     */
    @Data
    public static class Hybrid {
        /** 混合检索总开关，false 时 hybridSearch 直接走纯向量 */
        private boolean enabled = false;
        /** 是否启用 ES 词法路（false 或 ES 不可用时降级纯向量） */
        private boolean esEnabled = true;
        /** ES BM25 召回条数（大于最终 topK，给融合留余量） */
        private int esTopK = 20;
        /** 向量路召回条数 */
        private int vectorTopK = 20;
        /** 融合策略 */
        private Fusion fusion = new Fusion();
        /** 精排策略 */
        private Rerank rerank = new Rerank();
    }

    @Data
    public static class Fusion {
        /** 融合算法：rrf（倒数排名融合，目前唯一，预留加权扩展） */
        private String type = "rrf";
        /** RRF 常数 k，越大排名差异越平缓，经验值 60 */
        private int rrfK = 60;
    }

    @Data
    public static class Rerank {
        /** 精排类型：noop（不精排）/ rrf（沿用融合分）/ http（外部 Cross-Encoder） */
        private String type = "noop";
        /** 精排后保留条数 */
        private int topN = 5;
        /** Cross-Encoder 服务地址（type=http 时生效），如 SiliconFlow: https://api.siliconflow.cn/v1/rerank */
        private String url;
        /** Cross-Encoder API Key（建议环境变量注入） */
        private String apiKey;
        /** Rerank 模型名，如 BAAI/bge-reranker-v2-m3 */
        private String model;
        /** HTTP 调用超时（毫秒） */
        private int timeoutMs = 5000;
    }

    /**
     * Parent-Child 分块配置（小召大上下文策略）。
     * <p>原理：检索用小切片（精准命中），喂 LLM 用合并后的父段（上下文完整）。
     * 每 {@code groupSize} 个连续小切片合并为一个父段，父段文本存入切片 metadata。
     * <p>关闭：groupSize <= 1 时不注入 parentText，行为与原方案完全一致。
     */
    @Data
    public static class ParentChild {
        /** 是否启用 Parent-Child 分块 */
        private boolean enabled = true;
        /** 每 N 个连续小切片合并为一个父段（建议 2~5） */
        private int groupSize = 3;
    }

    /**
     * 语义缓存配置。
     * <p>原理：对已回答过的问题，按 embedding 余弦相似度匹配缓存命中，
     * 跳过检索+LLM 生成，直接返回缓存的回答与引用来源。
     * <p>关闭：enabled=false 时完全不查/写缓存，零影响降级。
     */
    @Data
    public static class SemanticCache {
        /** 是否启用语义缓存 */
        private boolean enabled = true;
        /** 命中阈值（0~1），余弦相似度 >= 此值视为命中 */
        private double threshold = 0.92;
        /** 缓存有效期（小时），超时自动失效 */
        private int ttlHours = 24;
        /** 候选条数：取最近 N 条缓存做逐一余弦比较（限制计算量） */
        private int candidateLimit = 50;
    }

    /**
     * 模型计费配置：按模型名配置每千 token 单价（元），未命中模型走 default。
     * <p>用于 AI 调用日志的费用估算（cost = promptTokens/1000*input + completionTokens/1000*output）。
     */
    @Data
    public static class Pricing {
        /** 默认输入单价（元/1K tokens） */
        private double defaultInput = 0.002;
        /** 默认输出单价（元/1K tokens） */
        private double defaultOutput = 0.006;
        /** 按模型名单价表 */
        private Map<String, ModelPrice> models = new HashMap<>();

        @Data
        public static class ModelPrice {
            /** 输入单价（元/1K tokens） */
            private double input;
            /** 输出单价（元/1K tokens） */
            private double output;
        }
    }
}
