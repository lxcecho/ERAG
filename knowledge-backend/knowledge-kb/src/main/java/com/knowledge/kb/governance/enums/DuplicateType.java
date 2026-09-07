package com.knowledge.kb.governance.enums;

/**
 * 文档重复类型：EXACT 精确重复（MD5 相同）/ NEAR 近似重复（SimHash Hamming 距离小）。
 *
 * @author: lxcechoo@gmail.com
 */
public enum DuplicateType {
    /** 精确重复：MD5 完全一致 */
    EXACT,
    /** 近似重复：SimHash Hamming 距离 ≤ 阈值 */
    NEAR
}
