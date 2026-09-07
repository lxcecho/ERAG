package com.knowledge.agent.tool;

import java.util.Set;

/**
 * 用户角色解析器：解析某用户在某租户下拥有的角色ID集合。
 * <p>用于 {@link ToolMetadataService#isGranted} 的角色级授权判定（tool_permission subject_type='R'）。
 * <p>解耦设计：Agent 模块不直接依赖认证模块的 sys_user_role 表，通过该接口抽象；
 * 默认实现 {@code NoopToolUserRolesResolver} 返回空集（角色级授权默认不阻断，见计划"默认开放"），
 * 后续可提供接 sys_user_role 的真实实现替换默认 bean 即生效。
 *
 * @author: lxcechoo@gmail.com
 */
public interface ToolUserRolesResolver {

    /**
     * 解析用户角色ID集合。
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 角色ID集合（无角色或未实现时返回空集）
     */
    Set<Long> resolveRoleIds(Long tenantId, Long userId);
}
