package com.knowledge.auth.util;

import com.knowledge.auth.security.SecurityUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具：在任意业务层获取当前登录用户信息。
 * <p>从 {@link SecurityContextHolder} 取出 {@link SecurityUserDetails}，
 * 避免各业务模块重复书写 SecurityContext 解包逻辑。
 *
 * @author: lxcechoo@gmail.com
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** 获取当前登录用户详情（未登录抛异常） */
    public static SecurityUserDetails currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityUserDetails details)) {
            throw new IllegalStateException("无法获取当前登录用户");
        }
        return details;
    }

    /** 当前登录用户 ID */
    public static Long currentUserId() {
        return currentUser().getUserId();
    }

    /** 当前登录用户名 */
    public static String currentUsername() {
        return currentUser().getUsername();
    }
}
