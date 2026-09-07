package com.knowledge.agent.workflow.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.agent.workflow.entity.WorkflowTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Workflow 流程任务 Mapper。
 * <p>统计查询用 {@link InterceptorIgnore} 关闭 MP 租户拦截器，手工注入 tenant_id
 * （同 AiCallLogMapper 模式），避免聚合 SQL 与拦截器不兼容。
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface WorkflowTaskMapper extends BaseMapper<WorkflowTask> {

    /**
     * Workflow 成功率统计（运维指标 #7）。
     * <p>successRate = COMPLETED / total × 100（保留 2 位小数）。
     *
     * @param tenantId 租户ID
     * @param start    起始时间（可空）
     * @param end      结束时间（可空）
     * @return total/completed/failed/successRate/tokens 聚合行
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT COUNT(*) AS total, "
            + "SUM(status = 'COMPLETED') AS completed, "
            + "SUM(status = 'FAILED') AS failed, "
            + "COALESCE(ROUND(SUM(status = 'COMPLETED') * 100 / NULLIF(COUNT(*), 0), 2), 0) AS successRate, "
            + "COALESCE(SUM(token_usage), 0) AS tokens "
            + "FROM workflow_task WHERE tenant_id = #{tenantId} "
            + "<if test='start != null and start != \"\"'> AND create_time &gt;= #{start} </if>"
            + "<if test='end != null and end != \"\"'> AND create_time &lt;= #{end} </if>"
            + "</script>")
    Map<String, Object> statsSuccessRate(@Param("tenantId") Long tenantId,
                                         @Param("start") String start,
                                         @Param("end") String end);

    /** 按日期统计 Workflow 成功率趋势（最近 30 天，运维看板折线图） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT DATE(create_time) AS day, COUNT(*) AS total, "
            + "SUM(status = 'COMPLETED') AS completed, "
            + "COALESCE(ROUND(SUM(status = 'COMPLETED') * 100 / NULLIF(COUNT(*), 0), 2), 0) AS successRate "
            + "FROM workflow_task WHERE tenant_id = #{tenantId} "
            + "<if test='start != null and start != \"\"'> AND create_time &gt;= #{start} </if>"
            + "<if test='end != null and end != \"\"'> AND create_time &lt;= #{end} </if>"
            + "GROUP BY DATE(create_time) ORDER BY day DESC LIMIT 30"
            + "</script>")
    List<Map<String, Object>> dailyStats(@Param("tenantId") Long tenantId,
                                         @Param("start") String start,
                                         @Param("end") String end);
}
