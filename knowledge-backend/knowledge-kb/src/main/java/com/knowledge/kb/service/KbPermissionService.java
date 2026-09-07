package com.knowledge.kb.service;

import com.knowledge.kb.constant.KbRole;

/**
 * 知识库权限校验服务（门面）。
 * <p>统一封装"用户对某知识库的权限判定"，业务层调用 checkXxx 即可，
 * 不通过则抛出 {@link com.knowledge.common.exception.BizException}（全局异常处理器转为 403 提示）。
 * <p>超级管理员（sys_role=admin）绕过所有校验。
 *
 * @author: lxcechoo@gmail.com
 */
public interface KbPermissionService {

    /** 查询用户在知识库中的角色；非成员返回 null（admin 返回 OWNER） */
    KbRole getRole(Long kbId, Long userId);

    /** 校验查看权限（viewer+），不通过抛异常 */
    void checkViewer(Long kbId, Long userId);

    /** 校验编辑权限（editor+），不通过抛异常 */
    void checkEditor(Long kbId, Long userId);

    /** 校验所有者权限（owner），不通过抛异常 */
    void checkOwner(Long kbId, Long userId);
}
