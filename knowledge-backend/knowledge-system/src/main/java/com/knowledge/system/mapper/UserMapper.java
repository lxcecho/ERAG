package com.knowledge.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.system.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 Mapper
 * 除 BaseMapper 通用能力外，提供基于 RBAC 关联查询的权限/角色获取。
 *
 * @author: lxcechoo@gmail.com
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * 查询用户拥有的权限标识（去重，仅启用菜单）
     */
    @Select("SELECT DISTINCT m.perms FROM sys_user_role ur " +
            "INNER JOIN sys_role_menu rm ON ur.role_id = rm.role_id " +
            "INNER JOIN sys_menu m ON rm.menu_id = m.id " +
            "WHERE ur.user_id = #{userId} AND m.perms IS NOT NULL AND m.perms <> '' " +
            "AND m.status = 0 AND m.deleted = 0")
    List<String> selectPermsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户拥有的角色 key（去重，仅启用角色）
     */
    @Select("SELECT DISTINCT r.role_key FROM sys_user_role ur " +
            "INNER JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND r.status = 0 AND r.deleted = 0")
    List<String> selectRoleKeysByUserId(@Param("userId") Long userId);
}
