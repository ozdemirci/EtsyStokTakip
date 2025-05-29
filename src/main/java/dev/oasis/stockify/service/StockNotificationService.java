package dev.oasis.stockify.service;

import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.model.StockNotification;
import dev.oasis.stockify.repository.StockNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StockNotificationService {
    private final StockNotificationRepository notificationRepository;
    private final EmailService emailService;

    public StockNotificationService(StockNotificationRepository notificationRepository, EmailService emailService) {
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
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

            // Send email notification
            emailService.sendLowStockNotification(product);
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
