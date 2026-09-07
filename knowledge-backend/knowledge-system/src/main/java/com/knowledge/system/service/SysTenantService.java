package com.knowledge.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowledge.system.entity.SysTenant;

import java.util.Optional;

/**
 *
 * @author: lxcechoo@gmail.com
 */
public interface SysTenantService extends IService<SysTenant> {

    /** 按编码查启用的租户（登录用） */
    Optional<SysTenant> findByCode(String tenantCode);

    /** 断言租户存在且启用，不存在则抛 BizException（返回解析后的 tenantId） */
    Long resolveTenantId(Long tenantId, String tenantCode, String defaultTenantCode);
}
