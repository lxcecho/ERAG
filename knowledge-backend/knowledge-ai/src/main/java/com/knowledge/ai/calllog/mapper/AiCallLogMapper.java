package com.knowledge.ai.calllog.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.ai.calllog.dto.AiCallLogQuery;
import com.knowledge.ai.calllog.entity.AiCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * AI 调用日志 Mapper。
 * <p>所有查询用 {@link InterceptorIgnore} 关闭 MP 租户拦截器，手工注入 tenant_id 条件，
 * 便于统计 SQL 聚合与平台视角查询。审计日志无软删，直接物理查询。
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {

    /**
     * 分页查询调用日志（支持模块/类型/模型/状态/用户/时间范围过滤）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT id, tenant_id, user_id, username, module, biz_type, model_name, "
            + "       prompt_tokens, completion_tokens, total_tokens, duration_ms, cost, "
            + "       status, error_msg, create_time "
            + "FROM ai_call_log "
            + "WHERE tenant_id = #{tenantId} "
            + "<if test='q.module != null and q.module != \"\"'> AND module = #{q.module} </if>"
            + "<if test='q.bizType != null and q.bizType != \"\"'> AND biz_type = #{q.bizType} </if>"
            + "<if test='q.modelName != null and q.modelName != \"\"'> AND model_name = #{q.modelName} </if>"
            + "<if test='q.status != null and q.status != \"\"'> AND status = #{q.status} </if>"
            + "<if test='q.userId != null'> AND user_id = #{q.userId} </if>"
            + "<if test='q.startTime != null and q.startTime != \"\"'> AND create_time &gt;= #{q.startTime} </if>"
            + "<if test='q.endTime != null and q.endTime != \"\"'> AND create_time &lt;= #{q.endTime} </if>"
            + "ORDER BY create_time DESC"
            + "</script>")
    IPage<AiCallLog> page(IPage<AiCallLog> page,
                          @Param("tenantId") Long tenantId,
                          @Param("q") AiCallLogQuery query);

    /** 概览：调用次数/Token/费用/耗时聚合（单行） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT COUNT(*) AS calls, "
            + "SUM(status = 'SUCCESS') AS successCount, "
            + "SUM(status = 'FAILED') AS failedCount, "
            + "COALESCE(SUM(total_tokens), 0) AS tokens, "
            + "COALESCE(SUM(prompt_tokens), 0) AS promptTokens, "
            + "COALESCE(SUM(completion_tokens), 0) AS completionTokens, "
            + "COALESCE(SUM(cost), 0) AS cost, "
            + "COALESCE(AVG(duration_ms), 0) AS avgDuration "
            + "FROM ai_call_log "
            + "WHERE tenant_id = #{tenantId} "
            + "<if test='start != null and start != \"\"'> AND create_time &gt;= #{start} </if>"
            + "<if test='end != null and end != \"\"'> AND create_time &lt;= #{end} </if>"
            + "</script>")
    Map<String, Object> statsOverview(@Param("tenantId") Long tenantId,
                                      @Param("start") String start,
                                      @Param("end") String end);

    /** 按模型统计（费用倒序） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT model_name AS dim, COUNT(*) AS calls, "
            + "COALESCE(SUM(total_tokens), 0) AS tokens, COALESCE(SUM(cost), 0) AS cost "
            + "FROM ai_call_log WHERE tenant_id = #{tenantId} "
            + "<if test='start != null and start != \"\"'> AND create_time &gt;= #{start} </if>"
            + "<if test='end != null and end != \"\"'> AND create_time &lt;= #{end} </if>"
            + "GROUP BY model_name ORDER BY cost DESC"
            + "</script>")
    List<Map<String, Object>> statsByModel(@Param("tenantId") Long tenantId,
                                           @Param("start") String start,
                                           @Param("end") String end);

    /** 按用户统计（费用倒序，Top 10） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT user_id AS userId, username AS dim, COUNT(*) AS calls, "
            + "COALESCE(SUM(total_tokens), 0) AS tokens, COALESCE(SUM(cost), 0) AS cost "
            + "FROM ai_call_log WHERE tenant_id = #{tenantId} "
            + "<if test='start != null and start != \"\"'> AND create_time &gt;= #{start} </if>"
            + "<if test='end != null and end != \"\"'> AND create_time &lt;= #{end} </if>"
            + "GROUP BY user_id, username ORDER BY cost DESC LIMIT 10"
            + "</script>")
    List<Map<String, Object>> statsByUser(@Param("tenantId") Long tenantId,
                                          @Param("start") String start,
                                          @Param("end") String end);

    /** 按日期统计（最近 30 天，日期倒序） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT DATE(create_time) AS day, COUNT(*) AS calls, "
            + "COALESCE(SUM(total_tokens), 0) AS tokens, COALESCE(SUM(cost), 0) AS cost "
            + "FROM ai_call_log WHERE tenant_id = #{tenantId} "
            + "<if test='start != null and start != \"\"'> AND create_time &gt;= #{start} </if>"
            + "<if test='end != null and end != \"\"'> AND create_time &lt;= #{end} </if>"
            + "GROUP BY DATE(create_time) ORDER BY day DESC LIMIT 30"
            + "</script>")
    List<Map<String, Object>> statsByDay(@Param("tenantId") Long tenantId,
                                         @Param("start") String start,
                                         @Param("end") String end);

    /* ==================== 告警评估：窗口内统计（llm:chat 资源用） ==================== */

    /** 窗口内调用次数（告警评估 metric=calls，llm:chat 资源） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM ai_call_log "
            + "WHERE tenant_id = #{tenantId} "
            + "AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{windowMinutes} MINUTE)")
    long countInWindow(@Param("tenantId") Long tenantId,
                       @Param("windowMinutes") int windowMinutes);

    /** 窗口内错误率（%，告警评估 metric=error_rate，llm:chat 资源） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COALESCE(ROUND(SUM(status = 'FAILED') * 100 / NULLIF(COUNT(*), 0), 2), 0) "
            + "FROM ai_call_log WHERE tenant_id = #{tenantId} "
            + "AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{windowMinutes} MINUTE)")
    double errorRateInWindow(@Param("tenantId") Long tenantId,
                             @Param("windowMinutes") int windowMinutes);

    /** 窗口内 Token 消耗（告警评估 metric=token_usage，llm:chat 资源） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COALESCE(SUM(total_tokens), 0) FROM ai_call_log "
            + "WHERE tenant_id = #{tenantId} "
            + "AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{windowMinutes} MINUTE)")
    long sumTokensInWindow(@Param("tenantId") Long tenantId,
                           @Param("windowMinutes") int windowMinutes);
}
