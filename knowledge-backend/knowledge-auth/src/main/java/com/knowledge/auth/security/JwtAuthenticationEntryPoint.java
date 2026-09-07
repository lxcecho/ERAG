package com.knowledge.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.common.result.Result;
import com.knowledge.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未认证访问处理器：访问需鉴权资源但未携带有效 token 时返回 401 JSON
 *
 * @author: lxcechoo@gmail.com
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException e) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Result<Void> result = Result.failed(ResultCode.UNAUTHORIZED.getCode(), "未登录或登录已过期");
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
