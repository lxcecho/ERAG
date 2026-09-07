package com.knowledge.ai.rag.optimize.eval.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.prompt.PromptTemplates;
import com.knowledge.ai.rag.optimize.config.RagOptimizeProperties;
import com.knowledge.ai.rag.optimize.dto.AnswerEvaluation;
import com.knowledge.ai.rag.optimize.eval.AnswerEvaluator;
import com.knowledge.ai.rag.optimize.prompt.OptimizePrompts;
import com.knowledge.ai.service.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 回答质量评价实现：规则版（基线）+ LLM 版（可选）。
 * <p><b>规则版</b>：基于词汇覆盖与重叠的廉价度量
 * <ul>
 *   <li>faithfulness = 回答中非停用词被上下文覆盖的比例（1=回答完全源于资料）</li>
 *   <li>relevance = 问题词与回答词的 Jaccard 相似度</li>
 *   <li>overallScore = 0.6×faithfulness + 0.4×relevance</li>
 * </ul>
 * <p><b>LLM 版</b>（默认关）：调用 LLM 评判，精度更高但 +1 LLM 调用；解析失败回退规则版。
 * <p>分词：CJK 单字 + Latin 连续字母数字（语言无关，适配中文）。
 * best-effort：开关关闭或异常返回 null，不阻断回答。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerEvaluatorImpl implements AnswerEvaluator {

    private static final Pattern TERM_PATTERN = Pattern.compile("[a-zA-Z0-9]+|[\\u4e00-\\u9fa5]");
    private static final Set<String> STOPWORDS = Set.of(
            "的", "了", "是", "在", "和", "与", "或", "也", "都", "就", "这", "那", "我", "你", "他", "它",
            "请", "对", "为", "以", "及", "等", "被", "把", "给", "向", "从", "到", "上", "下", "中",
            "the", "a", "an", "is", "are", "was", "were", "be", "to", "of", "in", "on", "for", "and", "or",
            "with", "as", "by", "at", "it", "this", "that", "i", "you", "he", "she", "we", "they");

    private final RagOptimizeProperties properties;
    private final LLMService llmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AnswerEvaluation evaluate(String question, List<RetrievalResult> results, String answer) {
        return evaluate(question, results, answer, true);
    }

    @Override
    public AnswerEvaluation evaluate(String question, List<RetrievalResult> results, String answer, boolean allowLlm) {
        if (!properties.getEval().isEnabled() || answer == null || answer.isBlank()) {
            return null;
        }
        try {
            if (allowLlm && properties.getEval().isLlmEvalEnabled()) {
                AnswerEvaluation llmEval = evaluateByLlm(question, results, answer);
                if (llmEval != null) {
                    return llmEval;
                }
                log.warn("[优化-评价] LLM 评价失败，回退规则版");
            }
            return evaluateByRule(question, results, answer);
        } catch (Exception e) {
            log.warn("[优化-评价] 评价异常: {}", e.getMessage());
            return null;
        }
    }

    /** 规则版评价 */
    AnswerEvaluation evaluateByRule(String question, List<RetrievalResult> results, String answer) {
        Set<String> contextTerms = results.stream()
                .filter(r -> r.getText() != null)
                .flatMap(r -> tokenize(r.getText()).stream())
                .collect(Collectors.toSet());
        Set<String> answerTerms = tokenize(answer);
        Set<String> questionTerms = tokenize(question == null ? "" : question);

        // faithfulness：回答非停用词被上下文覆盖比例
        Set<String> answerContentTerms = new HashSet<>(answerTerms);
        answerContentTerms.removeAll(STOPWORDS);
        double faithfulness = coverage(answerContentTerms, contextTerms);

        // relevance：问题与回答的 Jaccard（去停用词）
        Set<String> q = new HashSet<>(questionTerms);
        q.removeAll(STOPWORDS);
        Set<String> a = new HashSet<>(answerTerms);
        a.removeAll(STOPWORDS);
        double relevance = jaccard(q, a);

        double overall = 0.6 * faithfulness + 0.4 * relevance;
        String reason = String.format("规则评估：回答 %.0f%% 词汇被资料覆盖，与问题相关度 %.2f",
                faithfulness * 100, relevance);
        return new AnswerEvaluation(round(faithfulness), round(relevance), round(overall), reason, "RULE");
    }

    /** LLM 版评价：调用 LLM 评判，解析 JSON */
    private AnswerEvaluation evaluateByLlm(String question, List<RetrievalResult> results, String answer) {
        String context = results.stream()
                .filter(r -> r.getText() != null)
                .map(RetrievalResult::getText)
                .collect(Collectors.joining("\n\n"));
        String prompt = PromptTemplates.render(OptimizePrompts.EVAL_TEMPLATE, Map.of(
                "question", question == null ? "" : question,
                "context", context,
                "answer", answer));
        String resp = llmService.chat(prompt);
        JsonNode node = parseJsonObject(resp);
        if (node == null) {
            return null;
        }
        double faithfulness = node.path("faithfulness").asDouble(-1);
        double relevance = node.path("relevance").asDouble(-1);
        if (faithfulness < 0 || relevance < 0) {
            return null;
        }
        faithfulness = clamp(faithfulness);
        relevance = clamp(relevance);
        double overall = 0.6 * faithfulness + 0.4 * relevance;
        String reason = node.path("reason").asText("LLM 评估");
        return new AnswerEvaluation(round(faithfulness), round(relevance), round(overall), reason, "LLM");
    }

    /** 分词：CJK 单字 + Latin 连续字母数字（小写归一） */
    static Set<String> tokenize(String text) {
        Set<String> terms = new HashSet<>();
        if (text == null || text.isBlank()) {
            return terms;
        }
        Matcher m = TERM_PATTERN.matcher(text);
        while (m.find()) {
            terms.add(m.group().toLowerCase());
        }
        return terms;
    }

    /** 覆盖率：A 中被 B 包含的比例 */
    private static double coverage(Set<String> a, Set<String> b) {
        if (a.isEmpty()) {
            return 0.0;
        }
        int hit = 0;
        for (String s : a) {
            if (b.contains(s)) {
                hit++;
            }
        }
        return (double) hit / a.size();
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        Set<String> smaller = a.size() <= b.size() ? a : b;
        Set<String> larger = a.size() <= b.size() ? b : a;
        for (String s : smaller) {
            if (larger.contains(s)) {
                intersection++;
            }
        }
        int union = a.size() + b.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
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

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
