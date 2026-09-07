/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.agent.tool;

import com.knowledge.agent.config.AgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link HttpTool} 单元测试：验证 SSRF 白名单防护、协议校验、请求委托与异常处理。
 */
@ExtendWith(MockitoExtension.class)
class HttpToolTest {

    @Mock
    private RestTemplate restTemplate;

    private AgentProperties props;

    @BeforeEach
    void setUp() {
        props = new AgentProperties();
        props.getTool().getHttp().setAllowedHosts(List.of("localhost", "*.example.com"));
    }

    @Test
    void should_reject_non_whitelisted_host() {
        HttpTool tool = new HttpTool(restTemplate, props);
        ToolResult result = tool.execute(newContext(),
                Map.of("url", "https://evil.com/x"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("白名单"));
    }

    @Test
    void should_reject_non_http_scheme() {
        HttpTool tool = new HttpTool(restTemplate, props);
        ToolResult result = tool.execute(newContext(),
                Map.of("url", "ftp://localhost/x"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("http/https"));
    }

    @Test
    void should_reject_when_whitelist_empty() {
        props.getTool().getHttp().setAllowedHosts(List.of());
        HttpTool tool = new HttpTool(restTemplate, props);
        ToolResult result = tool.execute(newContext(),
                Map.of("url", "http://localhost/x"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("未配置"));
    }

    @Test
    void should_allow_glob_subdomain() {
        when(restTemplate.exchange(eq("https://api.example.com/x"), any(HttpMethod.class),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        HttpTool tool = new HttpTool(restTemplate, props);
        ToolResult result = tool.execute(newContext(),
                Map.of("url", "https://api.example.com/x"));

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(200, data.get("status"));
        assertEquals("ok", data.get("body"));
    }

    @Test
    void should_reject_glob_matching_base_domain_itself() {
        // *.example.com 仅匹配子域，example.com 本身不应命中（SSRF 安全保守策略）
        HttpTool tool = new HttpTool(restTemplate, props);
        ToolResult result = tool.execute(newContext(),
                Map.of("url", "https://example.com/x"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("白名单"));
    }

    @Test
    void should_execute_get_for_whitelisted_localhost() {
        when(restTemplate.exchange(eq("http://localhost:8080/ping"), any(HttpMethod.class),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("pong", HttpStatus.OK));

        HttpTool tool = new HttpTool(restTemplate, props);
        ToolResult result = tool.execute(newContext(),
                Map.of("url", "http://localhost:8080/ping", "method", "GET"));

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(200, data.get("status"));
        assertEquals("pong", data.get("body"));
        assertEquals(false, data.get("truncated"));
    }

    @Test
    void should_reject_unsupported_method() {
        HttpTool tool = new HttpTool(restTemplate, props);
        ToolResult result = tool.execute(newContext(),
                Map.of("url", "http://localhost/x", "method", "DELETE"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("GET/POST"));
    }

    @Test
    void should_truncate_large_body() {
        String big = "x".repeat(10000);
        when(restTemplate.exchange(any(String.class), any(HttpMethod.class),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(big, HttpStatus.OK));

        HttpTool tool = new HttpTool(restTemplate, props);
        ToolResult result = tool.execute(newContext(),
                Map.of("url", "http://localhost/x"));

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(true, data.get("truncated"));
        String body = (String) data.get("body");
        assertEquals(8000, body.length());
    }

    @Test
    void should_return_failure_on_rest_exception() {
        when(restTemplate.exchange(any(String.class), any(HttpMethod.class),
                any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("conn refused"));

        HttpTool tool = new HttpTool(restTemplate, props);
        ToolResult result = tool.execute(newContext(),
                Map.of("url", "http://localhost/x"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("HTTP 请求失败"));
    }

    @Test
    void should_not_require_auth() {
        HttpTool tool = new HttpTool(restTemplate, props);
        assertFalse(tool.authRequired(), "HTTP 工具为副作用工具，由角色级授权控制");
    }

    @Test
    void should_have_expected_tool_name() {
        HttpTool tool = new HttpTool(restTemplate, props);
        assertEquals("http_request", tool.name());
    }

    // ==================== 测试辅助 ====================

    private static ToolContext newContext() {
        return ToolContext.builder()
                .tenantId(1L).userId(10L).kbId(7L).taskId(100L).stepId(null).build();
    }
}
