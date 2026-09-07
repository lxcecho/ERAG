package com.knowledge.kb.governance.enums;

/**
 * 审核动作（document_review.action 审计流水）：SUBMIT 提交 / APPROVE 通过 / REJECT 驳回。
 *
 * @author: lxcechoo@gmail.com
 */
public enum ReviewAction {
    /** 提交审核（PENDING 文档发起审核） */
    SUBMIT,
    /** 审核通过 */
    APPROVE,
    /** 审核驳回 */
    REJECT
}
