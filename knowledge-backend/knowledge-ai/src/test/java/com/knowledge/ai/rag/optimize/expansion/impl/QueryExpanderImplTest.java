/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.rag.optimize.expansion.impl;

import com.knowledge.ai.rag.optimize.config.RagOptimizeProperties;
import com.knowledge.ai.service.LLMService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 查询扩展器单元测试：扩展词解析与拼装 / 去重截断 / 开关降级 / 异常回退。
 */
class QueryExpanderImplTest {

    private RagOptimizeProperties properties;
    private LLMService llm;
    private QueryExpanderImpl expander;

    @BeforeEach
    void setUp() {
        properties = new RagOptimizeProperties();
        properties.getExpansion().setEnabled(true);
        properties.getExpansion().setMaxTerms(5);
        llm = mock(LLMService.class);
        expander = new QueryExpanderImpl(properties, llm);
    }

    @Test
    void 开关关闭返回原问题() {
        properties.getExpansion().setEnabled(false);
        assertEquals("Spring Boot", expander.expand("Spring Boot"));
    }

    @Test
    void 空问题返回原问题() {
        assertEquals("", expander.expand(""));
    }

    @Test
    void 合法JSON拼装原问题与扩展词() {
        when(llm.chat(anyString())).thenReturn("{\"terms\": [\"Spring框架\", \"Java框架\"]}");
        String kw = expander.expand("Spring Boot");
        assertTrue(kw.startsWith("Spring Boot"), "应以原问题开头");
        assertTrue(kw.contains("Spring框架"));
        assertTrue(kw.contains("Java框架"));
    }

    @Test
    void 空扩展词返回原问题() {
        when(llm.chat(anyString())).thenReturn("{\"terms\": []}");
        assertEquals("Spring Boot", expander.expand("Spring Boot"));
    }

    @Test
    void 扩展词去重并截断至上限() {
        properties.getExpansion().setMaxTerms(2);
        when(llm.chat(anyString())).thenReturn(
                "{\"terms\": [\"A\", \"A\", \"B\", \"C\"]}");
        String kw = expander.expand("Q");
        // 去重后 A、B、C 取前 2 → A、B
        assertTrue(kw.contains("A"));
        assertTrue(kw.contains("B"));
        assertTrue(!kw.contains("C"), "超过 maxTerms=2 的词应被截断");
    }

    @Test
    void 非法JSON回退原问题() {
        when(llm.chat(anyString())).thenReturn("不是JSON");
        assertEquals("Spring Boot", expander.expand("Spring Boot"));
    }

    @Test
    void LLM异常回退原问题() {
        when(llm.chat(anyString())).thenThrow(new RuntimeException("失败"));
        assertEquals("Spring Boot", expander.expand("Spring Boot"));
    }
}
