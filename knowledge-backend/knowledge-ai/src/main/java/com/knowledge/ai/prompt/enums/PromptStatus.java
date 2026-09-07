package com.knowledge.ai.prompt.enums;

/**
 * Prompt 模板版本状态机。
 * <pre>
 *   DRAFT ──发布──▶ PUBLISHED ──被新版本替换──▶ ARCHIVED
 *                    ▲                          │
 *                    └─────回滚(生成新版本)──────┘
 * </pre>
 * 同一 prompt_code 下至多一个 PUBLISHED 版本（由 Service 层保证互斥）。
 * DRAFT 可原地编辑；PUBLISHED/ARCHIVED 不可变，编辑即派生新 DRAFT 版本。
 *
 * @author: lxcechoo@gmail.com
 */
public enum PromptStatus {

    /** 草稿：可编辑，未生效 */
    DRAFT,
    /** 已发布：当前生效版本，同 code 下唯一 */
    PUBLISHED,
    /** 已归档：被新发布版本替换的历史版本，不可变 */
    ARCHIVED;

    /** 是否不可变（编辑需派生新版本） */
    public boolean isImmutable() {
        return this == PUBLISHED || this == ARCHIVED;
    }
}
