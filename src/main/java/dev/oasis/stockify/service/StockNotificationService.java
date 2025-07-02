package dev.oasis.stockify.service;

import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.model.StockNotification;
import dev.oasis.stockify.repository.StockNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockNotificationService {
    
    private final StockNotificationRepository notificationRepository;
    private final Optional<EmailService> emailService;

   

    @Transactional
    public void checkAndCreateLowStockNotification(Product product) {
        if (product.isLowStock()) {
            boolean exists = notificationRepository.existsByProductAndReadFalse(product);
            if (exists) {
                log.debug("Low stock notification already exists for product: {}", product.getTitle());
                return;
            }

            StockNotification notification = new StockNotification();
            notification.setProduct(product);
            notification.setMessage(String.format("'%s' ürününün stok seviyesi düşük! Mevcut stok: %d, Eşik: %d",
                    product.getTitle(), product.getStockLevel(), product.getLowStockThreshold()));
            notification.setRead(false);
            notificationRepository.save(notification);
            log.info("Created low stock notification for product: {}", product.getTitle());

            // Send email notification if service is available
            emailService.ifPresent(service -> {
                try {
                    service.sendLowStockNotification(product);
                } catch (Exception e) {
                    log.error("Failed to send email notification", e);
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

    @Transactional
    public int markAllAsRead() {
        int count = notificationRepository.markAllAsRead();
        log.info("Marked {} notifications as read", count);
        return count;
    }

    @Transactional
    public int deleteAllRead() {
        int count = notificationRepository.deleteAllRead();
        log.info("Deleted {} read notifications", count);
        return count;
    }

    @Transactional
    public boolean deleteNotification(Long notificationId) {
        try {
            notificationRepository.deleteById(notificationId);
            log.info("Deleted notification with ID: {}", notificationId);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete notification with ID: {}", notificationId, e);
            return false;
        }
    }
}
