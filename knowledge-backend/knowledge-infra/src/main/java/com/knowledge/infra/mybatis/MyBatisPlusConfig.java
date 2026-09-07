package com.knowledge.infra.mybatis;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.tenant.TenantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：分页插件 + 多租户行级隔离插件。
 * <p>
 * 隔离机制（两级防御）：
 * 1. SELECT：自动拼 AND tenant_id = ?
 * 2. UPDATE/DELETE：自动拼 AND tenant_id = ?（防跨租户误删）
 * 3. INSERT：若 SQL 中未指定 tenant_id，自动把 TenantContext.requiredTenantId() 注入列值
 *    （注：若 Entity.setTenantId 已赋值，以实体字段值为准——推荐在 Service 层写 setTenantId
 *     作为兜底，避免租户ID依赖拦截器而存在"漏拦截"风险）
 * <p>
 * 重要：平台级表（sys_tenant / sys_menu 等）在 ignoreTables 中，不追加 tenant 条件。
 * <p>
 * 经验教训（来自类似项目）：
 * - TenantLineHandler.getTenantId() 若与外部方法同名会造成编译器绑定歧义，因此命名为
 *   resolveCurrentTenantId()，内部只做 LongValue 构造。
 * - 租户缺失时：若 tenant.enabled=true 直接抛 IllegalStateException（安全优先：不允许
 *   产生 tenant_id=0/null 的伪数据），由上层 Filter + JwtAuthenticationFilter 保证设置。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(TenantProperties.class)
@RequiredArgsConstructor
public class MyBatisPlusConfig {

    private final TenantProperties tenantProps;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 多租户行级隔离插件：注意顺序，必须在分页插件之前
        if (tenantProps.isEnabled()) {
            log.info("[MyBatis-Plus] 启用多租户行级隔离插件");
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler(tenantProps)));
        }

        // 2. 分页插件（MySQL 方言）
        PaginationInnerInterceptor pageInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        pageInterceptor.setMaxLimit(500L);
        pageInterceptor.setOverflow(false);
        interceptor.addInnerInterceptor(pageInterceptor);

        return interceptor;
    }

    /**
     * 租户行处理器：独立内部类，避免与外部 getTenantId 命名冲突（经验教训见 Failure Experience）。
     */
    @RequiredArgsConstructor
    private static class TenantLineHandler
            implements com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler {

        private final TenantProperties props;

        /** 列名：与 MySQL 列名保持一致（驼峰配置会自动转换，但拦截器直接用列名，写下划线） */
        @Override
        public net.sf.jsqlparser.expression.Expression getTenantId() {
            return new LongValue(TenantContext.requiredTenantId());
        }

        @Override
        public boolean ignoreTable(String tableName) {
            // 1) 配置白名单：平台级表；2) 严格大小写不敏感匹配
            return props.getIgnoreTables().stream()
                    .anyMatch(t -> t.equalsIgnoreCase(tableName));
        }
    }
}
