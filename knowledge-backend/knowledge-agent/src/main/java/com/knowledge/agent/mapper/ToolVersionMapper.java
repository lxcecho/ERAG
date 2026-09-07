package com.knowledge.agent.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.agent.entity.ToolVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 工具版本历史 Mapper。
 * <p>版本查询含平台预置（tenant_id=0），用 {@link InterceptorIgnore} 关闭租户拦截器，
 * 手工注入 {@code (tenant_id = #{tenantId} OR tenant_id = 0)} 条件。
 * <p>归档操作按精确租户匹配，只归档同租户范围的旧 PUBLISHED 版本。
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface ToolVersionMapper extends BaseMapper<ToolVersion> {

    /** 列出工具的全部版本（租户可见，含平台预置），按版本倒序。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM tool_version "
            + "WHERE deleted = 0 AND tool_name = #{toolName} "
            + "AND (tenant_id = #{tenantId} OR tenant_id = 0) "
            + "ORDER BY version DESC")
    List<ToolVersion> listVisibleByTenantAndName(@Param("tenantId") Long tenantId,
                                                  @Param("toolName") String toolName);

    /** 归档同租户范围的旧 PUBLISHED 版本（精确租户匹配，发布新版本前调用）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE tool_version SET status = 'ARCHIVED' "
            + "WHERE deleted = 0 AND tenant_id = #{tenantId} AND tool_name = #{toolName} "
            + "AND status = 'PUBLISHED'")
    int archivePublished(@Param("tenantId") Long tenantId,
                          @Param("toolName") String toolName);
}
