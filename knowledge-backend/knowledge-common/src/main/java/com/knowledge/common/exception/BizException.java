package com.knowledge.common.exception;

import com.knowledge.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常
 * <p>业务逻辑中主动抛出，由 {@link GlobalExceptionHandler} 统一捕获后返回给前端。
 * 携带状态码，可精准映射到 {@link ResultCode}。
 *
 * @author: lxcechoo@gmail.com
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误状态码 */
    private final int code;

    public BizException(String message) {
        super(message);
        this.code = ResultCode.BUSINESS_ERROR.getCode();
    }

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
