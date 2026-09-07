package com.knowledge.ai.parser;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * MinerU服务客户端
 * <p>
 * 调用MinerU微服务解析PDF文档
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.parser.type", havingValue = "mineru")
public class MinerUClient {

    private final RestTemplate restTemplate;

    @Value("${ai.parser.mineru.url:http://localhost:8000}")
    private String minerUUrl;

    @Value("${ai.parser.mineru.timeout:120}")
    private int timeoutSeconds;

    @Value("${ai.parser.mineru.max-retries:2}")
    private int maxRetries;

    public MinerUClient(@Qualifier("minerURestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private static final String RESOURCE = "mineru:parse";

    /**
     * 解析PDF文件
     *
     * @param pdfBytes PDF文件字节数组
     * @param filename 文件名（用于日志）
     * @return 解析结果
     */
    @SentinelResource(value = RESOURCE, blockHandler = "parsePdfBlockHandler")
    public MinerUResponse parsePdf(byte[] pdfBytes, String filename) {
        String url = minerUUrl + "/parse";
        log.info("调用MinerU服务: url={}, file={}, size={}KB",
                url, filename, pdfBytes.length / 1024);

        // 构建multipart请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileResource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return filename != null ? filename : "document.pdf";
            }
        };
        body.add("file", fileResource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // 带重试的请求
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                long startTime = System.currentTimeMillis();

                ResponseEntity<MinerUResponse> response = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        requestEntity,
                        MinerUResponse.class
                );

                long elapsed = System.currentTimeMillis() - startTime;
                log.info("MinerU响应: status={}, elapsed={}ms", response.getStatusCode(), elapsed);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    MinerUResponse result = response.getBody();
                    if (result.isSuccess()) {
                        log.info("MinerU解析成功: title={}, pages={}, tables={}",
                                result.getTitle(), result.getTotalPages(),
                                result.getTables() != null ? result.getTables().size() : 0);
                        return result;
                    } else {
                        throw new RuntimeException("MinerU返回错误: " + result.getMessage());
                    }
                } else {
                    throw new RuntimeException("MinerU返回异常状态: " + response.getStatusCode());
                }

            } catch (ResourceAccessException e) {
                lastException = e;
                log.warn("MinerU调用失败 (attempt {}/{}): {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000L * attempt); // 退避重试
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (Exception e) {
                lastException = e;
                log.error("MinerU调用异常: {}", e.getMessage());
                break; // 非网络错误不重试
            }
        }

        throw new RuntimeException("MinerU调用失败: " + (lastException != null ? lastException.getMessage() : "未知错误"), lastException);
    }

    /**
     * Sentinel 熔断降级处理：快速失败，让上层 SmartDocumentParser 走 Tika 降级
     */
    public MinerUResponse parsePdfBlockHandler(byte[] pdfBytes, String filename, BlockException ex) {
        log.warn("[MinerU] Sentinel 限流/熔断降级: resource={}, rule={}", RESOURCE,
                ex.getRule() != null ? ex.getRule().getResource() : "unknown");
        throw new RuntimeException("MinerU 服务熔断降级（Sentinel），请稍后重试或使用 Tika 解析", ex);
    }

    /**
     * 检查MinerU服务健康状态
     */
    public boolean isHealthy() {
        try {
            String url = minerUUrl + "/health";
            ResponseEntity<HealthResponse> response = restTemplate.getForEntity(url, HealthResponse.class);
            return response.getStatusCode().is2xxSuccessful() && response.getBody() != null;
        } catch (Exception e) {
            log.warn("MinerU健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * MinerU解析响应
     */
    @Data
    public static class MinerUResponse {
        private String status;
        private String markdown;
        private String title;

        @JsonProperty("total_pages")
        private int totalPages;

        private List<TableInfo> tables;
        private String message;

        public boolean isSuccess() {
            return "success".equals(status);
        }
    }

    /**
     * 表格信息
     */
    @Data
    public static class TableInfo {
        @JsonProperty("page_number")
        private int pageNumber;
        private String content;
        private int rows;
        private int cols;
    }

    /**
     * 健康检查响应
     */
    @Data
    public static class HealthResponse {
        private String status;
        private String service;
        private String version;
    }
}
