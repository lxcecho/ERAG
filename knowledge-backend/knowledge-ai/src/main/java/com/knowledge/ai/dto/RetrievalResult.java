package com.knowledge.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 检索结果（向量路 / 词法路 / 融合后通用）。
 * <p>chunkId：切片唯一标识，用于混合检索时两路结果去重对齐；
 * scoreType：得分来源（vector 向量相似度 / bm25 ES 关键词 / fused RRF 融合），
 * 便于可观测与 A/B 对比。二者均可空，向后兼容前端（non_null 序列化自动忽略）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 命中的文本切片 */
    private String text;

    /** 相似度得分（量纲随 scoreType 不同，融合后为 RRF 分） */
    private double score;

    /** 来源文件名 */
    private String source;

    /** 所属文档ID */
    private Long documentId;

    /** 切片序号 */
    private Integer chunkIndex;

    /** 切片唯一标识（doc{documentId}_chunk{index}），融合去重主键 */
    private String chunkId;

    /** 得分类型：vector / bm25 / fused */
    private String scoreType;
}
