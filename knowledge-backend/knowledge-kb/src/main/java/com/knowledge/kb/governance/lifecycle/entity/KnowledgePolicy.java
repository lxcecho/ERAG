package com.knowledge.kb.governance.lifecycle.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识治理策略实体（每知识库一份，按 (tenant_id, kb_id) 唯一）。
 * <p>驱动生命周期：强制审核 / 自动归档天数 / 保留期硬删除天数 / 允许审核角色 / 审核超时。
 * <p>无 {@code @TableLogic}：配置表，按 uk_tenant_kb upsert，不做软删。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@TableName("knowledge_policy")
public class KnowledgePolicy implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long kbId;

    /** 是否强制审核 1=须审核后发布 0=可直接发布 */
    private Boolean requireReview;

    /** 发布后自动归档天数 NULL=不自动归档 */
    private Integer autoArchiveDays;

    /** 归档后保留天数 NULL=永久 达到即硬删除 */
    private Integer retentionDays;

    /** 允许审核角色 逗号分隔 KB_OWNER,KB_EDITOR */
    private String approverRoles;

    /** 审核超时小时数 NULL=不超时 */
    private Integer reviewExpireHours;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
