package com.knowledge.agent.tool;

import lombok.Getter;

/**
 * 工具执行结果。
 * <p>成功时携带结构化数据 + token 消耗（供 Executor 累加做预算校验）；
 * 失败时携带错误信息（触发 Agent 步骤终止或重试）。
 *
 * @author: lxcechoo@gmail.com
 */
@Getter
public class ToolResult {

    private final boolean success;
    /** 结构化数据（List&lt;Evidence&gt; / String / Map 等，由调用方按约定强转） */
    private final Object data;
    /** 失败原因 */
    private final String errorMessage;
    /** 本次 LLM 调用 token 消耗（0 表示未调用 LLM，如纯检索工具） */
    private final int tokensUsed;

    private ToolResult(boolean success, Object data, String errorMessage, int tokensUsed) {
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
        this.tokensUsed = tokensUsed;
    }

    /** 成功：携带数据 */
    public static ToolResult success(Object data) {
        return new ToolResult(true, data, null, 0);
    }

    /** 成功：携带数据 + LLM token 消耗 */
    public static ToolResult success(Object data, int tokensUsed) {
        return new ToolResult(true, data, null, tokensUsed);
    }

    /** 失败：携带错误信息 */
    public static ToolResult failure(String errorMessage) {
        return new ToolResult(false, null, errorMessage, 0);
    }
}
