/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.ai.search.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.ai.config.AiProperties;
import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.search.SearchConstants;
import com.knowledge.common.alert.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * HTTP 精排服务单元测试：响应映射（mapResults）+ 配置缺失/空候选降级。
 * <p>真实 HTTP 往返属集成测试范畴，此处聚焦可纯逻辑验证的映射与降级路径。
 */
class HttpRerankServiceTest {

    private AiProperties.Rerank cfg;
    private HttpRerankService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AiProperties aiProperties = new AiProperties();
        cfg = aiProperties.getRag().getHybrid().getRerank();
        cfg.setType("http");
        cfg.setUrl("https://api.siliconflow.cn/v1/rerank");
        cfg.setApiKey("sk-test");
        cfg.setModel("BAAI/bge-reranker-v2-m3");
        cfg.setTimeoutMs(1000);
        service = new HttpRerankService(aiProperties, mock(AlertService.class));
    }

    @Test
    void rerank_空候选返回空() {
        assertTrue(service.rerank("q", List.of(), 5).isEmpty());
    }

    @Test
    void rerank_topN零返回空() {
        List<RetrievalResult> cs = List.of(result("c1"), result("c2"));
        assertTrue(service.rerank("q", cs, 0).isEmpty());
    }

    @Test
    void rerank_配置缺失降级为顺序截断() {
        cfg.setUrl(null); // 配置缺失，应跳过 HTTP 调用直接降级
        List<RetrievalResult> cs = List.of(result("c1"), result("c2"), result("c3"));
        List<RetrievalResult> out = service.rerank("q", cs, 2);
        assertEquals(2, out.size());
        assertEquals("c1", out.get(0).getChunkId());
        assertEquals("c2", out.get(1).getChunkId());
    }

    @Test
    void mapResults_合法响应按分降序映射并标记rerank() throws Exception {
        List<RetrievalResult> cs = List.of(result("c0"), result("c1"));
        JsonNode resp = mapper.readTree(
                "{\"results\":[{\"index\":1,\"relevance_score\":0.5},{\"index\":0,\"relevance_score\":0.9}]}");
        List<RetrievalResult> out = service.mapResults(resp, cs);
        assertEquals(2, out.size());
        assertEquals("c0", out.get(0).getChunkId(), "0.9 分对应 index 0 应排首位");
        assertEquals(0.9, out.get(0).getScore(), 0.001);
        assertEquals("c1", out.get(1).getChunkId());
        assertEquals(0.5, out.get(1).getScore(), 0.001);
        assertEquals(SearchConstants.SCORE_RERANK, out.get(0).getScoreType());
    }

    @Test
    void mapResults_无results字段返回空() throws Exception {
        List<RetrievalResult> cs = List.of(result("c0"));
        JsonNode resp = mapper.readTree("{\"foo\":1}");
        assertTrue(service.mapResults(resp, cs).isEmpty());
    }

    @Test
    void mapResults_null返回空() {
        assertTrue(service.mapResults(null, List.of(result("c0"))).isEmpty());
    }

    @Test
    void mapResults_索引越界跳过() throws Exception {
        List<RetrievalResult> cs = List.of(result("c0"));
        JsonNode resp = mapper.readTree(
                "{\"results\":[{\"index\":0,\"relevance_score\":0.9},{\"index\":9,\"relevance_score\":0.8}]}");
        List<RetrievalResult> out = service.mapResults(resp, cs);
        assertEquals(1, out.size(), "越界 index=9 应被跳过");
        assertEquals("c0", out.get(0).getChunkId());
    }

    private RetrievalResult result(String chunkId) {
        return new RetrievalResult("文本 " + chunkId, 0.5, "src", 1L, 0, chunkId, "fused");
    }
}
