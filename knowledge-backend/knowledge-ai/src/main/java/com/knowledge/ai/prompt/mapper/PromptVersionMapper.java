package com.knowledge.ai.prompt.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.ai.prompt.entity.PromptVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Prompt 版本内容 Mapper（操作 prompt_version 表）。
 * <p>该表无 tenant_id 列，租户隔离由 prompt_template 表负责；
 * 接口级 {@link InterceptorIgnore} 关闭租户拦截器，避免注入不存在的 tenant_id 列。
 * 通用 CRUD（insert/updateById/deleteById）由 {@link BaseMapper} 提供。
 *
 * @author: lxcechoo@gmail.com
 */
@InterceptorIgnore(tenantLine = "true")
@Mapper
public interface PromptVersionMapper extends BaseMapper<PromptVersion> {

    /**
     * 取某模板的最大版本号（派生新版本时使用，version 号同模板内递增）。
     */
    @Select("SELECT COALESCE(MAX(version), 0) FROM prompt_version "
            + "WHERE deleted = 0 AND template_id = #{templateId}")
    Integer maxVersionByTemplateId(@Param("templateId") Long templateId);

    /**
     * 将指定模板的 PUBLISHED 版本归档为 ARCHIVED。
     * <p>发布新版本前调用，保证同模板下至多一个 PUBLISHED。
     */
    @Update("UPDATE prompt_version SET status = 'ARCHIVED' "
            + "WHERE deleted = 0 AND template_id = #{templateId} AND status = 'PUBLISHED'")
    int archivePublished(@Param("templateId") Long templateId);
}
