/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.rag.optimize.rewrite.impl;

import com.knowledge.ai.rag.optimize.config.RagOptimizeProperties;
import com.knowledge.ai.rag.optimize.dto.RewriteResult;
import com.knowledge.ai.service.LLMService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 查询改写器单元测试：改写解析 / 多查询截断 / 开关降级 / 异常回退。
 */
class QueryRewriterImplTest {

    private RagOptimizeProperties properties;
    private LLMService llm;
    private QueryRewriterImpl rewriter;

    @BeforeEach
    void setUp() {
        properties = new RagOptimizeProperties();
        properties.getRewrite().setEnabled(true);
        properties.getRewrite().setMultiQueryEnabled(false);
        properties.getRewrite().setMaxSubQueries(3);
        properties.getRewrite().setIncludeHistory(true);
        llm = mock(LLMService.class);
        rewriter = new QueryRewriterImpl(properties, llm);
    }

    @Test
    void 开关关闭返回原问题回退() {
        properties.getRewrite().setEnabled(false);
        RewriteResult r = rewriter.rewrite("它是什么", List.of());
        assertEquals("它是什么", r.primaryQuery());
        assertTrue(r.subQueries().isEmpty());
    }

    @Test
    void 空问题返回回退() {
        RewriteResult r = rewriter.rewrite("", List.of());
        assertEquals("", r.primaryQuery());
        assertTrue(r.subQueries().isEmpty());
    }

    @Test
    void 合法JSON返回改写主查询() {
        when(llm.chat(anyString())).thenReturn(
                "{\"primary\": \"Spring Boot 是什么框架\", \"subQueries\": []}");
        RewriteResult r = rewriter.rewrite("它是什么框架", List.of());
        assertEquals("Spring Boot 是什么框架", r.primaryQuery());
    }

    @Test
    void 多查询开启时解析子查询并截断() {
        properties.getRewrite().setMultiQueryEnabled(true);
        properties.getRewrite().setMaxSubQueries(2);
        when(llm.chat(anyString())).thenReturn(
                "{\"primary\": \"Spring Boot 框架介绍\", \"subQueries\": [\"Q1\", \"Q2\", \"Q3\"]}");
        RewriteResult r = rewriter.rewrite("它", List.of());
        assertEquals("Spring Boot 框架介绍", r.primaryQuery());
        assertEquals(2, r.subQueries().size(), "应截断至 maxSubQueries=2");
    }

    @Test
    void 多查询关闭时忽略JSON中的子查询() {
        properties.getRewrite().setMultiQueryEnabled(false);
        when(llm.chat(anyString())).thenReturn(
                "{\"primary\": \"主查询\", \"subQueries\": [\"Q1\", \"Q2\"]}");
        RewriteResult r = rewriter.rewrite("它", List.of());
        assertTrue(r.subQueries().isEmpty(), "多查询关闭时 subQueries 应为空");
    }

    @Test
    void 非法JSON回退原问题() {
        when(llm.chat(anyString())).thenReturn("这不是合法JSON");
        RewriteResult r = rewriter.rewrite("它是什么", List.of());
        assertEquals("它是什么", r.primaryQuery());
        assertTrue(r.subQueries().isEmpty());
    }

    @Test
    void primary缺失回退原问题() {
        when(llm.chat(anyString())).thenReturn("{\"subQueries\": []}");
        RewriteResult r = rewriter.rewrite("它是什么", List.of());
        assertEquals("它是什么", r.primaryQuery());
    }

    @Test
    void LLM异常回退原问题() {
        when(llm.chat(anyString())).thenThrow(new RuntimeException("超时"));
        RewriteResult r = rewriter.rewrite("它是什么", List.of());
        assertEquals("它是什么", r.primaryQuery());
        assertTrue(r.subQueries().isEmpty());
    }
}
