package com.knowledge.ai.model;

import lombok.Getter;

/**
 * 模型供应商类型枚举。
 * <p>
 * 设计选型：统一基于 <b>OpenAI 兼容协议</b>接入多供应商，避免为每个供应商引入独立 SDK 带来的依赖膨胀与
 * 版本耦合。各供应商仅 {@code baseUrl / apiKey / 模型名}不同，底层共用 LangChain4j {@code OpenAiChatModel}：
 * <ul>
 *   <li>OpenAI / DeepSeek：原生 OpenAI 兼容协议</li>
 *   <li>Ollama：本地推理引擎，官方提供 {@code /v1} OpenAI 兼容端点</li>
 *   <li>Gemini：Google 官方提供 OpenAI 兼容端点 {@code /v1beta/openai}</li>
 *   <li>Claude：经 OpenAI 兼容网关（如 OneAPI / LiteLLM）接入；如需原生协议可新增 {@code AnthropicProvider}</li>
 * </ul>
 * 枚举携带各供应商默认 baseUrl 与默认模型名，配置缺失时兜底，便于零配置演示。
 *
 * @author: lxcechoo@gmail.com
 */
@Getter
public enum ProviderType {

    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    CLAUDE("Anthropic Claude", "https://api.anthropic.com/v1", "claude-3-5-sonnet-latest"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-1.5-flash"),
    OLLAMA("Ollama 本地", "http://localhost:11434/v1", "qwen2.5:7b");

    /** 展示名（日志 / 前端展示用） */
    private final String displayName;

    /** 默认 OpenAI 兼容 baseUrl（可被 application.yml 覆盖） */
    private final String defaultBaseUrl;

    /** 默认模型名 */
    private final String defaultModel;

    ProviderType(String displayName, String defaultBaseUrl, String defaultModel) {
        this.displayName = displayName;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
    }
}
