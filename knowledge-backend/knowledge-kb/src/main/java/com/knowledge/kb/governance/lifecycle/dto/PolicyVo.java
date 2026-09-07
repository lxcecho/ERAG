package com.knowledge.kb.governance.lifecycle.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识治理策略视图。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class PolicyVo {

    private Long id;
    private Long kbId;
    private Boolean requireReview;
    private Integer autoArchiveDays;
    private Integer retentionDays;
    private String approverRoles;
    private Integer reviewExpireHours;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
