package com.knowledge.auth.jwt;

import com.knowledge.auth.config.JwtProperties;
import com.knowledge.auth.security.SecurityUserDetails;
import com.knowledge.common.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 * 从请求头解析 token，校验通过则加载用户权限并写入 SecurityContext。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        try {
            if (StringUtils.hasText(token) && jwtTokenProvider.validate(token)) {
                try {
                    // 多租户：先从 JWT claim 取 tenantId，写进上下文 —— 这是权威来源，会覆盖 Filter 层的 header 值
                    Long tenantId = jwtTokenProvider.getTenantId(token);
                    if (tenantId != null) {
                        TenantContext.setTenantId(tenantId);
                    }
                    String username = jwtTokenProvider.getUsername(token);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (Exception e) {
                    log.warn("JWT 认证失败: {}", e.getMessage());
                    SecurityContextHolder.clearContext();
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // 清理安全上下文 + 租户上下文（双重保险，避免线程池复用串租户）
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        // 优先从请求头解析
        String header = request.getHeader(jwtProperties.getHeader());
        if (StringUtils.hasText(header) && header.startsWith(jwtProperties.getPrefix())) {
            return header.substring(jwtProperties.getPrefix().length()).trim();
        }
        // SSE 白名单路径：EventSource 无法设置自定义请求头，回退从 query 参数 token 读取
        List<String> allowPaths = jwtProperties.getAllowQueryTokenPaths();
        if (allowPaths != null && !allowPaths.isEmpty()) {
            String servletPath = request.getServletPath();
            if (allowPaths.contains(servletPath)) {
                String token = request.getParameter("token");
                if (StringUtils.hasText(token)) {
                    return token;
                }
            }
        }
        return null;
    }

    /**
     * SSE/异步请求完成后 Tomcat 会二次 dispatch 重新进入 Security 过滤链；
     * 默认 OncePerRequestFilter 跳过 async dispatch，导致 SecurityContext 为空，
     * AuthorizationFilter 误报 Access Denied（响应已提交，纯日志噪音）。
     * 这里允许 async dispatch 重跑 JWT 认证（Authorization 头仍在），消除该误报。
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    /** 静态工具：从当前 SecurityContext 取登录用户 */
    public static SecurityUserDetails currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SecurityUserDetails details) {
            return details;
        }
        return null;
    }
}
