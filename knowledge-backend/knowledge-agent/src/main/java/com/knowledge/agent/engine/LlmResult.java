package com.knowledge.agent.engine;

/**
 * LLM 文本调用结果：承载生成文本 + token 消耗明细。
 * <p>由 {@link AgentLlmCaller#call} 返回，调用方据 totalTokens 上报 AgentResult/ToolResult 做预算统计。
 *
 * @author: lxcechoo@gmail.com
 */
public record LlmResult(String text, int promptTokens, int completionTokens, int totalTokens) {
}
