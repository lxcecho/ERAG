package com.knowledge.ai.prompt.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.ai.prompt.entity.PromptVariable;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Prompt 变量定义 Mapper（操作 prompt_variable 表）。
 * <p>该表无 tenant_id 列，租户隔离由 prompt_template 表负责；
 * 接口级 {@link InterceptorIgnore} 关闭租户拦截器。
 * 通用 CRUD（insert/deleteById）由 {@link BaseMapper} 提供。
 *
 * @author: lxcechoo@gmail.com
 */
@InterceptorIgnore(tenantLine = "true")
@Mapper
public interface PromptVariableMapper extends BaseMapper<PromptVariable> {

    /**
     * 查某模板的全部变量（未删除，按 id 排序保证聚合顺序稳定）。
     */
    @Select("SELECT id, template_id, var_name, description, required, default_value, deleted, create_time, update_time "
            + "FROM prompt_variable "
            + "WHERE deleted = 0 AND template_id = #{templateId} "
            + "ORDER BY id ASC")
    List<PromptVariable> listByTemplateId(@Param("templateId") Long templateId);

    /**
     * 物理删除某模板的全部变量（编辑变量前先清旧，再插新）。
     * <p>变量定义为模板级配置，整批替换无审计价值；软删会因唯一键 {@code uk_tpl_var (template_id, var_name)}
     * 未包含 deleted 字段导致重新插入同名变量时触发 DuplicateKeyException，故改物理删除。
     */
    @Delete("DELETE FROM prompt_variable WHERE template_id = #{templateId}")
    int deleteByTemplateId(@Param("templateId") Long templateId);
}
