package com.knowledge.kb.governance.enums;

/**
 * 重复关系处理状态：PENDING 待处理 → CONFIRMED 确认重复 / IGNORED 忽略（误报）。
 *
 * @author: lxcechoo@gmail.com
 */
public enum DuplicateStatus {
    /** 待处理（检测到但未人工确认） */
    PENDING,
    /** 确认重复（可触发去重清理） */
    CONFIRMED,
    /** 忽略（人工判定为误报） */
    IGNORED
}
