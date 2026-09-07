package com.knowledge.common.exception;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.knowledge.common.result.Result;
import com.knowledge.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>统一拦截 Controller 层抛出的异常，转换为标准 {@link Result} 返回。
 * 按异常类型从细到粗匹配，避免被兜底逻辑吞掉具体信息。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：业务校验失败、状态非法等，记录 warn 级别
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("[业务异常] uri={}, code={}, msg={}", request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.failed(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常：@RequestBody + @Valid 触发
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ":" + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[参数校验失败] {}", msg);
        return Result.failed(ResultCode.PARAM_VALIDATE_FAILED.getCode(), msg);
    }

    /**
     * 参数绑定异常：@ModelAttribute 表单校验触发
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[参数绑定失败] {}", msg);
        return Result.failed(ResultCode.PARAM_VALIDATE_FAILED.getCode(), msg);
    }

    /**
     * 缺少必填请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("[缺少参数] {}", e.getParameterName());
        return Result.failed(ResultCode.BAD_REQUEST.getCode(), "缺少必要参数: " + e.getParameterName());
    }

    /**
     * 请求体反序列化失败（JSON 格式错误）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("[请求体解析失败] {}", e.getMessage());
        return Result.failed(ResultCode.BAD_REQUEST.getCode(), "请求体格式错误");
    }

    /**
     * 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("[方法不支持] {}", e.getMessage());
        return Result.failed(ResultCode.METHOD_NOT_ALLOWED.getCode(), e.getMessage());
    }

    /**
     * Sentinel 流控（限流）：QPS/线程数超阈值触发，返回 4290 限流码
     */
    @ExceptionHandler(FlowException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Result<Void> handleFlow(FlowException e, HttpServletRequest request) {
        FlowRule rule = e.getRule();
        log.warn("[限流] uri={}, resource={}, limit={}",
                request.getRequestURI(),
                rule != null ? rule.getResource() : "?",
                rule != null ? rule.getCount() : "?");
        return Result.failed(ResultCode.RATE_LIMITED.getCode(), ResultCode.RATE_LIMITED.getMessage());
    }

    /**
     * Sentinel 熔断降级：慢调用比例/异常比例/异常数熔断触发，返回 5003 熔断码
     */
    @ExceptionHandler(DegradeException.class)
    public Result<Void> handleDegrade(DegradeException e, HttpServletRequest request) {
        DegradeRule rule = e.getRule();
        log.warn("[熔断降级] uri={}, resource={}, timeWindow={}s",
                request.getRequestURI(),
                rule != null ? rule.getResource() : "?",
                rule != null ? rule.getTimeWindow() : "?");
        return Result.failed(ResultCode.CIRCUIT_BREAKER_OPEN.getCode(), ResultCode.CIRCUIT_BREAKER_OPEN.getMessage());
    }

    /**
     * Sentinel 系统规则/授权规则兜底：其余 BlockException 子类，返回 4291 降级码
     */
    @ExceptionHandler(BlockException.class)
    public Result<Void> handleBlock(BlockException e, HttpServletRequest request) {
        log.warn("[Sentinel拦截] uri={}, type={}", request.getRequestURI(), e.getClass().getSimpleName());
        return Result.failed(ResultCode.SERVICE_DEGRADED.getCode(), ResultCode.SERVICE_DEGRADED.getMessage());
    }

    /**
     * 兜底异常：未预期的系统异常，记录 error 级别并打印堆栈
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("[系统异常] uri={}", request.getRequestURI(), e);
        return Result.failed(ResultCode.SYSTEM_ERROR.getCode(), "系统繁忙，请稍后再试");
    }
}
