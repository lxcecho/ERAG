package com.knowledge.kb.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库分页查询条件
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KbPageQuery extends PageQuery {

    /** 名称关键字 */
    private String name;

    /** 状态 */
    private Integer status;
}
