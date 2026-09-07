package com.knowledge.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.system.entity.SysTenantMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户-菜单关系 Mapper
 *
 * @author: lxcechoo@gmail.com
 */
@Mapper
public interface SysTenantMenuMapper extends BaseMapper<SysTenantMenu> {
}
