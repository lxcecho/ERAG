package com.knowledge.ai.search.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.databind.JsonNode;
import com.knowledge.ai.config.AiProperties;
import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.search.RerankService;
import com.knowledge.ai.search.SearchConstants;
import com.knowledge.common.alert.AlertLevel;
import com.knowledge.common.alert.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 精排实现：对接外部 Cross-Encoder Rerank 服务（SiliconFlow 兼容 /v1/rerank）。
 * <p>当 {@code ai.rag.hybrid.rerank.type=http} 时装配，与 {@link NoopRerankService} 按 type 互斥。
 * <p>请求契约（SiliconFlow / bge-reranker 兼容）：
 * <pre>{@code
 * POST {url}
 * Authorization: Bearer {api-key}
 * {"model":"...","query":"问题","documents":["t1","t2"],"top_n":N,"return_documents":false}
 * }</pre>
 * 响应：{@code {"results":[{"index":0,"relevance_score":0.95},...]}}
 * <p><b>best-effort 降级</b>：任何异常（网络/超时/解析/配置缺失）都不阻断主流程，
 * 退化为按原顺序截断 topN（等价 noop），仅 warn 日志。Rerank 是锦上添花，不可拖垮检索。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "ai.rag.hybrid.rerank", name = "type", havingValue = "http")
public class HttpRerankService implements RerankService {

    private final AiProperties.Rerank cfg;
    private final RestClient restClient;
    private final AlertService alertService;

    public HttpRerankService(AiProperties aiProperties, AlertService alertService) {
        this.cfg = aiProperties.getRag().getHybrid().getRerank();
        this.alertService = alertService;
        int timeoutMs = cfg.getTimeoutMs() > 0 ? cfg.getTimeoutMs() : 5000;
        this.restClient = RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {
                    {
                        setConnectTimeout(timeoutMs);
                        setReadTimeout(timeoutMs);
                    }
                })
                .build();
        log.info("[Rerank] 启用 HTTP 精排 url={} model={} timeout={}ms", cfg.getUrl(), cfg.getModel(), timeoutMs);
    }

    @Override
    @SentinelResource(value = "rerank:http", blockHandler = "rerankBlockHandler")
    public List<RetrievalResult> rerank(String question, List<RetrievalResult> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (topN <= 0) {
            return List.of();
        }
        // 配置缺失：直接降级截断（不发起请求）
        if (isBlank(cfg.getUrl()) || isBlank(cfg.getApiKey()) || isBlank(cfg.getModel())) {
            log.warn("[Rerank-HTTP] url/apiKey/model 未配置，降级为顺序截断 topN={}", topN);
            return truncate(candidates, topN);
        }
        if (isBlank(question)) {
            question = "";
        }

        try {
            List<String> documents = new ArrayList<>(candidates.size());
            for (RetrievalResult r : candidates) {
                documents.add(r.getText() == null ? "" : r.getText());
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", cfg.getModel());
            body.put("query", question);
            body.put("documents", documents);
            body.put("top_n", Math.min(topN, candidates.size()));
            body.put("return_documents", false);

            JsonNode resp = restClient.post()
                    .uri(cfg.getUrl())
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            List<RetrievalResult> ranked = mapResults(resp, candidates);
            if (ranked.isEmpty()) {
                log.warn("[Rerank-HTTP] 响应无可解析结果，降级顺序截断");
                return truncate(candidates, topN);
            }
            log.info("[Rerank-HTTP] 候选 {} → 精排 {} 条", candidates.size(), ranked.size());
            return ranked;
        } catch (Exception e) {
            log.warn("[Rerank-HTTP] 调用失败，降级顺序截断: {}", e.getMessage());
            return truncate(candidates, topN);
        }
    }

    /** 将 rerank 响应映射回 RetrievalResult（按 relevance_score 降序，scoreType=rerank） */
    List<RetrievalResult> mapResults(JsonNode resp, List<RetrievalResult> candidates) {
        if (resp == null || !resp.has("results")) {
            return List.of();
        }
        JsonNode results = resp.get("results");
        List<RetrievalResult> out = new ArrayList<>(results.size());
        for (JsonNode item : results) {
            int index = item.path("index").asInt(-1);
            double score = item.path("relevance_score").asDouble(0d);
            if (index < 0 || index >= candidates.size()) {
                continue;
            }
            RetrievalResult r = candidates.get(index);
            out.add(new RetrievalResult(
                    r.getText(),
                    score,
                    r.getSource(),
                    r.getDocumentId(),
                    r.getChunkIndex(),
                    r.getChunkId(),
                    SearchConstants.SCORE_RERANK));
        }
        // 防御性排序：不依赖外部 API 是否预排序，统一按 relevance_score 降序
        out.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return out;
    }

    private static List<RetrievalResult> truncate(List<RetrievalResult> candidates, int topN) {
        return candidates.size() <= topN ? candidates : new ArrayList<>(candidates.subList(0, topN));
    }

    /**
     * Rerank 限流/熔断降级：返回原顺序截断（与 best-effort try-catch 降级一致）。
     * <p>注意：rerank 方法体内部已 try-catch 吞异常自降级，故 Sentinel 异常熔断不会自动触发
     * （异常未抛出方法外）；此 blockHandler 主要承接限流规则（Dashboard 可配 QPS 上限防 rerank 调用风暴）。
     */
    public List<RetrievalResult> rerankBlockHandler(String question, List<RetrievalResult> candidates,
                                                    int topN, BlockException ex) {
        alertService.alert(AlertLevel.WARN, "rerank:http", "Rerank限流降级",
                "blockType=" + ex.getClass().getSimpleName(), ex);
        log.warn("[Rerank-HTTP] 限流/熔断降级，返回顺序截断 topN={}", topN);
        return truncate(candidates, topN);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
