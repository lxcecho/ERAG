package com.knowledge.ai.rag.optimize.compress;

import com.knowledge.ai.dto.RetrievalResult;
import com.knowledge.ai.rag.optimize.config.RagOptimizeProperties;
import com.knowledge.ai.service.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 上下文压缩器：对检索后的候选切片做去重 + 预算截断，避免重复/冗长内容挤占 LLM 上下文窗口。
 * <p>三步流水线（均保留高排名切片）：
 * <ol>
 *   <li><b>去重</b>：按字符 bigram 计算 Jaccard 相似度，≥阈值则剔除低排名近似重复切片
 *      （语言无关，适配中文无空格场景）。</li>
 *   <li><b>截断</b>：按 ranked 顺序累加文本长度，超过 maxContextChars 时停止，保留前缀。</li>
 *   <li><b>LLM 抽取压缩</b>（可选，默认关）：对每个存活切片调用 LLM 抽取与问题相关的句子，
 *       保留原 RetrievalResult 槽位与来源（不破坏引用结构）。N 次 LLM 调用，成本重故默认关。</li>
 * </ol>
 * <p>开关关闭或异常时原样返回（零影响降级）。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextCompressor {

    private final RagOptimizeProperties properties;
    private final LLMService llmService;

    /**
     * 压缩上下文。
     *
     * @param results 检索结果（按相关性降序）
     * @param question 原始问题（LLM 抽取压缩时用于聚焦相关句子）
     * @return 压缩后结果
     */
    public List<RetrievalResult> compress(List<RetrievalResult> results, String question) {
        if (results == null || results.isEmpty()) {
            return results == null ? List.of() : results;
        }
        if (!properties.getCompress().isEnabled()) {
            return results;
        }
        List<RetrievalResult> deduped = dedup(results);
        List<RetrievalResult> truncated = truncate(deduped, properties.getCompress().getMaxContextChars());
        if (properties.getCompress().isLlmCompressEnabled()) {
            truncated = llmExtract(truncated, question);
        }
        if (truncated.size() < results.size()) {
            log.info("[优化-压缩] 候选 {} → 压缩后 {}", results.size(), truncated.size());
        }
        return truncated;
    }

    /** 去重：Jaccard 字符 bigram 相似度 ≥ 阈值则剔除低排名项 */
    List<RetrievalResult> dedup(List<RetrievalResult> results) {
        double threshold = properties.getCompress().getDedupThreshold();
        List<RetrievalResult> kept = new ArrayList<>(results.size());
        List<Set<String>> keptBigrams = new ArrayList<>();
        for (RetrievalResult r : results) {
            Set<String> bigrams = charBigrams(r.getText());
            boolean duplicate = false;
            for (Set<String> existing : keptBigrams) {
                if (jaccard(bigrams, existing) >= threshold) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                kept.add(r);
                keptBigrams.add(bigrams);
            }
        }
        return kept;
    }

    /** 截断：按 ranked 顺序累加字符数，超预算则停止 */
    List<RetrievalResult> truncate(List<RetrievalResult> results, int maxChars) {
        List<RetrievalResult> out = new ArrayList<>();
        int used = 0;
        for (RetrievalResult r : results) {
            int len = r.getText() == null ? 0 : r.getText().length();
            if (used + len > maxChars && !out.isEmpty()) {
                break;
            }
            out.add(r);
            used += len;
        }
        return out;
    }

    /** LLM 抽取压缩：逐切片抽取与问题相关句子，保留槽位与来源（best-effort） */
    private List<RetrievalResult> llmExtract(List<RetrievalResult> results, String question) {
        List<RetrievalResult> out = new ArrayList<>(results.size());
        for (RetrievalResult r : results) {
            try {
                String extracted = llmService.chat(extractPrompt(question, r.getText()));
                out.add(new RetrievalResult(
                        (extracted == null || extracted.isBlank()) ? r.getText() : extracted,
                        r.getScore(), r.getSource(), r.getDocumentId(),
                        r.getChunkIndex(), r.getChunkId(), r.getScoreType()));
            } catch (Exception e) {
                log.warn("[优化-压缩] LLM 抽取失败，保留原切片: {}", e.getMessage());
                out.add(r);
            }
        }
        return out;
    }

    private String extractPrompt(String question, String text) {
        return "请从下方文本中只抽取与问题直接相关的句子，保持原文表述不变，不要添加任何解释或前缀。\n"
                + "若全文相关则原样返回；若无相关内容返回空。\n\n"
                + "问题：" + question + "\n\n文本：" + (text == null ? "" : text);
    }

    /** 字符 bigram 集合（连续两字符），文本过短时退化为单字符集 */
    static Set<String> charBigrams(String text) {
        Set<String> set = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return set;
        }
        if (text.length() < 2) {
            set.add(text);
            return set;
        }
        for (int i = 0; i < text.length() - 1; i++) {
            set.add(text.substring(i, i + 2));
        }
        return set;
    }

    /** Jaccard 相似度 |A∩B| / |A∪B| */
    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
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
}
