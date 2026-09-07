package com.knowledge.agent.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.agent.entity.AgentTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * Agent 任务 Mapper。
 * <p>统计查询用 {@link InterceptorIgnore} 关闭 MP 租户拦截器，手工注入 tenant_id
 * （同 AiCallLogMapper 模式），避免聚合 SQL 与拦截器不兼容。
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {

    /**
     * Agent 任务耗时统计（运维指标 #3）。
     * <p>仅统计已完成（finished_time 非空）任务，duration = finished_time - create_time（毫秒）。
     *
     * @param tenantId 租户ID
     * @param start    起始时间（可空）
     * @param end      结束时间（可空）
     * @return total/completed/failed/avgDurationMs/maxDurationMs/tokens 聚合行
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT COUNT(*) AS total, "
            + "SUM(status = 'COMPLETED') AS completed, "
            + "SUM(status = 'FAILED') AS failed, "
            + "COALESCE(AVG(TIMESTAMPDIFF(MICROSECOND, create_time, finished_time) / 1000), 0) AS avgDurationMs, "
            + "COALESCE(MAX(TIMESTAMPDIFF(MICROSECOND, create_time, finished_time) / 1000), 0) AS maxDurationMs, "
            + "COALESCE(SUM(token_usage), 0) AS tokens "
            + "FROM agent_task "
            + "WHERE tenant_id = #{tenantId} AND finished_time IS NOT NULL "
            + "<if test='start != null and start != \"\"'> AND create_time &gt;= #{start} </if>"
            + "<if test='end != null and end != \"\"'> AND create_time &lt;= #{end} </if>"
            + "</script>")
    Map<String, Object> statsDuration(@Param("tenantId") Long tenantId,
                                      @Param("start") String start,
                                      @Param("end") String end);

    /** 按日期统计 Agent 任务耗时趋势（最近 30 天，运维看板折线图） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT DATE(create_time) AS day, COUNT(*) AS total, "
            + "SUM(status = 'COMPLETED') AS completed, "
            + "COALESCE(AVG(TIMESTAMPDIFF(MICROSECOND, create_time, finished_time) / 1000), 0) AS avgDurationMs "
            + "FROM agent_task WHERE tenant_id = #{tenantId} AND finished_time IS NOT NULL "
            + "<if test='start != null and start != \"\"'> AND create_time &gt;= #{start} </if>"
            + "<if test='end != null and end != \"\"'> AND create_time &lt;= #{end} </if>"
            + "GROUP BY DATE(create_time) ORDER BY day DESC LIMIT 30"
            + "</script>")
    java.util.List<Map<String, Object>> dailyStats(@Param("tenantId") Long tenantId,
                                                   @Param("start") String start,
                                                   @Param("end") String end);
}
