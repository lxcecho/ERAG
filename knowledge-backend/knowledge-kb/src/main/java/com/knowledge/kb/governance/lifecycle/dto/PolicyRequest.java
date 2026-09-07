package com.knowledge.kb.governance.lifecycle.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 知识治理策略保存请求（按 kbId upsert）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class PolicyRequest {

    @NotNull(message = "知识库ID不能为空")
    private Long kbId;

    /** 是否强制审核 1=须审核后发布 0=可直接发布 */
    private Boolean requireReview;

    /** 发布后自动归档天数 NULL=不自动归档 */
    @Positive(message = "自动归档天数须为正数")
    private Integer autoArchiveDays;

    /** 归档后保留天数 NULL=永久 达到即硬删除 */
    @Positive(message = "保留天数须为正数")
    private Integer retentionDays;

    /** 允许审核角色 逗号分隔 KB_OWNER,KB_EDITOR */
    private String approverRoles;

    /** 审核超时小时数 NULL=不超时 */
    @Positive(message = "审核超时小时数须为正数")
    private Integer reviewExpireHours;
}
