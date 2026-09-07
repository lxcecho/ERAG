package com.knowledge.ai.model;

import com.knowledge.common.exception.BizException;
import com.knowledge.common.result.ResultCode;

/**
 * 模型路由异常：无可用供应商、策略无法决策或全部供应商调用失败时抛出。
 * <p>继承 {@link BizException}，由 {@code GlobalExceptionHandler} 统一捕获返回前端。
 *
 * @author: lxcechoo@gmail.com
 */
public class ModelRouteException extends BizException {

    private static final long serialVersionUID = 1L;

    public ModelRouteException(String message) {
        super(message);
    }

    public ModelRouteException(ResultCode resultCode) {
        super(resultCode);
    }

    public ModelRouteException(ResultCode resultCode, String message) {
        super(resultCode, message);
    }
}
