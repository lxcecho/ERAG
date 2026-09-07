package com.knowledge.agent.custom.executor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自定义 Agent 提示词渲染器：替换 {context}/{question}/{步骤outputKey} 占位符。
 * <p>复用 {@link com.knowledge.ai.prompt.PromptTemplates#render} 的命名占位符约定，
 * 未提供值的占位符保持原样（便于发现遗漏变量）。
 *
 * @author: lxcechoo@gmail.com
 */
public final class CustomPromptRenderer {

    private CustomPromptRenderer() {
    }

    /**
     * 渲染系统提示词（单步模式）。
     *
     * @param template 模板（含 {context}/{question} 占位符）
     * @param context  注入的上下文（知识库检索/自定义内容/日志）
     * @param question 用户问题
     * @return 渲染后的提示词
     */
    public static String renderSystemPrompt(String template, String context, String question) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("context", context == null ? "" : context);
        vars.put("question", question == null ? "" : question);
        return com.knowledge.ai.prompt.PromptTemplates.render(template, vars);
    }

    /**
     * 渲染多步流程单步提示词。
     *
     * @param template  步骤模板（可引用 {context}/{question}/{已产出outputKey}）
     * @param context   外部注入上下文（inputFrom=context 时使用）
     * @param question  用户问题
     * @param stepVars  已执行步骤产物（outputKey → 输出文本）
     * @return 渲染后的提示词
     */
    public static String renderStepPrompt(String template, String context, String question,
                                          Map<String, String> stepVars) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("context", context == null ? "" : context);
        vars.put("question", question == null ? "" : question);
        if (stepVars != null) {
            vars.putAll(stepVars);
        }
        return com.knowledge.ai.prompt.PromptTemplates.render(template, vars);
    }
}
