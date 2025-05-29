package dev.oasis.stockify.repository;

import dev.oasis.stockify.model.StockNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StockNotificationRepository extends JpaRepository<StockNotification, Long> {
    List<StockNotification> findByReadFalseOrderByCreatedAtDesc();
    List<StockNotification> findAllByOrderByCreatedAtDesc();
}
