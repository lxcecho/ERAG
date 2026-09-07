package com.knowledge.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.common.result.Result;
import com.knowledge.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 无权限处理器：已认证但权限不足时返回 403 JSON（过滤链层面的拒绝）
 *
 * @author: lxcechoo@gmail.com
 */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException e) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Result<Void> result = Result.failed(ResultCode.FORBIDDEN.getCode(), "无权限访问");
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
