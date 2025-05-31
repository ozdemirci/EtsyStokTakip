package dev.oasis.stockify.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import dev.oasis.stockify.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true", matchIfMissing = false)
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${notification.email.to:noreply@localhost}")
    private String toEmail;

    @Value("${notification.email.from:noreply@localhost}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        logger.info("EmailService initialized with receiver: {} and sender: {}", toEmail, fromEmail);
    }

    public void sendLowStockNotification(Product product) {
        try {
            Context context = new Context();
            context.setVariable("product", product);

            String emailContent = templateEngine.process("email/low-stock-notification", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Düşük Stok Uyarısı: " + product.getTitle());
            helper.setText(emailContent, true);

            mailSender.send(message);
            logger.info("Low stock notification email sent for product: {}", product.getTitle());
        } catch (MessagingException e) {
            logger.error("Failed to send email for product: {}", product.getTitle(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }
}
