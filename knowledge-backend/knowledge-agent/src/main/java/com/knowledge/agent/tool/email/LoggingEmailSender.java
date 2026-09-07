package com.knowledge.agent.tool.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 日志模式邮件发送器：以结构化日志代替真实发送（默认模式）。
 * <p>开发/演示环境无需 SMTP 即可验证邮件工具链路；生产环境切换 {@code agent.tool.email.mode=smtp}
 * 即装配 {@link SmtpEmailSender} 替代。
 * <p>日志中不打印正文全文（仅长度），避免敏感信息泄漏到日志。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "agent.tool.email", name = "mode", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(EmailRequest request) {
        log.info("[EmailSend(LOG模式)] task={} from={} to={} cc={} subject={} 内容长度={}",
                request.getTaskId(),
                request.getFrom(),
                request.getTo(),
                request.getCc(),
                request.getSubject(),
                request.getContent() == null ? 0 : request.getContent().length());
    }
}
