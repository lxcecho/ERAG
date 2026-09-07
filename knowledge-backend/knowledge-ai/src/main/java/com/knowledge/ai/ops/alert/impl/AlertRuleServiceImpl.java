package com.knowledge.ai.ops.alert.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.ai.ops.alert.AlertRuleService;
import com.knowledge.ai.ops.dto.AlertRuleRequest;
import com.knowledge.ai.ops.entity.AlertRule;
import com.knowledge.ai.ops.mapper.AlertRuleMapper;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 告警规则管理服务实现。
 * <p>CRUD 走 MP 租户拦截器（HTTP 请求线程有 {@link TenantContext}），自动按 tenant_id 隔离；
 * insert 时手工注入 tenantId（拦截器不自动填充插入字段）。
 *
 * @author: lxcechoo@gmail.com
 */
@Service
@RequiredArgsConstructor
public class AlertRuleServiceImpl implements AlertRuleService {

    private final AlertRuleMapper alertRuleMapper;

    @Override
    public List<AlertRule> list() {
        return alertRuleMapper.selectList(new LambdaQueryWrapper<AlertRule>()
                .orderByDesc(AlertRule::getCreateTime));
    }

    @Override
    public AlertRule create(AlertRuleRequest req) {
        AlertRule rule = new AlertRule();
        rule.setTenantId(currentTenantId());
        applyRequest(rule, req);
        alertRuleMapper.insert(rule);
        return rule;
    }

    @Override
    public AlertRule update(Long id, AlertRuleRequest req) {
        AlertRule rule = mustGet(id);
        applyRequest(rule, req);
        alertRuleMapper.updateById(rule);
        return rule;
    }

    @Override
    public void delete(Long id) {
        mustGet(id);
        alertRuleMapper.deleteById(id);
    }

    @Override
    public void toggleEnabled(Long id, boolean enabled) {
        AlertRule rule = mustGet(id);
        rule.setEnabled(enabled ? 1 : 0);
        alertRuleMapper.updateById(rule);
    }

    /* ==================== 工具 ==================== */

    private AlertRule mustGet(Long id) {
        AlertRule rule = alertRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException("告警规则不存在: " + id);
        }
        return rule;
    }

    private void applyRequest(AlertRule rule, AlertRuleRequest req) {
        rule.setName(req.getName());
        rule.setResource(req.getResource());
        rule.setMetric(req.getMetric());
        rule.setOperator(req.getOperator());
        rule.setThreshold(req.getThreshold());
        rule.setWindowMinutes(req.getWindowMinutes() != null ? req.getWindowMinutes() : 5);
        rule.setLevel(req.getLevel() != null ? req.getLevel() : "WARN");
        rule.setEnabled(req.getEnabled() != null ? req.getEnabled() : 1);
    }

    private static Long currentTenantId() {
        Long id = TenantContext.getTenantId();
        return id != null ? id : TenantContext.PLATFORM_TENANT_ID;
    }
}
