package com.knowledge.auth.advice;

import com.knowledge.common.result.Result;
import com.knowledge.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 认证授权异常处理器
 * 处理 Spring Security 抛出的认证/授权异常，转换为统一 Result。
 * 与 common.GlobalExceptionHandler 协作，此处更具体，优先匹配。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@RestControllerAdvice
public class AuthExceptionHandler {

    /** 用户名或密码错误 */
    @ExceptionHandler(BadCredentialsException.class)
    public Result<Void> handleBadCredentials(BadCredentialsException e) {
        log.warn("[认证失败] {}", e.getMessage());
        return Result.failed(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
    }

    /** 账号已停用 */
    @ExceptionHandler(DisabledException.class)
    public Result<Void> handleDisabled(DisabledException e) {
        log.warn("[账号停用] {}", e.getMessage());
        return Result.failed(ResultCode.FORBIDDEN.getCode(), "账号已停用");
    }

    /** 其他认证异常 */
    @ExceptionHandler(AuthenticationException.class)
    public Result<Void> handleAuthentication(AuthenticationException e) {
        log.warn("[认证异常] {}", e.getMessage());
        return Result.failed(ResultCode.UNAUTHORIZED.getCode(), "认证失败");
    }

    /** 权限不足（方法级 @PreAuthorize 抛出） */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("[权限不足] {}", e.getMessage());
        return Result.failed(ResultCode.FORBIDDEN.getCode(), "无权限访问");
    }
}
