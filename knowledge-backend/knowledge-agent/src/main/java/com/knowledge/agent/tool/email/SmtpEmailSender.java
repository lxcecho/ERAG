package com.knowledge.agent.tool.email;

import com.knowledge.agent.config.AgentProperties;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * SMTP 模式邮件发送器：通过 JavaMail 真实发送。
 * <p>从 {@code agent.tool.email.*} 自建 {@link JavaMailSenderImpl}（不依赖 spring.mail.* 自动配置），
 * 仅当 {@code agent.tool.email.mode=smtp} 时装配。SMTP 参数缺失时启动期告警，发送时抛异常由 EmailTool 转失败结果。
 *
 * @author: lxcechoo@gmail.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent.tool.email", name = "mode", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private final AgentProperties props;
    private JavaMailSenderImpl mailSender;

    @PostConstruct
    public void init() {
        AgentProperties.Email c = props.getTool().getEmail();
        if (c.getSmtpHost() == null || c.getSmtpHost().isBlank()) {
            log.warn("[EmailSend(SMTP模式)] smtp-host 未配置，发送将失败；请配置 agent.tool.email.smtp-host 或切换 mode=log");
            return;
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(c.getSmtpHost());
        sender.setPort(c.getSmtpPort());
        sender.setUsername(c.getSmtpUsername());
        sender.setPassword(c.getSmtpPassword());
        sender.setDefaultEncoding("UTF-8");
        Properties javaMailProps = new Properties();
        // 465(SSL) / 587(STARTTLS) 常见端口按需开启 SSL/STARTTLS
        if (c.getSmtpPort() == 465) {
            javaMailProps.put("mail.smtp.ssl.enable", "true");
        } else if (c.getSmtpPort() == 587) {
            javaMailProps.put("mail.smtp.starttls.enable", "true");
        }
        javaMailProps.put("mail.smtp.auth", "true");
        javaMailProps.put("mail.smtp.timeout", "5000");
        javaMailProps.put("mail.smtp.connectiontimeout", "5000");
        javaMailProps.put("mail.smtp.writetimeout", "5000");
        sender.setJavaMailProperties(javaMailProps);
        this.mailSender = sender;
        log.info("[EmailSend(SMTP模式)] 初始化完成 host={} port={}", c.getSmtpHost(), c.getSmtpPort());
    }

    @Override
    public void send(EmailRequest request) {
        if (mailSender == null) {
            throw new IllegalStateException("SMTP 未配置（smtp-host 为空），无法发送邮件");
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom(request.getFrom());
            helper.setTo(request.getTo().toArray(new String[0]));
            if (request.getCc() != null && !request.getCc().isEmpty()) {
                helper.setCc(request.getCc().toArray(new String[0]));
            }
            helper.setSubject(request.getSubject());
            helper.setText(request.getContent(), false);
            mailSender.send(mime);
            log.info("[EmailSend(SMTP模式)] 发送成功 task={} to={} subject={}",
                    request.getTaskId(), request.getTo(), request.getSubject());
        } catch (Exception e) {
            log.error("[EmailSend(SMTP模式)] 发送失败 task={} to={}: {}",
                    request.getTaskId(), request.getTo(), e.getMessage());
            throw new IllegalStateException("邮件发送失败: " + e.getMessage(), e);
        }
    }
}
