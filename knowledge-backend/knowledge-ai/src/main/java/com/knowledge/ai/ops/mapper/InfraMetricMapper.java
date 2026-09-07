package com.knowledge.ai.ops.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.ai.ops.entity.InfraMetric;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 基础设施指标 Mapper。
 * <p>所有查询用 {@link InterceptorIgnore} 关闭 MP 租户拦截器，手工注入 tenant_id 条件
 * （同 AiCallLogMapper 模式）。审计日志无软删，直接物理查询。
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface InfraMetricMapper extends BaseMapper<InfraMetric> {

    /** 按资源聚合：调用次数 / 成功数 / 失败数 / 平均耗时（单资源单行） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT COUNT(*) AS calls, "
            + "SUM(success = 1) AS successCount, "
            + "SUM(success = 0) AS failedCount, "
            + "COALESCE(AVG(duration_ms), 0) AS avgDuration, "
            + "COALESCE(MAX(duration_ms), 0) AS maxDuration "
            + "FROM infra_metric "
            + "WHERE tenant_id = #{tenantId} AND resource = #{resource} "
            + "<if test='start != null and start != \"\"'> AND create_time &gt;= #{start} </if>"
            + "<if test='end != null and end != \"\"'> AND create_time &lt;= #{end} </if>"
            + "</script>")
    Map<String, Object> statsByResource(@Param("tenantId") Long tenantId,
                                        @Param("resource") String resource,
                                        @Param("start") String start,
                                        @Param("end") String end);

    /** 全部基础设施资源概览（Milvus/ES/MQ 各一行） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT resource, COUNT(*) AS calls, "
            + "SUM(success = 1) AS successCount, "
            + "SUM(success = 0) AS failedCount, "
            + "COALESCE(AVG(duration_ms), 0) AS avgDuration "
            + "FROM infra_metric WHERE tenant_id = #{tenantId} "
            + "<if test='start != null and start != \"\"'> AND create_time &gt;= #{start} </if>"
            + "<if test='end != null and end != \"\"'> AND create_time &lt;= #{end} </if>"
            + "GROUP BY resource"
            + "</script>")
    List<Map<String, Object>> statsAllResources(@Param("tenantId") Long tenantId,
                                                @Param("start") String start,
                                                @Param("end") String end);

    /** 按资源+日期统计时序（趋势图） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT DATE(create_time) AS day, resource, COUNT(*) AS calls, "
            + "COALESCE(AVG(duration_ms), 0) AS avgDuration "
            + "FROM infra_metric WHERE tenant_id = #{tenantId} "
            + "<if test='resource != null and resource != \"\"'> AND resource = #{resource} </if>"
            + "<if test='start != null and start != \"\"'> AND create_time &gt;= #{start} </if>"
            + "<if test='end != null and end != \"\"'> AND create_time &lt;= #{end} </if>"
            + "GROUP BY DATE(create_time), resource ORDER BY day DESC LIMIT 30"
            + "</script>")
    List<Map<String, Object>> dailyByResource(@Param("tenantId") Long tenantId,
                                              @Param("resource") String resource,
                                              @Param("start") String start,
                                              @Param("end") String end);

    /** 窗口内调用次数（告警评估用） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM infra_metric "
            + "WHERE tenant_id = #{tenantId} AND resource = #{resource} "
            + "AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{windowMinutes} MINUTE)")
    long countByResourceInWindow(@Param("tenantId") Long tenantId,
                                 @Param("resource") String resource,
                                 @Param("windowMinutes") int windowMinutes);

    /** 窗口内错误率（%，告警评估用）：SUM(success=0)*100/COUNT(*) */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COALESCE(ROUND(SUM(success = 0) * 100 / NULLIF(COUNT(*), 0), 2), 0) "
            + "FROM infra_metric "
            + "WHERE tenant_id = #{tenantId} AND resource = #{resource} "
            + "AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{windowMinutes} MINUTE)")
    double errorRateInWindow(@Param("tenantId") Long tenantId,
                             @Param("resource") String resource,
                             @Param("windowMinutes") int windowMinutes);

    /** 窗口内 P95 耗时（告警评估用，近似取第 95 百分位） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COALESCE(MAX(duration_ms), 0) FROM infra_metric "
            + "WHERE tenant_id = #{tenantId} AND resource = #{resource} "
            + "AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{windowMinutes} MINUTE) "
            + "AND success = 1 ORDER BY duration_ms DESC "
            + "LIMIT 1 OFFSET GREATEST(0, (SELECT COUNT(*) FROM infra_metric "
            + "WHERE tenant_id = #{tenantId} AND resource = #{resource} "
            + "AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{windowMinutes} MINUTE) "
            + "AND success = 1) * 5 / 100 - 1)")
    long latencyP95InWindow(@Param("tenantId") Long tenantId,
                            @Param("resource") String resource,
                            @Param("windowMinutes") int windowMinutes);
}
