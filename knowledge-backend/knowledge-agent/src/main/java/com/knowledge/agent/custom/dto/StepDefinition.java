package com.knowledge.agent.custom.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 多步骤流程单步定义（agent_definition.steps JSON 数组元素）。
 * <pre>
 * {
 *   "stepName": "日志摘要",
 *   "prompt": "请阅读以下日志片段……\n{context}",
 *   "inputFrom": "context",
 *   "outputKey": "summary"
 * }
 * </pre>
 * <p>inputFrom=context 时 prompt 可引用 {context}/{question}；
 * inputFrom=prev 时 prompt 可引用 {question} 与上一步 outputKey（如 {summary}）。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
public class StepDefinition {

    /** 步骤名称（展示用） */
    @NotBlank(message = "步骤名称不能为空")
    private String stepName;

    /** 该步 LLM 提示词（支持占位符） */
    @NotBlank(message = "步骤提示词不能为空")
    private String prompt;

    /** 输入来源：context=外部注入上下文 / prev=上一步输出 */
    private String inputFrom = "context";

    /** 产物 key（下一步 {outputKey} 引用，末步输出即最终 result） */
    @NotBlank(message = "产物 key 不能为空")
    private String outputKey;
}
