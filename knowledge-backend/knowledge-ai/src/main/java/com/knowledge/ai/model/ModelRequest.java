package com.knowledge.ai.model;

/**
 * 模型调用统一请求。
 * <p>跨供应商的请求抽象，屏蔽底层 SDK 差异。{@link ModelProvider} 负责将其映射为具体供应商的调用参数。
 *
 * @param systemPrompt   系统提示词（可空）
 * @param userPrompt     用户提示词（必填）
 * @param maxTokens      单次最大生成 token（可空，空则用供应商默认）
 * @param temperature    采样温度（可空，空则用供应商默认）
 * @param preferredModel 偏好模型名（可空，按名路由时使用）
 *
 * @author: lxcechoo@gmail.com
 */
public record ModelRequest(
        String systemPrompt,
        String userPrompt,
        Integer maxTokens,
        Double temperature,
        String preferredModel
) {

    /** 仅用户提示词的快捷构造 */
    public static ModelRequest of(String userPrompt) {
        return new ModelRequest(null, userPrompt, null, null, null);
    }

    /** 系统提示词 + 用户提示词的快捷构造 */
    public static ModelRequest of(String systemPrompt, String userPrompt) {
        return new ModelRequest(systemPrompt, userPrompt, null, null, null);
    }
}
