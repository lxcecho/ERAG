package com.knowledge.ai.prompt.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.ai.prompt.dto.PromptTemplateQuery;
import com.knowledge.ai.prompt.dto.PromptTemplateVO;
import com.knowledge.ai.prompt.entity.PromptTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Prompt 模板元数据 Mapper（三表拆分后仅操作 prompt_template 元数据表）。
 * <p>查询方法通过 JOIN prompt_version 聚合返回 {@link PromptTemplateVO}（含版本内容），
 * 并以 GROUP_CONCAT 子查询聚合 prompt_variable 为逗号字符串填 VO.variables。
 * <p>租户可见性：当前租户 + 平台预置（tenant_id=0）。MP 自动租户拦截器只能注入单一
 * tenant_id 条件，故下列查询均用 {@link InterceptorIgnore} 关闭拦截器并手工注入
 * {@code (tenant_id = #{tenantId} OR tenant_id = 0)} 条件，避免拦截器对含窗口函数/子查询
 * 的复杂 SQL 改写破坏。
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {

    /**
     * 分页查询每个 prompt_code 的最新版本（主列表：一行一个 Prompt）。
     * <p>JOIN template + version，PARTITION BY prompt_code ORDER BY version DESC 取 rn=1。
     * 租户可见性：当前租户 + 平台预置（tenant_id=0）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT * FROM ( "
            + "  SELECT v.id, t.id AS template_id, t.tenant_id, t.prompt_code, t.name, t.type, "
            + "         v.version, v.content, v.status, v.remark, v.creator_id, "
            + "         v.create_time, v.update_time, "
            + "         COALESCE((SELECT GROUP_CONCAT(pv.var_name) FROM prompt_variable pv "
            + "                   WHERE pv.template_id = t.id AND pv.deleted = 0), '') AS variables, "
            + "         ROW_NUMBER() OVER (PARTITION BY t.prompt_code ORDER BY v.version DESC) AS rn "
            + "  FROM prompt_template t "
            + "  INNER JOIN prompt_version v ON v.template_id = t.id AND v.deleted = 0 "
            + "  WHERE t.deleted = 0 AND (t.tenant_id = #{tenantId} OR t.tenant_id = 0) "
            + "  <if test='query.name != null and query.name != \"\"'> AND t.name LIKE CONCAT('%', #{query.name}, '%') </if>"
            + "  <if test='query.type != null and query.type != \"\"'> AND t.type = #{query.type} </if>"
            + "  <if test='query.promptCode != null and query.promptCode != \"\"'> AND t.prompt_code LIKE CONCAT('%', #{query.promptCode}, '%') </if>"
            + ") ranked WHERE ranked.rn = 1 ORDER BY ranked.create_time DESC"
            + "</script>")
    IPage<PromptTemplateVO> pageLatestPerCode(IPage<PromptTemplateVO> page,
                                               @Param("tenantId") Long tenantId,
                                               @Param("query") PromptTemplateQuery query);

    /**
     * 按版本ID查询聚合 VO（含平台预置，用于详情/编辑前加载）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT v.id, t.id AS template_id, t.tenant_id, t.prompt_code, t.name, t.type, "
            + "v.version, v.content, v.status, v.remark, v.creator_id, v.create_time, v.update_time, "
            + "COALESCE((SELECT GROUP_CONCAT(pv.var_name) FROM prompt_variable pv "
            + "          WHERE pv.template_id = t.id AND pv.deleted = 0), '') AS variables "
            + "FROM prompt_version v "
            + "INNER JOIN prompt_template t ON t.id = v.template_id AND t.deleted = 0 "
            + "WHERE v.deleted = 0 AND v.id = #{id} "
            + "AND (t.tenant_id = #{tenantId} OR t.tenant_id = 0)")
    PromptTemplateVO findVoByIdVisible(@Param("tenantId") Long tenantId,
                                        @Param("id") Long versionId);

    /**
     * 某个 prompt_code 的全部版本（按版本倒序），租户可见性同上。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT v.id, t.id AS template_id, t.tenant_id, t.prompt_code, t.name, t.type, "
            + "v.version, v.content, v.status, v.remark, v.creator_id, v.create_time, v.update_time, "
            + "COALESCE((SELECT GROUP_CONCAT(pv.var_name) FROM prompt_variable pv "
            + "          WHERE pv.template_id = t.id AND pv.deleted = 0), '') AS variables "
            + "FROM prompt_version v "
            + "INNER JOIN prompt_template t ON t.id = v.template_id AND t.deleted = 0 "
            + "WHERE v.deleted = 0 AND t.prompt_code = #{promptCode} "
            + "AND (t.tenant_id = #{tenantId} OR t.tenant_id = 0) "
            + "ORDER BY v.version DESC")
    List<PromptTemplateVO> listVersionsByCode(@Param("tenantId") Long tenantId,
                                               @Param("promptCode") String promptCode);

    /**
     * 取某个 prompt_code 的已发布版本（租户自有优先于平台预置）。
     * <p>{@code ORDER BY t.tenant_id DESC} 使租户自有(tenant_id>0)排在平台(0)之前，
     * 同租户内再按 version DESC 取最新。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT v.id, t.id AS template_id, t.tenant_id, t.prompt_code, t.name, t.type, "
            + "v.version, v.content, v.status, v.remark, v.creator_id, v.create_time, v.update_time, "
            + "COALESCE((SELECT GROUP_CONCAT(pv.var_name) FROM prompt_variable pv "
            + "          WHERE pv.template_id = t.id AND pv.deleted = 0), '') AS variables "
            + "FROM prompt_version v "
            + "INNER JOIN prompt_template t ON t.id = v.template_id AND t.deleted = 0 "
            + "WHERE v.deleted = 0 AND t.prompt_code = #{promptCode} AND v.status = 'PUBLISHED' "
            + "AND (t.tenant_id = #{tenantId} OR t.tenant_id = 0) "
            + "ORDER BY t.tenant_id DESC, v.version DESC LIMIT 1")
    PromptTemplateVO getPublishedByCode(@Param("tenantId") Long tenantId,
                                         @Param("promptCode") String promptCode);

    /**
     * 按 prompt_code + 精确租户查模板元数据（不含平台预置，用于 create 查重 / 派生时定位租户自有 template_id）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id, tenant_id, prompt_code, name, type, creator_id, deleted, create_time, update_time "
            + "FROM prompt_template "
            + "WHERE deleted = 0 AND tenant_id = #{tenantId} AND prompt_code = #{promptCode}")
    PromptTemplate findByCode(@Param("tenantId") Long tenantId,
                               @Param("promptCode") String promptCode);
}
