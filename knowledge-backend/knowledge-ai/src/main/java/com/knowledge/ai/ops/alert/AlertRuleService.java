package com.knowledge.ai.ops.alert;

import com.knowledge.ai.ops.dto.AlertRuleRequest;
import com.knowledge.ai.ops.entity.AlertRule;

import java.util.List;

/**
 * 告警规则管理服务：CRUD + 启停切换。
 * <p>规则实体有软删（{@code @TableLogic}），CRUD 走 MP 租户拦截器（HTTP 请求线程有 TenantContext）。
 * 定时评估由 {@code OpsAlertEvaluator} 通过 {@code AlertRuleMapper.listAllEnabled} 跨租户读取。
 *
 * @author: lxcechoo@gmail.com
 */
public interface AlertRuleService {

    /** 列表查询（当前租户全部规则，含停用） */
    List<AlertRule> list();

    /** 新建规则 */
    AlertRule create(AlertRuleRequest req);

    /** 更新规则 */
    AlertRule update(Long id, AlertRuleRequest req);

    /** 删除规则（软删） */
    void delete(Long id);

    /** 启停切换 */
    void toggleEnabled(Long id, boolean enabled);
}
