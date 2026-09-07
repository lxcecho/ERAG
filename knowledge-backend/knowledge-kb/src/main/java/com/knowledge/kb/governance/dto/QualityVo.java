package com.knowledge.kb.governance.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档质量评分视图（携带文档名便于列表展示）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class QualityVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long kbId;
    private Long docId;
    /** 文档名（联查 kb_document） */
    private String docName;
    /** 总分 0-100 */
    private Integer score;
    private Integer completeness;
    private Integer freshness;
    private Integer structure;
    private Integer coverage;
    private String summary;
    /** RULE / LLM */
    private String evaluator;
    private LocalDateTime createTime;
}
