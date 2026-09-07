package com.knowledge.kb.governance.lifecycle.enums;

/**
 * 文档生命周期状态（顶层治理状态，与 review_status 共存）。
 * <p>
 * DRAFT 草稿（新建/驳回后）→ REVIEW 审核中 → PUBLISHED 已发布（唯一可被 RAG 检索）→ ARCHIVED 已归档（不可检索，可恢复）。
 * <p>仅 PUBLISHED 参与检索（由 {@code filterGovernanceValid} 的 lifecycleGate 门禁保证）。
 *
 * @author: lxcechoo@gmail.com
 */
public enum LifecycleStatus {
    /** 草稿（新建默认态 / 审核驳回后回到此态） */
    DRAFT,
    /** 审核中（已提交待审核） */
    REVIEW,
    /** 已发布（可被 RAG 检索） */
    PUBLISHED,
    /** 已归档（不可检索，可通过 RESTORE 恢复为 PUBLISHED） */
    ARCHIVED;

    /** 是否可被 RAG 检索（仅 PUBLISHED） */
    public boolean isRetrievable() {
        return this == PUBLISHED;
    }

    /** 是否为终态（PUBLISHED/ARCHIVED，需显式动作才能离开） */
    public boolean isTerminal() {
        return this == PUBLISHED || this == ARCHIVED;
    }
}
