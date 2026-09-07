package com.knowledge.ai.prompt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * Prompt 模板新增/编辑请求。
 * <ul>
 *   <li>新增：{@code id} 为空，{@code promptCode} 必填，创建 version=1 的 DRAFT。</li>
 *   <li>编辑：{@code id} 非空。若目标为 DRAFT 则原地更新；若为 PUBLISHED/ARCHIVED 则派生新 DRAFT 版本。</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class PromptTemplateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 编辑时传入版本行ID；新增时为空 */
    private Long id;

    /** Prompt 逻辑编码（新增必填，小写下划线，如 rag_system_prompt） */
    @NotBlank(message = "promptCode 不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$", message = "promptCode 须以字母开头，仅含小写字母/数字/下划线，2-64 字符")
    private String promptCode;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 128, message = "模板名称最长128个字符")
    private String name;

    @NotBlank(message = "模板类型不能为空")
    @Pattern(regexp = "^(system|rag|agent)$", message = "模板类型仅支持 system / rag / agent")
    private String type;

    @NotBlank(message = "模板内容不能为空")
    private String content;

    /** 变量列表（逗号分隔，如 context,question），可空 */
    private String variables;

    /** 版本说明，可空 */
    @Size(max = 255, message = "版本说明最长255个字符")
    private String remark;
}
