package com.knowledge.common.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 多租户上下文：贯穿请求全链路的「当前租户ID」容器。
 * <p>
 * 技术选型：
 * - 使用 TransmittableThreadLocal（TTL）而非 InheritableThreadLocal / ThreadLocal：
 *   项目中大量使用 @Async("docAsyncExecutor"/"operLogExecutor") 线程池，而 ITL 仅在新建线程时传递，
 *   线程池复用线程时子线程变量不会刷新，会造成经典的"串租户"事故；
 *   TTL 是阿里巴巴标准库，通过装饰线程池/Executor 在线程提交时捕获并重置上下文变量，保证正确性。
 * - 清理策略：Filter + HandlerInterceptor 双保险 finally 清理，防内存泄漏与线程池复用污染。
 *
 * @author: lxcechoo@gmail.com
 */
public final class TenantContext {

    private TenantContext() {
    }

    /** 平台保留租户ID（0号=平台运维租户） */
    public static final long PLATFORM_TENANT_ID = 0L;

    /** 演示默认租户ID（1号=demo） */
    public static final long DEMO_TENANT_ID = 1L;

    private static final TransmittableThreadLocal<Long> TENANT = new TransmittableThreadLocal<>();

    /** 设置当前租户（为 null 时清理） */
    public static void setTenantId(Long tenantId) {
        if (tenantId == null) {
            clear();
        } else {
            TENANT.set(tenantId);
        }
    }

    /** 获取当前租户ID，可能为 null（未登录 / 白名单接口） */
    public static Long getTenantId() {
        return TENANT.get();
    }

    /** 必须获取租户ID，否则抛异常（MP 拦截器、向量库、ES 等强隔离场景调用） */
    public static Long requiredTenantId() {
        Long id = TENANT.get();
        if (id == null) {
            throw new IllegalStateException("当前请求未指定租户，请先登录或在请求头携带 X-Tenant-Code");
        }
        return id;
    }

    /** 当前是否处于平台上下文（tenant=0） */
    public static boolean isPlatformContext() {
        Long id = TENANT.get();
        return id != null && id == PLATFORM_TENANT_ID;
    }

    /** 清理上下文（请求出参前必须调用） */
    public static void clear() {
        TENANT.remove();
    }
}
