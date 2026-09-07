package com.knowledge.agent.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.agent.entity.ToolMetadata;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 工具元数据 Mapper。
 * <p>租户可见性：当前租户 + 平台预置（tenant_id=0）。MP 租户拦截器只能注入单一 tenant_id 条件，
 * 无法表达"租户 OR 平台"继承，故下列查询均用 {@link InterceptorIgnore} 关闭拦截器，
 * 手工注入 {@code (tenant_id = #{tenantId} OR tenant_id = 0)} 条件（与 prompt 模块一致）。
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface ToolMetadataMapper extends BaseMapper<ToolMetadata> {

    /**
     * 按租户+工具名查可见元数据（租户自有优先于平台预置）。
     * <p>{@code ORDER BY tenant_id DESC LIMIT 1} 使租户自有(tenant_id>0)排在平台(0)之前。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM tool_metadata "
            + "WHERE deleted = 0 AND tool_name = #{toolName} "
            + "AND (tenant_id = #{tenantId} OR tenant_id = 0) "
            + "ORDER BY tenant_id DESC LIMIT 1")
    ToolMetadata selectVisibleByTenantAndName(@Param("tenantId") Long tenantId,
                                               @Param("toolName") String toolName);

    /**
     * 列出租户可见的全部工具元数据（租户自有覆盖平台预置，按 tool_name 去重取租户优先）。
     * <p>ROW_NUMBER PARTITION BY tool_name ORDER BY tenant_id DESC 取 rn=1，保证同工具只返回一行。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM ( "
            + "  SELECT *, ROW_NUMBER() OVER (PARTITION BY tool_name ORDER BY tenant_id DESC) AS rn "
            + "  FROM tool_metadata "
            + "  WHERE deleted = 0 AND (tenant_id = #{tenantId} OR tenant_id = 0) "
            + ") ranked WHERE ranked.rn = 1 ORDER BY ranked.sort_order")
    List<ToolMetadata> listVisibleByTenant(@Param("tenantId") Long tenantId);

    /** 按精确租户+工具名查（不含平台预置，用于 register 查重/定位租户自有行）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM tool_metadata "
            + "WHERE deleted = 0 AND tenant_id = #{tenantId} AND tool_name = #{toolName}")
    ToolMetadata selectExactByTenantAndName(@Param("tenantId") Long tenantId,
                                             @Param("toolName") String toolName);

    /** 更新当前生效版本号（精确租户匹配）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE tool_metadata SET current_version = #{version} "
            + "WHERE deleted = 0 AND tenant_id = #{tenantId} AND tool_name = #{toolName}")
    int updateCurrentVersion(@Param("tenantId") Long tenantId,
                              @Param("toolName") String toolName,
                              @Param("version") String version);

    /** 更新启用状态（精确租户匹配）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE tool_metadata SET enabled = #{enabled} "
            + "WHERE deleted = 0 AND tenant_id = #{tenantId} AND tool_name = #{toolName}")
    int updateEnabled(@Param("tenantId") Long tenantId,
                       @Param("toolName") String toolName,
                       @Param("enabled") Integer enabled);
}
