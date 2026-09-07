package com.knowledge.ai.rag.optimize.rewrite.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.ai.prompt.PromptTemplates;
import com.knowledge.ai.rag.optimize.config.RagOptimizeProperties;
import com.knowledge.ai.rag.optimize.dto.CombinedQueryResult;
import com.knowledge.ai.rag.optimize.dto.RewriteResult;
import com.knowledge.ai.rag.optimize.prompt.OptimizePrompts;
import com.knowledge.ai.rag.optimize.rewrite.QueryRewriter;
import com.knowledge.ai.service.LLMService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询改写 LLM 实现：调用 LLM 做指代消解改写 + 可选多查询生成。
 * <p>流程：组装历史 → 渲染 {@link OptimizePrompts#REWRITE_TEMPLATE} → LLM → 解析 JSON {primary, subQueries}。
 * <p>best-effort：任一步异常回退 {@link RewriteResult#fallback}（主查询=原问题，无子查询）。
 * <p>多查询：仅 multi-query-enabled=true 时解析 subQueries 并截断至 maxSubQueries；否则强制空列表。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryRewriterImpl implements QueryRewriter {

    private final RagOptimizeProperties properties;
    private final LLMService llmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 合并改写+扩展：一次 LLM 调用同时输出 primary / subQueries / keywordQuery。
     * <p>best-effort：异常回退 {@link CombinedQueryResult#fallback}。
     */
    public CombinedQueryResult combinedRewriteExpand(String question, List<ChatMessage> history) {
        if (question == null || question.isBlank()) {
            return CombinedQueryResult.fallback(question);
        }
        RagOptimizeProperties.Rewrite cfg = properties.getRewrite();
        RagOptimizeProperties.Expansion expCfg = properties.getExpansion();
        if (!cfg.isEnabled() && !expCfg.isEnabled()) {
            return CombinedQueryResult.fallback(question);
        }
        try {
            String historyText = cfg.isIncludeHistory() ? formatHistory(history) : "（无）";
            String directive = cfg.isMultiQueryEnabled()
                    ? "请同时生成至多 " + cfg.getMaxSubQueries() + " 个语义相关但表述不同的备选查询，用于多路召回提升召回率。"
                    : "无需生成备选查询，subQueries 字段返回空数组。";
            int maxTerms = expCfg.isEnabled() ? expCfg.getMaxTerms() : 0;
            String keywordDirective = maxTerms > 0
                    ? "请为改写后的主查询生成至多 " + maxTerms + " 个同义词/近义词/专业术语，拼入 keywordQuery 字段（原问题 + 空格 + 扩展词）。"
                    : "无需扩展，keywordQuery 直接返回原问题。";
            String prompt = PromptTemplates.render(OptimizePrompts.REWRITE_EXPANSION_TEMPLATE, Map.of(
                    "history", historyText,
                    "question", question,
                    "multi_query_directive", directive + "\n" + keywordDirective,
                    "max_terms", String.valueOf(maxTerms)));
            String resp = llmService.chat(prompt);
            return parseCombined(resp, question, cfg);
        } catch (Exception e) {
            log.warn("[优化-合并改写扩展] 调用失败，回退原问题: {}", e.getMessage());
            return CombinedQueryResult.fallback(question);
        }
    }

    private CombinedQueryResult parseCombined(String resp, String question, RagOptimizeProperties.Rewrite cfg) {
        JsonNode node = parseJsonObject(resp);
        if (node == null) {
            log.warn("[优化-合并改写扩展] LLM 响应非 JSON，回退原问题");
            return CombinedQueryResult.fallback(question);
        }
        String primary = node.path("primary").asText("").trim();
        if (primary.isEmpty()) primary = question;
        List<String> subs = cfg.isMultiQueryEnabled() ? parseSubQueries(node, cfg.getMaxSubQueries()) : List.of();
        String keywordQuery = node.path("keywordQuery").asText("").trim();
        if (keywordQuery.isEmpty()) keywordQuery = question;
        log.info("[优化-合并改写扩展] 原: {} → 主: {} 子查询: {} keywordLen: {}",
                question, primary, subs.size(), keywordQuery.length());
        return new CombinedQueryResult(primary, subs, keywordQuery);
    }

    @Override
    public RewriteResult rewrite(String question, List<ChatMessage> history) {
        if (question == null || question.isBlank()) {
            return RewriteResult.fallback(question);
        }
        RagOptimizeProperties.Rewrite cfg = properties.getRewrite();
        if (!cfg.isEnabled()) {
            return RewriteResult.fallback(question);
        }
        try {
            String historyText = cfg.isIncludeHistory() ? formatHistory(history) : "（无）";
            String directive = cfg.isMultiQueryEnabled()
                    ? "请同时生成至多 " + cfg.getMaxSubQueries() + " 个语义相关但表述不同的备选查询，用于多路召回提升召回率。"
                    : "无需生成备选查询，subQueries 字段返回空数组。";
            String prompt = PromptTemplates.render(OptimizePrompts.REWRITE_TEMPLATE, Map.of(
                    "history", historyText,
                    "question", question,
                    "multi_query_directive", directive));
            String resp = llmService.chat(prompt);
            return parse(resp, question, cfg);
        } catch (Exception e) {
            log.warn("[优化-改写] 改写失败，回退原问题: {}", e.getMessage());
            return RewriteResult.fallback(question);
        }
    }

    private RewriteResult parse(String resp, String question, RagOptimizeProperties.Rewrite cfg) {
        JsonNode node = parseJsonObject(resp);
        if (node == null) {
            log.warn("[优化-改写] LLM 响应非 JSON，回退原问题");
            return RewriteResult.fallback(question);
        }
        String primary = node.path("primary").asText("").trim();
        if (primary.isEmpty()) {
            primary = question;
        }
        List<String> subs = List.of();
        if (cfg.isMultiQueryEnabled()) {
            subs = parseSubQueries(node, cfg.getMaxSubQueries());
        }
        log.info("[优化-改写] 原: {} → 主: {} 子查询: {}", question, primary, subs.size());
        return new RewriteResult(primary, subs);
    }

    private List<String> parseSubQueries(JsonNode node, int max) {
        JsonNode arr = node.get("subQueries");
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

    /** 格式化对话历史为 "用户: ...\n助手: ..." 文本，供 LLM 做指代消解 */
    private String formatHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "（无）";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : history) {
            String role;
            String text;
            if (m instanceof UserMessage um && um.hasSingleText()) {
                role = "用户";
                text = um.singleText();
            } else if (m instanceof AiMessage am && am.text() != null) {
                role = "助手";
                text = am.text();
            } else {
                continue;
            }
            if (text != null && !text.isBlank()) {
                sb.append(role).append(": ").append(text).append('\n');
            }
        }
        return sb.length() == 0 ? "（无）" : sb.toString();
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
