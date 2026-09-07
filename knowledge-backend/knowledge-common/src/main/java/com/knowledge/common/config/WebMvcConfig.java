package com.knowledge.common.config;

import com.knowledge.common.tenant.TenantContextCleanInterceptor;
import com.knowledge.common.tenant.TenantProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 全局配置
 * <p>1. 跨域策略：前后端分离开发端口不同，需开放 CORS（Security 过滤链同步开启 cors()）。
 * <p>2. 注册租户上下文清理拦截器（作为 Servlet Filter finally 的双保险）。
 * <p>3. 启用多租户配置属性。
 * <p>4. 头像静态资源映射：{@code /avatars/**} → {@code ./data/avatars/}（用户头像本地存储访问）。
 *
 * @author: lxcechoo@gmail.com
 */
@Configuration
@EnableConfigurationProperties(TenantProperties.class)
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantContextCleanInterceptor tenantCleanInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 最高优先级：在所有业务拦截器前/后均执行清理，防嵌套请求遗漏
        registry.addInterceptor(tenantCleanInterceptor)
                .addPathPatterns("/**")
                .order(Integer.MIN_VALUE);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 用户头像静态资源（AvatarStorageService 落盘目录）
        registry.addResourceHandler("/avatars/**")
                .addResourceLocations("file:./data/avatars/");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 使用 OriginPattern 以兼容 allowCredentials=true 的场景
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
