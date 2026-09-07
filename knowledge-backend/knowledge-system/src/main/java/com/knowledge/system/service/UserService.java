package com.knowledge.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowledge.system.entity.User;

import java.util.List;

/**
 * 用户服务接口
 *
 * @author: lxcechoo@gmail.com
 */
public interface UserService extends IService<User> {

    /** 根据用户名查询用户（单租户模式 / 未开多租户时使用） */
    User getByUsername(String username);

    /** 根据租户 + 用户名查询（多租户模式：唯一键 uk_tenant_username） */
    User getByTenantIdAndUsername(Long tenantId, String username);

    /** 查询用户权限标识列表 */
    List<String> listPermsByUserId(Long userId);

    /** 查询用户角色 key 列表 */
    List<String> listRoleKeysByUserId(Long userId);
}
