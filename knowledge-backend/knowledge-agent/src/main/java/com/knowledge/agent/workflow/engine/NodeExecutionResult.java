package com.knowledge.agent.workflow.engine;

import lombok.Getter;

/**
 * 节点执行结果（NodeHandler 统一返回）。
 * <p>成功时 output 写入上下文 outputKey 供下游引用；失败时 errorMessage 触发重试/终止。
 *
 * @author: lxcechoo@gmail.com
 */
@Getter
public class NodeExecutionResult {

    private final boolean success;
    private final Object output;
    private final String errorMessage;
    private final int tokensUsed;

    private NodeExecutionResult(boolean success, Object output, String errorMessage, int tokensUsed) {
        this.success = success;
        this.output = output;
        this.errorMessage = errorMessage;
        this.tokensUsed = tokensUsed;
    }

    public static NodeExecutionResult success(Object output) {
        return new NodeExecutionResult(true, output, null, 0);
    }

    public static NodeExecutionResult success(Object output, int tokensUsed) {
        return new NodeExecutionResult(true, output, null, tokensUsed);
    }

    public static NodeExecutionResult failure(String errorMessage) {
        return new NodeExecutionResult(false, null, errorMessage, 0);
    }
}
