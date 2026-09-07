package com.knowledge.kb.governance.engine;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 文档质量规则评分器（4 维度 × 25 分 = 100 分）。
 * <p>无需调用 LLM，基于文本统计与文档元数据快速评估，适合入库时批量自动评分。
 * <ul>
 *   <li>完整性(25)：按解析后纯文本长度分档，内容越充实分越高。</li>
 *   <li>时效性(25)：按文档创建时间距今天数衰减，越新分越高。</li>
 *   <li>结构性(25)：检测标题/列表/分段等结构标记占比，结构化文档分更高。</li>
 *   <li>覆盖度(25)：按切片数量分档，覆盖主题越广分越高。</li>
 * </ul>
 * <p>提供两个入口：
 * <ul>
 *   <li>{@link #score}：入库时由完整文本评分（结构维度可精确统计标记）。</li>
 *   <li>{@link #scoreByStats}：手动重评时仅凭已存储统计量评分（结构维度用长度代理估计），
 *       避免重新解析文件——主要刷新时效性分。</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
@Component
public class QualityScorer {

    private static final int FULL = 25;

    /**
     * 入库评分：基于完整文本（结构维度精确统计标记）。
     */
    public QualityResult score(String text, int chunkCount, LocalDateTime createTime) {
        int length = text == null ? 0 : text.length();
        int completeness = scoreCompleteness(length);
        int freshness = scoreFreshness(createTime);
        int structure = scoreStructure(text);
        int coverage = scoreCoverage(chunkCount);
        return build(completeness, freshness, structure, coverage);
    }

    /**
     * 手动重评：基于已存储统计量（结构维度用长度代理，主要刷新时效性）。
     *
     * @param contentLength 解析后纯文本长度（存于 document_fingerprint）
     * @param chunkCount    切片数量
     * @param createTime    文档创建时间
     */
    public QualityResult scoreByStats(int contentLength, int chunkCount, LocalDateTime createTime) {
        int completeness = scoreCompleteness(contentLength);
        int freshness = scoreFreshness(createTime);
        // 无完整文本时，按长度代理估计结构分（长文档通常含更多结构标记）
        int structure = scoreStructureByLength(contentLength);
        int coverage = scoreCoverage(chunkCount);
        return build(completeness, freshness, structure, coverage);
    }

    private QualityResult build(int completeness, int freshness, int structure, int coverage) {
        int total = completeness + freshness + structure + coverage;
        String summary = String.format(
                "完整性%d + 时效性%d + 结构性%d + 覆盖度%d = %d（规则评分）",
                completeness, freshness, structure, coverage, total);
        return new QualityResult(total, completeness, freshness, structure, coverage, summary, "RULE");
    }

    /** 完整性：按纯文本长度分档 */
    private int scoreCompleteness(int length) {
        if (length >= 5000) return FULL;
        if (length >= 2000) return 20;
        if (length >= 500) return 15;
        if (length >= 100) return 8;
        return 3;
    }

    /** 时效性：按创建时间距今天数衰减 */
    private int scoreFreshness(LocalDateTime createTime) {
        if (createTime == null) return 10;
        long days = ChronoUnit.DAYS.between(createTime.toLocalDate(), LocalDate.now());
        if (days <= 30) return FULL;
        if (days <= 90) return 20;
        if (days <= 180) return 15;
        if (days <= 365) return 10;
        return 5;
    }

    /** 结构性：统计结构标记（换行比例 / Markdown 标题 / 列表项 / 编号） */
    private int scoreStructure(String text) {
        if (text == null || text.isBlank()) return 0;
        int length = text.length();
        long newlines = text.chars().filter(c -> c == '\n').count();
        long headings = countMatches(text, "\n#") + countMatches(text, "# ");
        long bullets = countMatches(text, "\n- ") + countMatches(text, "\n* ") + countMatches(text, "\n• ");
        long numbered = countMatches(text, "\n1.") + countMatches(text, "\n2.") + countMatches(text, "\n3.");

        double newlineRatio = (double) newlines / length;
        int score = 0;
        if (newlineRatio >= 0.01 && newlineRatio <= 0.08) score += 8;
        else if (newlineRatio > 0.08) score += 5;
        if (headings > 0) score += 7;
        if (bullets > 0 || numbered > 0) score += 7;
        if (countMatches(text, "\n\n") > 0) score += 3;
        return Math.min(score, FULL);
    }

    /** 结构性代理估计：无完整文本时按长度分档（长文档结构化概率更高） */
    private int scoreStructureByLength(int length) {
        if (length >= 5000) return 18;
        if (length >= 2000) return 14;
        if (length >= 500) return 10;
        if (length >= 100) return 6;
        return 3;
    }

    /** 覆盖度：按切片数量分档 */
    private int scoreCoverage(int chunkCount) {
        if (chunkCount >= 10) return FULL;
        if (chunkCount >= 5) return 20;
        if (chunkCount >= 2) return 15;
        if (chunkCount == 1) return 8;
        return 0;
    }

    private int countMatches(String text, String sub) {
        if (text == null || sub == null || sub.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
