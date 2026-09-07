package com.knowledge.agent.tool;

import lombok.Getter;

/**
 * 工具调用异常：工具执行过程中抛出的业务异常，携带工具名与错误码便于审计定位。
 * <p>由 {@link ToolExecutor} 统一捕获并转为 {@link ToolResult#failure}，
 * 不向调用方泄漏堆栈（安全考虑），仅记录到审计日志。
 *
 * @author: lxcechoo@gmail.com
 */
@Getter
public class ToolException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 触发异常的工具名 */
    private final String toolName;

    /** 错误码（MISSING_PARAMETER / PERMISSION_DENIED / TOOL_NOT_FOUND / EXECUTION_ERROR 等） */
    private final String errorCode;

    public ToolException(String toolName, String errorCode, String message) {
        super(message);
        this.toolName = toolName;
        this.errorCode = errorCode;
    }

    public ToolException(String toolName, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.toolName = toolName;
        this.errorCode = errorCode;
    }
}
