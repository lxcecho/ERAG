package com.knowledge.agent.tool;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * {@link ToolUserRolesResolver} 默认空实现。
 * <p>返回空集 → 角色级授权(tool_permission subject_type='R')在无真实实现时不阻断（默认开放）。
 * 后续接 sys_user_role 实现时，用 @Primary 或替换该 bean 即可生效角色级授权。
 *
 * @author: lxcechoo@gmail.com
 */
@Component
public class NoopToolUserRolesResolver implements ToolUserRolesResolver {

    @Override
    public Set<Long> resolveRoleIds(Long tenantId, Long userId) {
        return Set.of();
    }
}
