package com.knowledge.kb.governance.lifecycle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.kb.entity.KbDocAuditLog;
import com.knowledge.kb.governance.lifecycle.dto.AuditQuery;
import com.knowledge.kb.governance.lifecycle.dto.AuditVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 知识审计 Mapper：联表 kb_document 分页（kb_doc_audit_log 无 kb_id 列，需 JOIN 取知识库归属）。
 * <p>kb_doc_audit_log 不走行级租户拦截器，故查询显式带 tenant_id 条件（由 Service 从 TenantContext 注入）。
 *
 * @author: lxcechoo@gmail.com
 */
public interface KnowledgeAuditMapper extends BaseMapper<KbDocAuditLog> {

    @Select("""
            <script>
            SELECT a.id, a.tenant_id AS tenantId, a.user_id AS userId, a.doc_id AS docId,
                   d.original_name AS docName, a.action, a.result,
                   a.pass_reason AS passReason, a.deny_reason AS denyReason,
                   a.ip, a.user_agent AS userAgent, a.create_time AS createTime
            FROM kb_doc_audit_log a
            LEFT JOIN kb_document d ON a.doc_id = d.id AND d.deleted = 0
            WHERE 1 = 1
              <if test="query.tenantId != null"> AND a.tenant_id = #{query.tenantId} </if>
              <if test="query.kbId != null"> AND d.kb_id = #{query.kbId} </if>
              <if test="query.docId != null"> AND a.doc_id = #{query.docId} </if>
              <if test="query.action != null and query.action != ''"> AND a.action = #{query.action} </if>
              <if test="query.result != null and query.result != ''"> AND a.result = #{query.result} </if>
              <if test="query.userId != null"> AND a.user_id = #{query.userId} </if>
              <if test="query.beginTime != null"> AND a.create_time &gt;= #{query.beginTime} </if>
              <if test="query.endTime != null"> AND a.create_time &lt;= #{query.endTime} </if>
            ORDER BY a.create_time DESC
            </script>
            """)
    IPage<AuditVo> selectAuditPage(IPage<AuditVo> page, @Param("query") AuditQuery query);
}
