package com.knowledge.common.tenant;

import com.knowledge.common.context.TenantContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 多租户上下文过滤器：
 * - 优先级最高（Order=HIGHEST_PRECEDENCE），在 Spring Security/JWT 过滤器之前执行，先确定租户；
 * - 优先级高于 MP：保证执行到 Mapper 时 TenantContext 已有 tenantId；
 * - 双保险：finally 清理，防内存泄漏 + 线程池复用串租户。
 * <p>
 * 解析顺序（先到先得）：
 *   1) ignore-path 白名单 → 不设租户（MP 里该类请求尽量不碰业务表）；
 *   2) X-Tenant-Id 请求头（数字，运维/跨租户场景直接指定）；
 *   3) X-Tenant-Code 请求头（字符串编码，登录时按编码找租户ID）。
 * <p>
 * 登录成功后 JWT 会把 tenantId 写入 claim，所以 JwtAuthenticationFilter 会**再次覆盖**此处的
 * 租户值（JWT 才是权威来源）。本 Filter 的作用是：未登录请求（登录接口本身、注册、公共查询）也有租户上下文。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TenantContextFilter implements Filter {

    private final TenantProperties props;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        try {
            if (props.isEnabled()) {
                String path = req.getRequestURI();
                if (!isIgnored(path)) {
                    Long tid = parseTenantId(req);
                    if (tid != null) {
                        TenantContext.setTenantId(tid);
                    }
                }
            }
            chain.doFilter(req, resp);
        } finally {
            // 双重保险：请求出参一定 clear（即使 MP/Security 有一个抛异常）
            TenantContext.clear();
        }
    }

    private Long parseTenantId(HttpServletRequest req) {
        // 1) 直接指定 ID（最权威）
        String hdrId = req.getHeader(props.getHeaderTenantId());
        if (StringUtils.hasText(hdrId)) {
            try {
                return Long.parseLong(hdrId.trim());
            } catch (NumberFormatException e) {
                log.warn("[Tenant] X-Tenant-Id 不是合法数字: {}", hdrId);
            }
        }
        // 2) 按编码映射 ID（这里仅留占位：登录接口按 tenant_code 查 sys_tenant.id 后再覆盖 JWT claim）
        //    过滤器层不直接查 DB，避免每次请求都 hit 一次库。
        return null;
    }

    private boolean isIgnored(String path) {
        if (path == null) return true;
        for (String prefix : props.getIgnorePaths()) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }
}
