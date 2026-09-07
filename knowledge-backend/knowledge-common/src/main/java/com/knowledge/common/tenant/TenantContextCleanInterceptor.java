package com.knowledge.common.tenant;

import com.knowledge.common.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * MVC 层拦截器：在 Handler 执行后再一次 clear。
 * <p>
 * 作为 TenantContextFilter.finally 的双保险——若 Servlet 过滤器链有异常分支，或线程池中派生的
 * Web 子上下文，此处能再兜底。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
public class TenantContextCleanInterceptor implements HandlerInterceptor {

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (TenantContext.getTenantId() != null) {
            log.trace("[Tenant] 清理租户上下文 tid={}", TenantContext.getTenantId());
            TenantContext.clear();
        }
    }
}
