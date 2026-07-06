package com.example.pvplatform.common;

import com.example.pvplatform.config.MailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProps;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        MailProperties mailProps) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.mailProps = mailProps;
    }

    /**
     * Send an email. Falls back to logging if SMTP is not configured or fails.
     */
    public void send(String to, String subject, String body) {
        if (mailSender == null) {
            log.warn("SMTP未配置(spring.mail.host为空), 不发邮件: to={}", to);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            // 发件人必须和SMTP登录用户一致(QQ邮箱强制要求)
            String from = (mailProps.from() != null && !mailProps.from().isBlank())
                ? mailProps.from() : mailProps.username();
            if (from == null || from.isBlank()) {
                log.warn("邮件发送失败: 未配置发件人(MAIL_FROM或MAIL_USERNAME)");
                return;
            }
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("邮件已发送: to={}, subject={}", to, subject);
        } catch (Exception e) {
            // 只打异常消息，不打印堆栈和邮件正文（防止泄露密钥和验证码）
            log.warn("邮件发送失败: to={}, error={}", to, e.getMessage());
        }
    }
}
