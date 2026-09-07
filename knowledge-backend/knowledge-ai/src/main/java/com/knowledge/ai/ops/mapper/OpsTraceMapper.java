package com.knowledge.ai.ops.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.ai.ops.entity.OpsTrace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 分布式追踪 Mapper。
 * <p>查询用 {@link InterceptorIgnore} 关闭租户拦截器，手工注入 tenant_id（同 AiCallLogMapper 模式）。
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface OpsTraceMapper extends BaseMapper<OpsTrace> {

    /** 分页查询 trace 列表（按 trace_id 去重展示，取每条 trace 的 ROOT span） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT t.id, t.tenant_id, t.trace_id, t.span_id, t.span_name, t.span_type, "
            + "t.start_time, t.duration_ms, t.status, t.attributes_json "
            + "FROM ops_trace t "
            + "INNER JOIN (SELECT trace_id, MIN(start_time) AS min_start "
            + "  FROM ops_trace WHERE tenant_id = #{tenantId} "
            + "  <if test='q.spanType != null and q.spanType != \"\"'> AND span_type = #{q.spanType} </if>"
            + "  <if test='q.start != null and q.start != \"\"'> AND start_time &gt;= #{q.start} </if>"
            + "  <if test='q.end != null and q.end != \"\"'> AND start_time &lt;= #{q.end} </if>"
            + "  GROUP BY trace_id) g ON t.trace_id = g.trace_id AND t.start_time = g.min_start "
            + "WHERE t.tenant_id = #{tenantId} "
            + "<if test='q.traceId != null and q.traceId != \"\"'> AND t.trace_id = #{q.traceId} </if>"
            + "ORDER BY t.start_time DESC"
            + "</script>")
    IPage<OpsTrace> pageRootSpans(IPage<OpsTrace> page,
                                  @Param("tenantId") Long tenantId,
                                  @Param("q") com.knowledge.ai.ops.dto.TraceQuery q);

    /** 查询单个 trace 的全部 span（构建 span 树） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id, tenant_id, trace_id, span_id, parent_span_id, span_name, span_type, "
            + "start_time, duration_ms, status, attributes_json "
            + "FROM ops_trace WHERE tenant_id = #{tenantId} AND trace_id = #{traceId} "
            + "ORDER BY start_time")
    List<OpsTrace> listByTraceId(@Param("tenantId") Long tenantId,
                                 @Param("traceId") String traceId);
}
