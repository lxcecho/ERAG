package com.knowledge.kb.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档分页查询条件
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KbDocumentQuery extends PageQuery {

    /** 所属知识库ID */
    private Long kbId;

    /** 解析状态 */
    private Integer status;

    /** 原始文件名关键字 */
    private String originalName;
}
