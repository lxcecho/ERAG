package com.knowledge.ai.model.provider;

import com.knowledge.ai.model.ProviderType;
import com.knowledge.ai.model.config.ModelRouterProperties;

/**
 * Anthropic Claude 供应商。
 * <p>经 OpenAI 兼容网关（如 OneAPI / LiteLLM）接入，baseUrl 指向网关地址；
 * 如需原生 Anthropic 协议，可新增 {@code AnthropicNativeProvider} 基于 langchain4j-anthropic 实现，
 * 不影响本路由抽象——这正是统一 {@link com.knowledge.ai.model.ModelProvider} 接口的意义。
 *
 * @author: lxcechoo@gmail.com
 */
public class ClaudeProvider extends AbstractOpenAiProvider {

    public ClaudeProvider(ModelRouterProperties.ProviderConfig config) {
        super(ProviderType.CLAUDE, config);
    }
}
