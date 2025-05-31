package dev.oasis.stockify.config;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.mail.MailHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailHealthConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
    public JavaMailSender mailSender() {
        return new JavaMailSenderImpl();
    }

    @Bean
    @ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
    public HealthIndicator mailHealthIndicator(JavaMailSender mailSender) {
        return new MailHealthIndicator((JavaMailSenderImpl) mailSender);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "notification.email.enabled", havingValue = "false", matchIfMissing = true)
    public JavaMailSender dummyMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        // Dummy configuration that won't try to connect
        mailSender.setHost("dummy");
        mailSender.setPort(0);
        return mailSender;
    }
}
