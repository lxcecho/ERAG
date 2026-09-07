package com.knowledge.kb.governance.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SimHash 指纹计算器（文档近似去重核心算法）。
 * <p>原理：将文本分词后，每个特征哈希为 64 位，按位加权投票得到一份 64 位指纹；
 * 两文档指纹的 Hamming 距离越小则越相似（经验阈值 ≤3 视为近似重复）。
 * <p>相较于纯 MD5 只能判完全一致，SimHash 对局部修改 / 排版差异具备鲁棒性，
 * 适用于「同一份文档多次上传 / 微调后重传」的近似检测场景。
 * <p>分词策略：CJK 字符按单字切分（中文无空格，字级特征对 SimHash 足够），
 * Latin 数字字母按连续词切分；标点空白作为分隔符忽略。
 *
 * @author: lxcechoo@gmail.com
 */
@Component
public class SimHashCalculator {

    /** FNV-1a 64 位哈希初始偏移量 */
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    /** FNV-1a 64 位哈希素数 */
    private static final long FNV_PRIME = 0x100000001b3L;

    /**
     * 计算文本的 64 位 SimHash 指纹。
     *
     * @param text 纯文本内容（解析后）
     * @return 64 位指纹；空文本返回 0
     */
    public long simHash(String text) {
        if (text == null || text.isBlank()) {
            return 0L;
        }
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return 0L;
        }
        // 每个比特位的累加权重：命中 +1，未命中 -1
        int[] bits = new int[64];
        for (String token : tokens) {
            long hash = fnv1a64(token);
            for (int i = 0; i < 64; i++) {
                if (((hash >> i) & 1L) == 1L) {
                    bits[i]++;
                } else {
                    bits[i]--;
                }
            }
        }
        long fingerprint = 0L;
        for (int i = 0; i < 64; i++) {
            if (bits[i] > 0) {
                fingerprint |= (1L << i);
            }
        }
        return fingerprint;
    }

    /**
     * 计算两个指纹的 Hamming 距离（不同比特位数）。
     */
    public int hammingDistance(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    /**
     * 由 Hamming 距离估算相似度 0~1：similarity = 1 - distance / 64。
     */
    public double similarity(long a, long b) {
        return 1.0 - hammingDistance(a, b) / 64.0;
    }

    /**
     * 分词：CJK 单字 + Latin 连续字母数字词（小写归一），标点空白忽略。
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder latin = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                flushLatin(tokens, latin);
                tokens.add(String.valueOf(c));
            } else if (Character.isLetterOrDigit(c)) {
                latin.append(c);
            } else {
                flushLatin(tokens, latin);
            }
        }
        flushLatin(tokens, latin);
        return tokens;
    }

    private void flushLatin(List<String> tokens, StringBuilder latin) {
        if (latin.length() > 0) {
            tokens.add(latin.toString().toLowerCase());
            latin.setLength(0);
        }
    }

    /** 是否为 CJK 统一表意文字（含扩展 A 区常用范围） */
    private boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)   // CJK 统一表意文字
                || (c >= 0x3400 && c <= 0x4DBF) // CJK 扩展 A
                || (c >= 0xF900 && c <= 0xFAFF); // CJK 兼容表意文字
    }

    /** FNV-1a 64 位哈希：轻量快速、分布均匀，适合 SimHash 特征哈希 */
    private long fnv1a64(String s) {
        long hash = FNV_OFFSET;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
