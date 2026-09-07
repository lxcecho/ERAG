/**
 * @author: lxcechoo@gmail.com
 */

package com.knowledge.kb.governance.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SimHash 指纹算法单元测试。
 * <p>验证：确定性 / 区分性 / 近似重复鲁棒性（小幅修改 Hamming 距离小）/ 空文本兜底。
 */
class SimHashCalculatorTest {

    private final SimHashCalculator calculator = new SimHashCalculator();

    @Test
    void simHash_空文本返回0() {
        assertEquals(0L, calculator.simHash(null));
        assertEquals(0L, calculator.simHash(""));
        assertEquals(0L, calculator.simHash("   "));
    }

    @Test
    void simHash_相同文本指纹一致() {
        String text = "企业级 AI 知识库助手 RAG 检索增强生成方案";
        assertEquals(calculator.simHash(text), calculator.simHash(text));
    }

    @Test
    void simHash_不同文本指纹不同() {
        long a = calculator.simHash("知识库检索增强生成技术方案设计");
        long b = calculator.simHash("今天天气真好适合出门散步看电影");
        assertNotEquals(a, b, "完全不同的文本应有不同指纹");
    }

    @Test
    void hammingDistance_相同指纹距离为0() {
        long fp = calculator.simHash("测试文本内容");
        assertEquals(0, calculator.hammingDistance(fp, fp));
    }

    @Test
    void similarity_相同指纹相似度为1() {
        long fp = calculator.simHash("测试文本内容");
        assertEquals(1.0, calculator.similarity(fp, fp), 0.000001);
    }

    @Test
    void 近似重复_小幅修改Hamming距离应较小() {
        // 两段文本仅结尾不同，SimHash 应判定为近似重复（距离 ≤ 阈值 3）
        String base = "企业级 AI 知识库助手，基于 Spring Boot 3 与 LangChain4j 构建，"
                + "提供文档解析、切片、向量化、混合检索与多轮对话能力。";
        String tweaked = base + "新增过期文档自动驳回。";
        long fpA = calculator.simHash(base);
        long fpB = calculator.simHash(tweaked);
        int distance = calculator.hammingDistance(fpA, fpB);
        assertTrue(distance <= 3, "近似文本 Hamming 距离应 ≤3，实际=" + distance);
    }

    @Test
    void 完全不同文本Hamming距离应较大() {
        long a = calculator.simHash("知识库 RAG 检索增强生成 向量数据库 Milvus Embedding 切片");
        long b = calculator.simHash("苹果香蕉橘子葡萄西瓜芒果菠萝草莓蓝莓柠檬樱桃火龙果");
        int distance = calculator.hammingDistance(a, b);
        assertTrue(distance > 3, "无关文本 Hamming 距离应 >3，实际=" + distance);
    }

    @Test
    void similarity_范围始终在0到1之间() {
        long a = calculator.simHash("文本一");
        long b = calculator.simHash("完全不同的另一段文本内容用于测试相似度边界");
        double sim = calculator.similarity(a, b);
        assertTrue(sim >= 0.0 && sim <= 1.0, "相似度应在 [0,1]，实际=" + sim);
    }

    @Test
    void simHash_支持中英文混合分词() {
        // 不抛异常即视为通过：CJK 单字 + Latin 词混合
        long fp = calculator.simHash("RAG 是 Retrieval Augmented Generation 的缩写，中文叫检索增强生成。");
        assertNotEquals(0L, fp);
    }
}
