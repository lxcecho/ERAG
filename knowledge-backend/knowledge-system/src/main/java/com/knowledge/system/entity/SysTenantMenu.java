package com.knowledge.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 租户与菜单关系表（RBAC 粒度：租户可使用哪些菜单）。
 * <p>平台级表，不走 MP 行级租户过滤。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("sys_tenant_menu")
public class SysTenantMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long menuId;
}
