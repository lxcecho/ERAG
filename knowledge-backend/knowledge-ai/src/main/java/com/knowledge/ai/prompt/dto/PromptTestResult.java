package com.knowledge.ai.prompt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Prompt 模板测试结果：渲染后的提示词 + LLM 生成回答。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromptTestResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 变量渲染后的最终提示词 */
    private String renderedPrompt;

    /** LLM 生成回答 */
    private String output;

    /** 本次调用消耗 token 数（取不到时为 0） */
    private int tokens;
}
