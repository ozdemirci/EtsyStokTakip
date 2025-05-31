package dev.oasis.stockify.service;

import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.model.StockNotification;
import dev.oasis.stockify.repository.StockNotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Optional;

@Service
public class StockNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(StockNotificationService.class);
    private final StockNotificationRepository notificationRepository;
    private final Optional<EmailService> emailService;

    public StockNotificationService(StockNotificationRepository notificationRepository,
                                  @Autowired(required = false) EmailService emailService) {
        this.notificationRepository = notificationRepository;
        this.emailService = Optional.ofNullable(emailService);
        logger.info("StockNotificationService initialized. Email service enabled: {}", this.emailService.isPresent());
    }

    @Transactional
    public void checkAndCreateLowStockNotification(Product product) {
        if (product.isLowStock()) {
            StockNotification notification = new StockNotification();
            notification.setProduct(product);
            notification.setMessage(String.format("'%s' ürününün stok seviyesi düşük! Mevcut stok: %d, Eşik: %d",
                    product.getTitle(), product.getStockLevel(), product.getLowStockThreshold()));
            notification.setRead(false);
            notificationRepository.save(notification);
            logger.info("Created low stock notification for product: {}", product.getTitle());

            // Send email notification if service is available
            emailService.ifPresent(service -> {
                try {
                    service.sendLowStockNotification(product);
                } catch (Exception e) {
                    logger.error("Failed to send email notification", e);
                }
            });
        }
    }

    public List<StockNotification> getUnreadNotifications() {
        return notificationRepository.findByReadFalseOrderByCreatedAtDesc();
    }

    public List<StockNotification> getAllNotifications() {
        return notificationRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
        });
    }
}
