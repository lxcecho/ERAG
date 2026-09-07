package com.knowledge.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户表（多租户核心实体）。
 * <p>注意：该表是平台级元数据，不走 MP 多租户行级过滤（已在 ignore-tables 中）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("sys_tenant")
public class SysTenant implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 平台运维租户固定 ID（=0） */
    public static final long PLATFORM_TENANT_ID = 0L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户编码（唯一），登录时用 */
    private String tenantCode;

    /** 租户名称 */
    private String tenantName;

    /** 状态：0 启用 1 停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
