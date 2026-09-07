package com.knowledge.kb.governance.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档审核记录视图（审计流水）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class ReviewVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long kbId;
    private Long docId;
    private Long reviewerId;
    /** SUBMIT / APPROVE / REJECT */
    private String action;
    private String comment;
    private LocalDateTime createTime;
}
