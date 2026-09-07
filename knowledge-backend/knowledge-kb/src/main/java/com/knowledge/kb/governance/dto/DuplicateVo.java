package com.knowledge.kb.governance.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 文档重复关系视图（携带两文档名称）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class DuplicateVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long kbId;
    private Long docId1;
    private Long docId2;
    private BigDecimal similarity;
    private String dupType;
    private String status;
    private LocalDateTime createTime;
    private String docName1;
    private String docName2;
}
