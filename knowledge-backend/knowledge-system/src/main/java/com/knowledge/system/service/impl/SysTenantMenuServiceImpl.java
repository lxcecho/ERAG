package com.knowledge.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowledge.system.entity.SysTenantMenu;
import com.knowledge.system.mapper.SysTenantMenuMapper;
import com.knowledge.system.service.SysTenantMenuService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
/**
 *
 * @author: lxcechoo@gmail.com
 */
public class SysTenantMenuServiceImpl
        extends ServiceImpl<SysTenantMenuMapper, SysTenantMenu>
        implements SysTenantMenuService {

    @Override
    public List<Long> listMenuIdsByTenantId(Long tenantId) {
        if (tenantId == null) return List.of();
        List<SysTenantMenu> list = list(new LambdaQueryWrapper<SysTenantMenu>()
                .eq(SysTenantMenu::getTenantId, tenantId));
        List<Long> ids = new ArrayList<>(list.size());
        for (SysTenantMenu r : list) ids.add(r.getMenuId());
        return ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetMenus(Long tenantId, List<Long> menuIds) {
        remove(new LambdaQueryWrapper<SysTenantMenu>().eq(SysTenantMenu::getTenantId, tenantId));
        if (menuIds == null || menuIds.isEmpty()) return;
        List<SysTenantMenu> batch = new ArrayList<>(menuIds.size());
        for (Long menuId : menuIds) {
            SysTenantMenu r = new SysTenantMenu();
            r.setTenantId(tenantId);
            r.setMenuId(menuId);
            batch.add(r);
        }
        saveBatch(batch);
    }
}
