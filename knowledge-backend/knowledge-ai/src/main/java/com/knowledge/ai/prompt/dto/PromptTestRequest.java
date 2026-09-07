package com.knowledge.ai.prompt.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * Prompt 模板测试请求：渲染模板变量并调用 LLM 生成预览结果。
 * <p>两种输入方式（二选一，由 Service 校验至少传一个）：
 * <ul>
 *   <li>{@code promptCode} 非空：从 DB 加载该 code 的 PUBLISHED 版本内容进行测试。</li>
 *   <li>{@code content} 非空：直接使用传入内容测试（用于编辑时实时预览未保存的模板）。</li>
 * </ul>
 * {@code variables} 为变量键值表，与模板内 {@code {varName}} 占位符一一对应。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class PromptTestRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 加载 DB 已发布模板进行测试（与 content 二选一） */
    private String promptCode;

    /** 直接测试传入内容（与 promptCode 二选一） */
    private String content;

    /** 变量键值表，用于 {var} 占位符渲染 */
    private Map<String, String> variables;
}
