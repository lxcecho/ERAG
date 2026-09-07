package com.knowledge.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowledge.common.context.TenantContext;
import com.knowledge.common.exception.BizException;
import com.knowledge.common.tenant.TenantProperties;
import com.knowledge.system.entity.SysTenant;
import com.knowledge.system.mapper.SysTenantMapper;
import com.knowledge.system.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 租户服务实现（平台级查询，不走 MP 行级过滤——sys_tenant 在 ignoreTables）。
 *
 * @author: lxcechoo@gmail.com
 */
@Service
@RequiredArgsConstructor
public class SysTenantServiceImpl extends ServiceImpl<SysTenantMapper, SysTenant> implements SysTenantService {

    private final TenantProperties props;

    @Override
    public Optional<SysTenant> findByCode(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) return Optional.empty();
        return Optional.ofNullable(getOne(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getTenantCode, tenantCode)
                .eq(SysTenant::getStatus, 0)
                .last("LIMIT 1")));
    }

    @Override
    public Long resolveTenantId(Long tenantId, String tenantCode, String defaultTenantCode) {
        // 若未开多租户：全部走 demo=1 号，或直接保持上下文为空
        if (!props.isEnabled()) {
            return tenantId != null ? tenantId : TenantContext.DEMO_TENANT_ID;
        }
        // 1) 显式 tenantId 优先
        if (tenantId != null) {
            SysTenant t = getById(tenantId);
            if (t == null || t.getStatus() != 0) {
                throw new BizException(400, "租户不存在或已禁用（tenantId=" + tenantId + "）");
            }
            return t.getId();
        }
        // 2) tenantCode 次之
        String code = StringUtils.hasText(tenantCode) ? tenantCode : props.getDefaultTenantCode();
        SysTenant t = getOne(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getTenantCode, code)
                .eq(SysTenant::getStatus, 0)
                .last("LIMIT 1"));
        if (t == null) {
            throw new BizException(400, "租户编码不存在或已禁用（tenantCode=" + code + "）");
        }
        return t.getId();
    }
}
