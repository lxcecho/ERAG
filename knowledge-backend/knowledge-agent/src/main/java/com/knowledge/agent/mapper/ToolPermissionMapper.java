package com.knowledge.agent.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.agent.entity.ToolPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工具权限授权 Mapper。
 * <p>权限记录按精确租户查询（无平台继承，每个租户独立管理授权）。
 * 用 {@link InterceptorIgnore} 关闭租户拦截器，手工注入 {@code tenant_id = #{tenantId}} 条件，
 * 避免依赖 TenantContext（工具权限校验发生在设置租户上下文之前）。
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface ToolPermissionMapper extends BaseMapper<ToolPermission> {

    /** 查询某租户下某工具的全部授权记录（T=租户级 + R=角色级）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM tool_permission "
            + "WHERE tool_name = #{toolName} AND tenant_id = #{tenantId}")
    List<ToolPermission> selectByTenantAndTool(@Param("tenantId") Long tenantId,
                                                @Param("toolName") String toolName);
}
