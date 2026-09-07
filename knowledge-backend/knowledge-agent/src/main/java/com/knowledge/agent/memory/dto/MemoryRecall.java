package com.knowledge.agent.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 向量记忆召回项（VectorMemory.recall 返回）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryRecall {

    /** 召回文本（摘要/事实内容） */
    private String content;

    /** 相似度得分（0~1） */
    private Double score;

    /** 记忆类型 SUMMARY/LONG_TERM */
    private String memoryType;

    /** 来源会话ID */
    private Long sourceSessionId;
}
