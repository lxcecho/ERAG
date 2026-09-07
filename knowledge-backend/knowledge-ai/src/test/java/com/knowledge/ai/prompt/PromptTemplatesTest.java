/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PromptTemplates 内置模板与引用校验工具单测。
 * <p>重点覆盖 RAG 引用强制兜底相关的纯逻辑：
 * {@link PromptTemplates#containsCitation}（[编号] 检测）、
 * {@link PromptTemplates#citationsWithinRange}（编号越界防幻觉）、
 * {@link PromptTemplates#buildRepairPrompt}（修复提示词渲染）。
 */
class PromptTemplatesTest {

    /* ==================== buildContext ==================== */

    @Test
    void buildContext_按编号拼装文本与来源() {
        String ctx = PromptTemplates.buildContext(
                List.of("文本A", "文本B"), List.of("a.pdf", "b.pdf"));
        assertTrue(ctx.contains("[1] (来源: a.pdf)"));
        assertTrue(ctx.contains("文本A"));
        assertTrue(ctx.contains("[2] (来源: b.pdf)"));
        assertTrue(ctx.contains("文本B"));
    }

    /* ==================== containsCitation ==================== */

    @Test
    void containsCitation_单个编号命中() {
        assertTrue(PromptTemplates.containsCitation("HDMI 是数字化接口 [1]。"));
    }

    @Test
    void containsCitation_连续编号命中() {
        assertTrue(PromptTemplates.containsCitation("信号传输到终端 [1][3]。"));
    }

    @Test
    void containsCitation_无引用返回false() {
        assertFalse(PromptTemplates.containsCitation("HDMI 是数字化接口。"));
    }

    @Test
    void containsCitation_null返回false() {
        assertFalse(PromptTemplates.containsCitation(null));
    }

    /* ==================== citationsWithinRange ==================== */

    @Test
    void citationsWithinRange_编号均在范围内通过() {
        assertTrue(PromptTemplates.citationsWithinRange("引用 [1] 与 [2][3]", 5));
    }

    @Test
    void citationsWithinRange_编号越界拒绝() {
        assertFalse(PromptTemplates.citationsWithinRange("引用 [9]", 5));
        assertFalse(PromptTemplates.citationsWithinRange("引用 [0]", 5));
    }

    @Test
    void citationsWithinRange_无引用返回true() {
        assertTrue(PromptTemplates.citationsWithinRange("无任何引用", 5));
    }

    @Test
    void citationsWithinRange_maxIdx非法返回false() {
        assertFalse(PromptTemplates.citationsWithinRange("引用 [1]", 0));
        assertFalse(PromptTemplates.citationsWithinRange(null, 5));
    }

    /* ==================== buildRepairPrompt ==================== */

    @Test
    void buildRepairPrompt_渲染上下文与初稿() {
        String prompt = PromptTemplates.buildRepairPrompt("【编号上下文】[1] 文本A", "初稿回答");
        assertTrue(prompt.contains("【编号上下文】[1] 文本A"));
        assertTrue(prompt.contains("初稿回答"));
        // 占位符必须全部替换，无残留
        assertFalse(prompt.contains("{context}"));
        assertFalse(prompt.contains("{draft}"));
        // 修复规则明确"仅补引用、不改实质"
        assertTrue(prompt.contains("只补上引用标注"));
        assertTrue(prompt.contains("不要输出任何解释"));
    }

    @Test
    void buildRepairPrompt_null值渲染为空串() {
        String prompt = PromptTemplates.buildRepairPrompt(null, null);
        assertFalse(prompt.contains("null"));
        assertFalse(prompt.contains("{context}"));
        assertFalse(prompt.contains("{draft}"));
    }

    /* ==================== render ==================== */

    @Test
    void render_替换命名占位符() {
        String out = PromptTemplates.render("你好 {name}", Map.of("name", "张三"));
        assertEquals("你好 张三", out);
    }
}
