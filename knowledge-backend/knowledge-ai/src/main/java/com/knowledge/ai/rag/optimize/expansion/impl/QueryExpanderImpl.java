package com.knowledge.ai.rag.optimize.expansion.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.ai.prompt.PromptTemplates;
import com.knowledge.ai.rag.optimize.config.RagOptimizeProperties;
import com.knowledge.ai.rag.optimize.expansion.QueryExpander;
import com.knowledge.ai.rag.optimize.prompt.OptimizePrompts;
import com.knowledge.ai.service.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 查询扩展 LLM 实现：调用 LLM 生成同义词 / 近义词 / 专业术语，拼装为关键词增强查询。
 * <p>流程：渲染 {@link OptimizePrompts#EXPANSION_TEMPLATE} → LLM → 解析 JSON {terms} → 去重截断 → 拼装。
 * <p>拼装策略：keywordQuery = 原问题 + " " + 扩展词（空格连接）。
 * 仅作用于 BM25 词法路（三查询解耦的 keywordQuery），不污染向量路与精排基准。
 * <p>best-effort：任一步异常返回原问题。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryExpanderImpl implements QueryExpander {

    private final RagOptimizeProperties properties;
    private final LLMService llmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String expand(String question) {
        if (question == null || question.isBlank()) {
            return question;
        }
        if (!properties.getExpansion().isEnabled()) {
            return question;
        }
        try {
            int maxTerms = properties.getExpansion().getMaxTerms();
            String prompt = PromptTemplates.render(OptimizePrompts.EXPANSION_TEMPLATE, Map.of(
                    "question", question,
                    "max_terms", String.valueOf(maxTerms)));
            String resp = llmService.chat(prompt);
            List<String> terms = parseTerms(resp, maxTerms);
            if (terms.isEmpty()) {
                log.info("[优化-扩展] 无扩展词，沿用原问题");
                return question;
            }
            String keywordQuery = question + " " + String.join(" ", terms);
            log.info("[优化-扩展] 原: {} → 扩展 {} 词: {}", question, terms.size(), terms);
            return keywordQuery;
        } catch (Exception e) {
            log.warn("[优化-扩展] 扩展失败，回退原问题: {}", e.getMessage());
            return question;
        }
    }

    private List<String> parseTerms(String resp, int max) {
        JsonNode node = parseJsonObject(resp);
        if (node == null) {
            return List.of();
        }
        JsonNode arr = node.get("terms");
        if (arr == null || !arr.isArray()) {
            return List.of();
        }
        List<String> seen = new ArrayList<>();
        for (JsonNode item : arr) {
            String s = item.asText("").trim();
            if (!s.isEmpty() && !seen.contains(s)) {
                seen.add(s);
            }
            if (seen.size() >= max) {
                break;
            }
        }
        return seen;
    }

    /** 从可能含 Markdown 包裹的文本中提取首个 JSON 对象 */
    private JsonNode parseJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(text.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }
}
