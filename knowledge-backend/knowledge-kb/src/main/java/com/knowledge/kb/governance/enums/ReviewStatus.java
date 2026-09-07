package com.knowledge.kb.governance.enums;

/**
 * 文档审核状态机：PENDING → APPROVED / REJECTED。
 * <p>仅 APPROVED 文档默认参与 RAG 检索（可通过 ai.governance.review-gate 配置开关）。
 *
 * @author: lxcechoo@gmail.com
 */
public enum ReviewStatus {
    /** 待审核（新上传默认态） */
    PENDING,
    /** 审核通过 */
    APPROVED,
    /** 审核驳回 */
    REJECTED;

    /** 已终态（不可再改回 PENDING） */
    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }
}
