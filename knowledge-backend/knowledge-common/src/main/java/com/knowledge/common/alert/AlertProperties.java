package com.knowledge.common.alert;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 告警配置属性
 * <p>对应 application.yml 中 {@code alert.*} 配置项。
 * <p>策略：默认开启结构化日志告警，关闭 Webhook 推送（演示场景零外部依赖）；
 * 生产环境配置 {@code alert.webhook-enabled=true} + {@code alert.webhook-url} 后即可推送 Feishu/DingTalk。
 *
 * @author: lxcechoo@gmail.com
 */
@Data
@Component
@ConfigurationProperties(prefix = "alert")
public class AlertProperties {

    /** 告警总开关：false 时 AlertService 不记录不推送（完全静默） */
    private boolean enabled = true;

    /** Webhook 推送开关：true 且 webhookUrl 非空时异步推送 */
    private boolean webhookEnabled = false;

    /** Webhook 地址（Feishu/DingTalk 机器人兼容接口） */
    private String webhookUrl;

    /** 同源同标题告警冷却时间（秒），避免告警风暴 */
    private int cooldownSeconds = 60;

    /** 触发告警的最低级别：低于该级别不告警（INFO < WARN < CRITICAL） */
    private AlertLevel minLevel = AlertLevel.WARN;
}
