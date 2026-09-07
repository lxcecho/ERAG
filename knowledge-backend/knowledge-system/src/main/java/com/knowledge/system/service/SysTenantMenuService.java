package com.knowledge.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowledge.system.entity.SysTenantMenu;

import java.util.List;

/**
 * 租户-菜单关系 Service
 *
 * @author: lxcechoo@gmail.com
 */
public interface SysTenantMenuService extends IService<SysTenantMenu> {

    /** 查询租户可访问的菜单ID列表（用于前端加载租户级菜单白名单） */
    List<Long> listMenuIdsByTenantId(Long tenantId);

    /** 重置租户菜单（先删后插，事务由调用方保证） */
    void resetMenus(Long tenantId, List<Long> menuIds);
}
