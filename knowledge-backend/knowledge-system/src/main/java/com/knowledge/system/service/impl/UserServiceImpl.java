package com.knowledge.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowledge.common.context.TenantContext;
import com.knowledge.system.entity.User;
import com.knowledge.system.mapper.UserMapper;
import com.knowledge.system.service.UserService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
/**
 *
 * @author: lxcechoo@gmail.com
 */
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public User getByUsername(String username) {
        Long tid = TenantContext.getTenantId();
        if (tid != null) {
            // 多租户上下文已存在：直接按 tenant+username 精准匹配（防串租户）
            return getByTenantIdAndUsername(tid, username);
        }
        // 兼容单租户模式：MP 全局拦截器会补条件，不会产生串租户
        return lambdaQuery().eq(User::getUsername, username).one();
    }

    @Override
    public User getByTenantIdAndUsername(Long tenantId, String username) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, tenantId)
                .eq(User::getUsername, username));
    }

    @Override
    public List<String> listPermsByUserId(Long userId) {
        List<String> perms = baseMapper.selectPermsByUserId(userId);
        return perms != null ? perms : Collections.emptyList();
    }

    @Override
    public List<String> listRoleKeysByUserId(Long userId) {
        List<String> roles = baseMapper.selectRoleKeysByUserId(userId);
        return roles != null ? roles : Collections.emptyList();
    }
}
