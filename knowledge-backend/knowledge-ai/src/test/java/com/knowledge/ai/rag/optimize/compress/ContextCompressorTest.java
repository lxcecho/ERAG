/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.rag.optimize.compress;

import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.rag.optimize.config.RagOptimizeProperties;
import com.knowledge.ai.service.LLMService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 上下文压缩器单元测试：去重（Jaccard bigram）+ char 预算截断 + 开关降级。
 * <p>LLM 抽取压缩默认关，LLMService 不被调用，故传入 mock 即可（无需桩）。
 */
class ContextCompressorTest {

    private RagOptimizeProperties properties;
    private ContextCompressor compressor;

    @BeforeEach
    void setUp() {
        properties = new RagOptimizeProperties();
        properties.getCompress().setEnabled(true);
        properties.getCompress().setDedupThreshold(0.8);
        properties.getCompress().setMaxContextChars(6000);
        properties.getCompress().setLlmCompressEnabled(false);
        compressor = new ContextCompressor(properties, mock(LLMService.class));
    }

    @Test
    void compress_空输入返回空() {
        assertTrue(compressor.compress(List.of(), "q").isEmpty());
        assertTrue(compressor.compress(null, "q").isEmpty());
    }

    @Test
    void compress_开关关闭原样返回() {
        properties.getCompress().setEnabled(false);
        List<RetrievalResult> input = List.of(result("文本A", "c1"), result("文本B", "c2"));
        List<RetrievalResult> out = compressor.compress(input, "q");
        assertEquals(2, out.size());
    }

    @Test
    void dedup_完全相同文本去重为一条() {
        List<RetrievalResult> input = List.of(
                result("知识库检索增强生成技术方案", "c1"),
                result("知识库检索增强生成技术方案", "c2"));
        List<RetrievalResult> out = compressor.compress(input, "q");
        assertEquals(1, out.size(), "完全相同文本应去重为一条");
    }

    @Test
    void dedup_不同文本均保留() {
        List<RetrievalResult> input = List.of(
                result("苹果香蕉橘子葡萄", "c1"),
                result("Java Spring Boot 框架", "c2"));
        List<RetrievalResult> out = compressor.compress(input, "q");
        assertEquals(2, out.size(), "语义不同的文本应都保留");
    }

    @Test
    void truncate_超预算截断保留高排名() {
        properties.getCompress().setMaxContextChars(10);
        List<RetrievalResult> input = List.of(
                result("12345", "c1"),    // 5 字符，used=5
                result("67890", "c2"),    // 5 字符，used=10
                result("abcdef", "c3"));  // +6=16>10，且 out 非空 → 截断
        List<RetrievalResult> out = compressor.compress(input, "q");
        assertEquals(2, out.size(), "超预算后应截断，保留前两条");
        assertEquals("c1", out.get(0).getChunkId());
        assertEquals("c2", out.get(1).getChunkId());
    }

    @Test
    void dedup_近似重复文本按阈值去重() {
        // 两段文本高度相似（仅结尾差一字符），bigram Jaccard 应 ≥ 0.8
        List<RetrievalResult> input = List.of(
                result("企业级AI知识库助手检索增强生成方案设计文档", "c1"),
                result("企业级AI知识库助手检索增强生成方案设计文档", "c2"));
        List<RetrievalResult> out = compressor.compress(input, "q");
        assertEquals(1, out.size(), "近似重复文本应去重");
    }

    private RetrievalResult result(String text, String chunkId) {
        return new RetrievalResult(text, 1.0, "src", 1L, 0, chunkId, "fused");
    }
}
