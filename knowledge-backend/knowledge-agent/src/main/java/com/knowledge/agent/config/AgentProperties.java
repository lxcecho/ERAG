package com.knowledge.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 引擎配置属性（读取 application.yml 中 agent.* 配置）。
 * <p>设计原因：将 Agent 的 LLM 调参、检索范围、执行预算集中管理，
 * 切换模型或调优只需改配置，无需改代码。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** Agent 默认系统提示（约束 Agent 行为：分步推理、引用溯源、不凭空作答） */
    private String defaultSystemPrompt = "你是一个企业知识库分析助手。请严格基于检索到的资料回答，"
            + "所有结论必须附带来源文档引用；若资料不足，请明确说明而非臆测。";

    /** LLM 对话模型名（默认 qwen-plus，OpenAI 兼容协议） */
    private String model = "qwen-plus";

    /** 采样温度，Agent 推理任务偏低（确定性优先） */
    private double temperature = 0.3;

    /** 单次 LLM 调用最大生成 token */
    private int maxTokens = 4096;

    /** 单任务最大执行步数（防止无限循环） */
    private int maxSteps = 12;

    /** 单任务最大工具调用次数（预算封顶，防止资源耗尽） */
    private int maxToolCalls = 30;

    /** 单任务 token 累计预算封顶（含 prompt+completion；超限终止，防止资源耗尽） */
    private int maxTokensPerTask = 60000;

    /** KnowledgeAgent 每轮检索返回的 chunk 数 */
    private int searchTopK = 8;

    /** 任务 wall-clock 超时（秒） */
    private int taskTimeoutSeconds = 300;

    /** 检索配置 */
    private Retrieval retrieval = new Retrieval();

    /** 工具配置（v3-3 Tool Registry：超时/线程池/Email/Http/Sql） */
    private Tool tool = new Tool();

    /** 编排引擎配置（v3-4 AgentGraph：重试/超时/回滚/线程池） */
    private Orchestrator orchestrator = new Orchestrator();

    @Data
    public static class Retrieval {
        /** 初召放大倍数（给权限 Post-Filter 留余量） */
        private int overFetchMultiplier = 4;
        /** 最小初召条数保底 */
        private int minOverFetch = 20;
    }

    /**
     * 工具配置：per-tool 超时默认值、执行线程池、各工具子配置。
     * <p>per-tool 超时优先取自 DB 元数 tool_metadata.timeout_ms，无则用 {@link #defaultTimeoutMs}。
     */
    @Data
    public static class Tool {
        /** 工具默认执行超时（毫秒），DB 元数据未配置时兜底 */
        private long defaultTimeoutMs = 60000;
        /** 工具执行线程池大小（专用池，与 Agent 主流程隔离） */
        private int executorPoolSize = 8;
        /** 邮件工具配置 */
        private Email email = new Email();
        /** HTTP 工具配置 */
        private Http http = new Http();
        /** SQL 工具配置 */
        private Sql sql = new Sql();
    }

    /** 邮件工具配置（mode: log=日志代替发送 / smtp=真实发送） */
    @Data
    public static class Email {
        /** 发送模式 log/smtp（log 模式无需 SMTP，仅记录日志） */
        private String mode = "log";
        /** 发件人地址 */
        private String from = "no-reply@knowledge.ai";
        /** SMTP 主机（mode=smtp 时生效） */
        private String smtpHost;
        /** SMTP 端口 */
        private int smtpPort = 465;
        /** SMTP 用户名 */
        private String smtpUsername;
        /** SMTP 密码 */
        private String smtpPassword;
    }

    /** HTTP 工具配置（SSRF 防护白名单 + 超时） */
    @Data
    public static class Http {
        /** 允许请求的主机白名单（支持 glob，如 *.example.com / localhost） */
        private List<String> allowedHosts = new ArrayList<>();
        /** 连接超时（毫秒） */
        private int connectTimeoutMs = 5000;
        /** 读取超时（毫秒） */
        private int readTimeoutMs = 15000;
    }

    /** SQL 工具配置（只读强制 + 表白名单 + 行数限制） */
    @Data
    public static class Sql {
        /** 允许查询的表白名单（仅允许 SELECT 这些表） */
        private List<String> allowedTables = new ArrayList<>();
        /** 返回行数上限 */
        private int maxRows = 1000;
        /** 单条查询超时（秒） */
        private int queryTimeoutSeconds = 10;
    }

    /**
     * 编排引擎配置（v3-4）：节点级重试 / 硬超时 / 回滚开关 / 执行线程池。
     * <p>节点级策略优先取自 {@link com.knowledge.agent.orchestrator.AgentNode}，未配置时用此处默认值兜底。
     */
    @Data
    public static class Orchestrator {
        /** 节点默认额外重试次数（不含首次；0=不重试） */
        private int defaultMaxRetries = 1;
        /** 节点默认重试退避（毫秒，实际等待 = backoff * attempt） */
        private long defaultRetryBackoffMs = 500L;
        /** 节点默认硬超时（毫秒，0=不限，由任务级 taskTimeoutSeconds 兜底） */
        private long defaultNodeTimeoutMs = 120000L;
        /** 编排执行线程池大小（专用池，与 tool/agent 异步池隔离） */
        private int executorPoolSize = 4;
        /** 是否启用补偿回滚（关闭则节点失败直接 FAILED，不执行补偿链） */
        private boolean rollbackEnabled = true;
    }
}
