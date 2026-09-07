package com.knowledge.common.tenant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * 多租户配置：
 * - enabled：总开关（false 时所有拦截器不生效，便于演示期关闭多租户模式）
 * - default-tenant-code：未指定租户时默认的租户编码（demo=1号租户）
 * - ignore-tables：MP 拦截器忽略的表名（平台级表，不做租户隔离）
 * - ignore-paths：Web 过滤器忽略的请求路径（登录、接口文档、SSE 白名单等）
 * - ignore-header：请求头名（X-Tenant-Code / X-Tenant-Id）
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@ConfigurationProperties(prefix = "tenant")
public class TenantProperties {

    private boolean enabled = false;

    private String defaultTenantCode = "demo";

    private String headerTenantCode = "X-Tenant-Code";

    private String headerTenantId = "X-Tenant-Id";

    /** 平台级表：忽略租户条件（sys_tenant/sys_menu + 字典/日志按策略） */
    private Set<String> ignoreTables = new HashSet<>(Set.of(
            "sys_tenant",
            "sys_tenant_menu",
            "sys_menu"
    ));

    /** 登录、接口文档、健康检查等：不要求租户上下文 */
    private Set<String> ignorePaths = new HashSet<>(Set.of(
            "/auth/login",
            "/auth/register",
            "/auth/captcha",
            "/actuator/health",
            "/doc.html",
            "/webjars",
            "/swagger-ui",
            "/v3/api-docs",
            "/favicon.ico"
    ));
}
