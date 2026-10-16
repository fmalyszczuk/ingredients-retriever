package com.malyszczuk.ingredients_retriever.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Real email when an SMTP server is configured (set SPRING_MAIL_HOST, and the SPRING_MAIL_PORT / _USERNAME / _PASSWORD
 * it needs; any free provider or a local Mailpit/MailHog works). With none configured, the emails are written to the
 * log instead, which is only meant for development: the log then contains working verification and reset links.
 */
@Slf4j
@Configuration
public class MailConfig {

    @Bean
    @ConditionalOnProperty("spring.mail.host")
    public Mailer smtpMailer(JavaMailSender sender, @Value("${pantrypal.mail.from}") String from) {
        return (to, subject, body) -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        };
    }

    // matchIfMissing: used exactly when spring.mail.host is not set at all (no value can equal this havingValue)
    @Bean
    @ConditionalOnProperty(name = "spring.mail.host", havingValue = "\u0000-never", matchIfMissing = true)
    public Mailer loggingMailer() {
        log.warn("No SMTP server configured (spring.mail.host): emails are only written to this log. "
                + "That is for development; set SPRING_MAIL_HOST to send real ones.");
        return (to, subject, body) -> log.info("EMAIL (not sent, logged only)\nTo: {}\nSubject: {}\n\n{}", to, subject, body);
    }
}
