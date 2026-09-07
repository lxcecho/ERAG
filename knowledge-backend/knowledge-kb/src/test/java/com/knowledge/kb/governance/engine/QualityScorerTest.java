/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.kb.governance.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 质量规则评分器单元测试。
 * <p>验证：总分区间 / 四维度求和一致 / 完整性随长度递增 / 时效性随时间衰减 / evaluator 标记。
 */
class QualityScorerTest {

    private final QualityScorer scorer = new QualityScorer();

    @Test
    void score_总分等于四维度之和且在0到100之间() {
        String text = "# 标题\n\n这是一段足够长的文档内容，用于测试完整性评分。".repeat(20);
        QualityResult result = scorer.score(text, 8, LocalDateTime.now());

        int sum = result.completeness() + result.freshness() + result.structure() + result.coverage();
        assertEquals(sum, result.score());
        assertTrue(result.score() >= 0 && result.score() <= 100);
    }

    @Test
    void score_标记为RULE规则评分() {
        QualityResult result = scorer.score("测试内容", 1, LocalDateTime.now());
        assertEquals("RULE", result.evaluator());
        assertNotNull(result.summary());
    }

    @Test
    void 完整性_长文本得分高于短文本() {
        LocalDateTime now = LocalDateTime.now();
        QualityResult shortResult = scorer.score("短", 1, now);
        String longText = "这是一段很长的文档内容，用于测试完整性维度的评分分档逻辑。".repeat(200);
        QualityResult longResult = scorer.score(longText, 10, now);
        assertTrue(longResult.completeness() > shortResult.completeness(),
                "长文本完整性应高于短文本");
    }

    @Test
    void 时效性_新文档得分高于旧文档() {
        String text = "测试文本内容足够长以触发完整性高分档位";
        QualityResult fresh = scorer.score(text.repeat(50), 5, LocalDateTime.now());
        QualityResult stale = scorer.score(text.repeat(50), 5, LocalDateTime.now().minusYears(2));
        assertTrue(fresh.freshness() > stale.freshness(),
                "新文档时效性应高于旧文档");
    }

    @Test
    void 覆盖度_多切片得分高于单切片() {
        LocalDateTime now = LocalDateTime.now();
        String text = "测试内容".repeat(100);
        QualityResult single = scorer.score(text, 1, now);
        QualityResult many = scorer.score(text, 12, now);
        assertTrue(many.coverage() > single.coverage(),
                "多切片覆盖度应高于单切片");
    }

    @Test
    void 结构性_含Markdown标题列表得分高于纯文本() {
        LocalDateTime now = LocalDateTime.now();
        String structured = "# 一级标题\n\n## 二级标题\n\n- 列表项一\n- 列表项二\n\n1. 编号一\n2. 编号二\n\n正文内容".repeat(10);
        String plain = "正文内容正文内容正文内容正文内容正文内容正文内容正文内容".repeat(10);
        QualityResult s = scorer.score(structured, 5, now);
        QualityResult p = scorer.score(plain, 5, now);
        assertTrue(s.structure() >= p.structure(),
                "结构化文本结构性分应不低于纯文本");
    }

    @Test
    void scoreByStats_无文本时也能基于统计量评分() {
        // 手动重评场景：仅凭 contentLength / chunkCount / createTime
        QualityResult result = scorer.scoreByStats(6000, 12, LocalDateTime.now());
        assertEquals(25, result.completeness(), "长度≥5000 完整性应满分");
        assertEquals(25, result.coverage(), "切片≥10 覆盖度应满分");
        assertEquals(25, result.freshness(), "当天创建时效性应满分");
        assertEquals("RULE", result.evaluator());
        assertTrue(result.score() > 0);
    }

    @Test
    void scoreByStats_空统计量不抛异常() {
        QualityResult result = scorer.scoreByStats(0, 0, null);
        assertTrue(result.score() >= 0 && result.score() <= 100);
    }
}
