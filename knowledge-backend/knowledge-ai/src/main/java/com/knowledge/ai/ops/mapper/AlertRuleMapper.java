package com.knowledge.ai.ops.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.ai.ops.entity.AlertRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 告警规则 Mapper。
 * <p>alert_rule 走 MP 租户拦截器（有 tenant_id 且非平台级），但 listEnabled 用于定时任务
 * （无 SecurityContext）故用 {@link InterceptorIgnore} + 手工 tenant_id 兜底平台视角。
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface AlertRuleMapper extends BaseMapper<AlertRule> {

    /** 查询启用规则（定时评估用，含全部租户平台视角） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id, tenant_id, name, resource, metric, operator, threshold, "
            + "window_minutes, level, enabled FROM alert_rule "
            + "WHERE enabled = 1 AND deleted = 0")
    List<AlertRule> listAllEnabled();
}
