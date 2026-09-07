package com.knowledge.ai.ops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 运维中心配置属性。
 * <p>对应 application.yml 中 {@code ops.*} 配置项。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Component
@ConfigurationProperties(prefix = "ops")
public class OpsProperties {

    /** 链路追踪配置 */
    private Trace trace = new Trace();

    /** 告警评估配置 */
    private Alert alert = new Alert();

    @Data
    public static class Trace {
        /** 链路追踪总开关：false 时 TraceService 不记录（避免演示环境噪声） */
        private boolean enabled = true;

        /** 采样率 [0,1]：演示环境默认全采 */
        private double sampleRate = 1.0;
    }

    @Data
    public static class Alert {
        /** 评估定时任务 cron（默认每 5 分钟） */
        private String evalCron = "0 */5 * * * ?";

        /** 月度预算阈值（元）：超阈值触发 WARN 告警 */
        private double monthlyBudget = 100;
    }
}
