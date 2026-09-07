package com.knowledge.agent.tool;

import com.knowledge.agent.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 请求工具：发起 GET/POST 请求，受白名单管控防 SSRF。
 * <p>
 * <b>SSRF 防护</b>：请求 URL 的 host 必须命中 {@code agent.tool.http.allowed-hosts} 白名单
 * （支持精确匹配与 {@code *.example.com} 子域通配）；仅允许 http/https 协议。默认拒绝，
 * 未命中白名单直接返回失败，不发起请求。
 * <p>
 * <b>副作用工具授权</b>：authRequired=false，由 tool_permission 角色级授权控制。
 * <p>
 * <b>超时</b>：连接/读取超时由 {@link RestTemplate} 全局配置（agent.tool.http.connect/read-timeout-ms）
 * 兜底；入参 timeoutMs 仅作记录，实际超时以全局配置为准（避免每请求新建工厂）。
 * <p>
 * <b>响应截断</b>：body 截断到 {@value #MAX_BODY_CHARS} 字符，防止超大响应耗尽 token 预算。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpTool implements Tool {

    private static final int MAX_BODY_CHARS = 8000;

    private final RestTemplate restTemplate;
    private final AgentProperties props;

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "url": {"type": "string", "description": "请求URL(须在白名单内)"},
                "method": {"type": "string", "description": "HTTP方法 GET/POST", "default": "GET"},
                "headers": {"type": "object", "description": "请求头"},
                "body": {"type": "string", "description": "请求体(POST)"},
                "timeoutMs": {"type": "integer", "description": "超时毫秒(受全局配置上限约束)"}
              },
              "required": ["url"]
            }""";

    @Override
    public String name() {
        return "http_request";
    }

    @Override
    public String description() {
        return "发起 HTTP GET/POST 请求，受白名单管控防 SSRF。"
                + "仅允许请求白名单内主机(支持 *.domain 子域通配)。";
    }

    @Override
    public String parametersJsonSchema() {
        return SCHEMA;
    }

    @Override
    public boolean authRequired() {
        return false;
    }

    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> arguments) {
        String url = String.valueOf(arguments.get("url")).trim();
        String method = arguments.containsKey("method") && arguments.get("method") != null
                ? String.valueOf(arguments.get("method")).trim().toUpperCase()
                : "GET";

        // 1. SSRF 校验：协议 + host 白名单
        String hostError = validateHost(url);
        if (hostError != null) {
            return ToolResult.failure(hostError);
        }
        if (!"GET".equals(method) && !"POST".equals(method)) {
            return ToolResult.failure("仅支持 GET/POST 方法，当前: " + method);
        }

        // 2. 组装请求头
        HttpHeaders headers = new HttpHeaders();
        Object headersArg = arguments.get("headers");
        if (headersArg instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    headers.add(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
        }

        // 3. 发起请求
        HttpMethod httpMethod = "POST".equals(method) ? HttpMethod.POST : HttpMethod.GET;
        String body = arguments.get("body") == null ? null : String.valueOf(arguments.get("body"));
        HttpEntity<String> entity;
        if (body != null && !body.isEmpty()) {
            if (headers.getContentType() == null) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }
            entity = new HttpEntity<>(body, headers);
        } else {
            entity = new HttpEntity<>(headers);
        }

        ResponseEntity<String> resp;
        try {
            resp = restTemplate.exchange(url, httpMethod, entity, String.class);
        } catch (RestClientException e) {
            log.warn("[HttpRequest] 请求失败 url={} method={}: {}", url, method, e.getMessage());
            return ToolResult.failure("HTTP 请求失败: " + e.getMessage());
        }

        // 4. 组装响应（body 截断）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", resp.getStatusCode().value());
        Map<String, String> respHeaders = new LinkedHashMap<>();
        if (resp.getHeaders() != null) {
            resp.getHeaders().forEach((k, v) -> respHeaders.put(k, v == null ? "" : String.join(",", v)));
        }
        data.put("headers", respHeaders);
        String respBody = resp.getBody() == null ? "" : resp.getBody();
        boolean truncated = respBody.length() > MAX_BODY_CHARS;
        data.put("body", truncated ? respBody.substring(0, MAX_BODY_CHARS) : respBody);
        data.put("truncated", truncated);

        log.info("[HttpRequest] task={} url={} method={} status={} bodyLen={}",
                ctx.getTaskId(), url, method, resp.getStatusCode().value(), respBody.length());
        return ToolResult.success(data);
    }

    /**
     * SSRF 校验：仅允许 http/https 协议，host 必须命中白名单。
     *
     * @return 校验失败返回错误信息，通过返回 null
     */
    private String validateHost(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return "URL 格式非法: " + url;
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            return "仅允许 http/https 协议，当前: " + scheme;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "URL 缺少 host: " + url;
        }
        host = host.toLowerCase();

        List<String> allowed = props.getTool().getHttp().getAllowedHosts();
        if (allowed == null || allowed.isEmpty()) {
            return "未配置 HTTP 白名单(agent.tool.http.allowed-hosts)，拒绝所有请求";
        }
        for (String pattern : allowed) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            String p = pattern.trim().toLowerCase();
            if (matchesHost(host, p)) {
                return null; // 命中白名单
            }
        }
        return "目标 host 不在白名单内: " + host + "（白名单: " + allowed + "）";
    }

    /**
     * host 白名单匹配：
     * <ul>
     *   <li>精确匹配（如 {@code localhost} / {@code api.example.com}）</li>
     *   <li>{@code *.example.com}：匹配任意层级子域（host 以 {@code .example.com} 结尾）</li>
     * </ul>
     */
    private static boolean matchesHost(String host, String pattern) {
        if (pattern.equals(host)) {
            return true;
        }
        if (pattern.startsWith("*.")) {
            String base = pattern.substring(2);
            return host.endsWith("." + base);
        }
        return false;
    }
}
