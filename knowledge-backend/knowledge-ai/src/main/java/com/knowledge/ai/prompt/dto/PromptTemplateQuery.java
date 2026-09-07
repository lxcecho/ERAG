package com.knowledge.ai.prompt.dto;

import com.knowledge.kb.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Prompt 模板分页查询条件（主列表：每个 prompt_code 取最新版本）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PromptTemplateQuery extends PageQuery {

    /** 模板名称（模糊） */
    private String name;

    /** 类型 system/rag/agent */
    private String type;

    /** Prompt 逻辑编码（模糊） */
    private String promptCode;
}
