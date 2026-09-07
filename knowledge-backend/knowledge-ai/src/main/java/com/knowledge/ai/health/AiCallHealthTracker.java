package com.knowledge.ai.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI/外部服务调用健康追踪器
 * <p>以「资源」为维度（如 "llm" / "milvus" / "embedding"），用滑动窗口统计成功/失败次数，
 * 供各 {@link org.springframework.boot.actuate.health.HealthIndicator} 读取错误率判定组件健康状态。
 * <p>
 * 设计要点：
 * <ul>
 *   <li><b>不主动探测</b>：仅在业务调用成功/失败时被动上报，避免健康检查主动烧 token 或打外部服务。</li>
 *   <li><b>滑动窗口</b>：每个资源维护 1 分钟窗口，窗口过期自动重置（lazy rollover，读取或写入时检查）。</li>
 *   <li><b>低开销</b>：仅 AtomicLong 计数 + 时间戳，无锁读路径。</li>
 *   <li><b>最小样本</b>：总调用数 < {@link #MIN_SAMPLE} 时不判定为不健康（避免冷启动误报）。</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
public class AiCallHealthTracker {

    /** 窗口时长（毫秒）：1 分钟 */
    private static final long WINDOW_MS = 60_000L;

    /** 最小样本数：低于此值不判定不健康（避免冷启动或低流量误报） */
    private static final int MIN_SAMPLE = 5;

    /** 不健康错误率阈值：窗口内错误率 >= 此值视为 DEGRADED */
    private static final double DEGRADED_ERROR_RATE = 0.5;

    private final ConcurrentHashMap<String, StatWindow> windows = new ConcurrentHashMap<>();

    /** 上报成功一次 */
    public void recordSuccess(String resource) {
        windowOf(resource).recordSuccess();
    }

    /** 上报失败一次 */
    public void recordFailure(String resource) {
        windowOf(resource).recordFailure();
    }

    /**
     * 获取资源健康统计（触发窗口滚动）。
     *
     * @param resource 资源名（如 "llm" / "milvus"）
     * @return 统计快照
     */
    public HealthStats getStats(String resource) {
        return windowOf(resource).snapshot();
    }

    private StatWindow windowOf(String resource) {
        return windows.computeIfAbsent(resource, k -> new StatWindow());
    }

    /** 单资源的滑动窗口统计 */
    private static final class StatWindow {
        private final AtomicLong success = new AtomicLong();
        private final AtomicLong failure = new AtomicLong();
        private volatile long windowStart = System.currentTimeMillis();

        void recordSuccess() {
            rolloverIfNeeded();
            success.incrementAndGet();
        }

        void recordFailure() {
            rolloverIfNeeded();
            failure.incrementAndGet();
        }

        HealthStats snapshot() {
            rolloverIfNeeded();
            long s = success.get();
            long f = failure.get();
            long total = s + f;
            double errorRate = total == 0 ? 0.0 : (double) f / total;
            boolean healthy = total < MIN_SAMPLE || errorRate < DEGRADED_ERROR_RATE;
            return new HealthStats(s, f, total, errorRate, healthy);
        }

        private void rolloverIfNeeded() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= WINDOW_MS) {
                // 滚动窗口：重置计数与起始时间（多线程并发重置无副作用，最多多滚一次）
                success.set(0);
                failure.set(0);
                windowStart = now;
            }
        }
    }

    /**
     * 健康统计快照（不可变值对象）。
     *
     * @param success    窗口内成功次数
     * @param failure    窗口内失败次数
     * @param total      总调用次数
     * @param errorRate  错误率 [0,1]
     * @param healthy    是否健康（综合最小样本 + 错误率判定）
     */
    public record HealthStats(long success, long failure, long total, double errorRate, boolean healthy) {
    }
}
