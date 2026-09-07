/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.rag.optimize.eval.impl;

import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.rag.optimize.config.RagOptimizeProperties;
import com.knowledge.ai.rag.optimize.dto.AnswerEvaluation;
import com.knowledge.ai.service.LLMService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 回答评价器单元测试：规则版忠实度/相关度 + 开关降级 + LLM 版回退。
 */
class AnswerEvaluatorImplTest {

    private RagOptimizeProperties properties;
    private AnswerEvaluatorImpl evaluator;

    @BeforeEach
    void setUp() {
        properties = new RagOptimizeProperties();
        properties.getEval().setEnabled(true);
        properties.getEval().setLlmEvalEnabled(false);
        evaluator = new AnswerEvaluatorImpl(properties, mock(LLMService.class));
    }

    @Test
    void evaluate_开关关闭返回null() {
        properties.getEval().setEnabled(false);
        AnswerEvaluation r = evaluator.evaluate("问题", List.of(), "回答");
        assertNull(r);
    }

    @Test
    void evaluate_空回答返回null() {
        assertNull(evaluator.evaluate("问题", List.of(), ""));
        assertNull(evaluator.evaluate("问题", List.of(), null));
    }

    @Test
    void 规则版_回答词汇全在上下文则忠实度为1() {
        List<RetrievalResult> results = List.of(
                new RetrievalResult("Spring Boot 是 Java 框架，用于快速开发", 1.0, "s", 1L, 0, "c1", "fused"));
        AnswerEvaluation r = evaluator.evaluate("什么是Spring Boot", results, "Spring Boot 是 Java 框架");
        assertNotNull(r);
        assertEquals("RULE", r.method());
        assertTrue(r.faithfulness() > 0.5, "回答词汇多在上下文，忠实度应较高");
    }

    @Test
    void 规则版_回答词汇不在上下文则忠实度低() {
        List<RetrievalResult> results = List.of(
                new RetrievalResult("苹果香蕉橘子葡萄西瓜芒果", 1.0, "s", 1L, 0, "c1", "fused"));
        AnswerEvaluation r = evaluator.evaluate("q", results, "Python Django Flask Web 框架开发");
        assertNotNull(r);
        assertTrue(r.faithfulness() < 0.5, "回答词汇不在上下文，忠实度应较低");
    }

    @Test
    void 规则版_相关度为问题与回答的Jaccard() {
        // 问题词 = {知识, 库, 是, 什么}（去掉停用词"是/什么"... "什么"不在停用词表，保留）
        List<RetrievalResult> results = List.of(
                new RetrievalResult("知识库是存储知识的系统", 1.0, "s", 1L, 0, "c1", "fused"));
        AnswerEvaluation r = evaluator.evaluate("知识库是什么", results, "知识库是存储知识的系统");
        assertNotNull(r);
        assertTrue(r.relevance() > 0.0, "问题与回答有重叠词，相关度应 >0");
        assertEquals(0.6 * r.faithfulness() + 0.4 * r.relevance(), r.overallScore(), 0.001);
    }

    @Test
    void LLM版_解析失败回退规则版() {
        properties.getEval().setLlmEvalEnabled(true);
        LLMService llm = mock(LLMService.class);
        when(llm.chat(org.mockito.ArgumentMatchers.anyString())).thenReturn("这不是合法JSON");
        evaluator = new AnswerEvaluatorImpl(properties, llm);

        List<RetrievalResult> results = List.of(
                new RetrievalResult("Spring Boot 是 Java 框架", 1.0, "s", 1L, 0, "c1", "fused"));
        AnswerEvaluation r = evaluator.evaluate("Spring Boot 是什么", results, "Spring Boot 是 Java 框架");
        assertNotNull(r);
        assertEquals("RULE", r.method(), "LLM 解析失败应回退规则版");
    }

    @Test
    void LLM版_合法JSON返回LLM评价() {
        properties.getEval().setLlmEvalEnabled(true);
        LLMService llm = mock(LLMService.class);
        when(llm.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"faithfulness\": 0.9, \"relevance\": 0.8, \"reason\": \"回答准确\"}");
        evaluator = new AnswerEvaluatorImpl(properties, llm);

        List<RetrievalResult> results = List.of(
                new RetrievalResult("Spring Boot 是 Java 框架", 1.0, "s", 1L, 0, "c1", "fused"));
        AnswerEvaluation r = evaluator.evaluate("Spring Boot 是什么", results, "Spring Boot 是 Java 框架");
        assertNotNull(r);
        assertEquals("LLM", r.method());
        assertEquals(0.9, r.faithfulness(), 0.001);
        assertEquals(0.8, r.relevance(), 0.001);
    }

    @Test
    void allowLlm为false时跳过LLM评价() {
        properties.getEval().setLlmEvalEnabled(true);
        LLMService llm = mock(LLMService.class);
        when(llm.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"faithfulness\": 0.9, \"relevance\": 0.8, \"reason\": \"x\"}");
        evaluator = new AnswerEvaluatorImpl(properties, llm);

        List<RetrievalResult> results = List.of(
                new RetrievalResult("Spring Boot 是 Java 框架", 1.0, "s", 1L, 0, "c1", "fused"));
        AnswerEvaluation r = evaluator.evaluate("Spring Boot", results, "Spring Boot 是 Java 框架", false);
        assertEquals("RULE", r.method(), "allowLlm=false 应跳过 LLM 评价走规则版");
    }
}
