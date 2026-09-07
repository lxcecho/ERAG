package com.knowledge.kb.governance.lifecycle.enums;

/**
 * 生命周期动作（驱动 {@link LifecycleStatus} 状态迁移）。
 * <p>合法迁移由 {@code LifecycleStateMachine.nextStatus} 裁定，非法组合返回 null 由 Service 抛 BizException。
 *
 * @author: lxcechoo@gmail.com
 */
public enum LifecycleAction {
    /** 提交审核：DRAFT → REVIEW（policy.requireReview=true 时） */
    SUBMIT,
    /** 直接发布：DRAFT → PUBLISHED（policy.requireReview=false 时，跳过审核） */
    PUBLISH,
    /** 审核通过：REVIEW → PUBLISHED */
    APPROVE,
    /** 审核驳回：REVIEW → DRAFT */
    REJECT,
    /** 归档：PUBLISHED → ARCHIVED */
    ARCHIVE,
    /** 恢复：ARCHIVED → PUBLISHED */
    RESTORE,
    /** 更新重审：PUBLISHED → REVIEW（requireReview=true）/ 保持 PUBLISHED（requireReview=false） */
    UPDATE
}
