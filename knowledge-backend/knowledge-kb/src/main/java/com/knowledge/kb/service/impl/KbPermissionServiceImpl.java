package com.knowledge.kb.service.impl;

import com.knowledge.auth.security.SecurityUserDetails;
import com.knowledge.auth.util.SecurityUtils;
import com.knowledge.common.exception.BizException;
import com.knowledge.kb.constant.KbRole;
import com.knowledge.kb.entity.KbMember;
import com.knowledge.kb.service.KbMemberService;
import com.knowledge.kb.service.KbPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 知识库权限校验服务实现。
 * <p>权限模型：admin 超管绕过 → 否则查 kb_member 角色 → 按等级判定。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbPermissionServiceImpl implements KbPermissionService {

    private final KbMemberService kbMemberService;

    @Override
    public KbRole getRole(Long kbId, Long userId) {
        // 超级管理员绕过
        if (isAdmin()) {
            return KbRole.OWNER;
        }
        KbMember member = kbMemberService.lambdaQuery()
                .eq(KbMember::getKbId, kbId)
                .eq(KbMember::getUserId, userId)
                .one();
        return member == null ? null : KbRole.of(member.getRole());
    }

    @Override
    public void checkViewer(Long kbId, Long userId) {
        check(kbId, userId, KbRole.VIEWER, "无访问该知识库的权限");
    }

    @Override
    public void checkEditor(Long kbId, Long userId) {
        check(kbId, userId, KbRole.EDITOR, "无编辑该知识库的权限（需 editor 及以上）");
    }

    @Override
    public void checkOwner(Long kbId, Long userId) {
        check(kbId, userId, KbRole.OWNER, "无管理该知识库的权限（需 owner）");
    }

    /**
     * 通用校验：用户角色等级 >= 要求等级。
     */
    private void check(Long kbId, Long userId, KbRole required, String msg) {
        KbRole role = getRole(kbId, userId);
        if (role == null || role.getLevel() < required.getLevel()) {
            throw new BizException(403, msg);
        }
    }

    /**
     * 当前登录用户是否为「平台级超管」或「当前租户的管理员」。
     * <p>多租户权限模型：
     * - platform_super_admin：跨所有租户的超管（0号平台租户用户）
     * - tenant_admin：当前租户内的管理员，管理本租户知识库
     * - tenant_user：普通用户，需通过 kb_member 授权
     */
    private boolean isAdmin() {
        try {
            SecurityUserDetails user = SecurityUtils.currentUser();
            if (user == null || user.getRoles() == null) return false;
            return user.getRoles().contains("platform_super_admin")
                    || user.getRoles().contains("tenant_admin")
                    // 兼容老数据（单租户时期遗留 role_key=admin）：将 admin 视为 tenant_admin
                    || user.getRoles().contains("admin");
        } catch (Exception e) {
            return false;
        }
    }
}
