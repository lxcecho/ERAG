package com.knowledge.agent.tool.email;

/**
 * 邮件发送器接口：解耦 EmailTool 与具体发送实现（log / smtp）。
 * <p>由 {@link LoggingEmailSender}（log 模式，默认）与 {@link SmtpEmailSender}（smtp 模式）实现，
 * 通过 {@code @ConditionalOnProperty(agent.tool.email.mode)} 按配置装配单一实现注入 EmailTool。
 *
 * @author: lxcechoo@gmail.com
 */
public interface EmailSender {

    /**
     * 发送邮件。
     *
     * @param request 邮件请求（已校验非空）
     */
    void send(EmailRequest request);
}
