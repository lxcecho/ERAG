package com.knowledge.agent.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 证据（Agent 领域的检索单元）：从 {@code RetrievalResult} 转换而来。
 * <p>独立于 LangChain4j 的 RetrievalResult，使 Agent 层不感知 RAG 层数据结构（解耦）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class Evidence implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 来源文档ID */
    private Long documentId;

    /** 来源文件名 */
    private String source;

    /** 切片唯一标识（doc{documentId}_chunk{index}） */
    private String chunkId;

    /** 切片序号 */
    private Integer chunkIndex;

    /** 命中文本内容 */
    private String content;

    /** 相似度得分 */
    private double score;

    /** 得分类型：vector / bm25 / fused */
    private String scoreType;
}
