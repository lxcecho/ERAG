/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.custom.executor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CustomPromptRenderer} 单元测试：占位符替换、缺失占位符容忍、多步串联替换。
 */
class CustomPromptRendererTest {

    @Test
    void should_replace_context_and_question_in_system_prompt() {
        String template = "你是分析助手。资料：{context}\n问题：{question}";
        String result = CustomPromptRenderer.renderSystemPrompt(template, "日志A", "根因是什么？");
        assertEquals("你是分析助手。资料：日志A\n问题：根因是什么？", result);
    }

    @Test
    void should_keep_missing_placeholder_as_is() {
        String template = "资料：{context}\n请分析：{unknown}";
        String result = CustomPromptRenderer.renderSystemPrompt(template, "ctx", "q");
        assertTrue(result.contains("{unknown}"), "未提供值的占位符应保持原样");
        assertTrue(result.contains("ctx"));
    }

    @Test
    void should_replace_null_context_with_empty() {
        String result = CustomPromptRenderer.renderSystemPrompt("资料：{context}", null, "q");
        assertEquals("资料：", result);
    }

    @Test
    void should_chain_prev_step_output_into_next_step_prompt() {
        Map<String, String> stepVars = new LinkedHashMap<>();
        stepVars.put("summary", "日志摘要内容");
        String result = CustomPromptRenderer.renderStepPrompt(
                "基于摘要：{summary} 回答问题：{question}", null, "部署失败原因", stepVars);
        assertEquals("基于摘要：日志摘要内容 回答问题：部署失败原因", result);
    }

    @Test
    void should_render_step_prompt_with_context_and_question() {
        String result = CustomPromptRenderer.renderStepPrompt(
                "{context}\n问题：{question}", "上下文文本", "问题A", Map.of());
        assertTrue(result.startsWith("上下文文本"));
        assertTrue(result.contains("问题A"));
    }
}
