package dev.oasis.stockify.repository;

import dev.oasis.stockify.model.StockNotification;
import dev.oasis.stockify.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import org.springframework.lang.NonNull;

public interface StockNotificationRepository extends JpaRepository<StockNotification, Long> {
    
    List<StockNotification> findByReadFalseOrderByCreatedAtDesc();
    List<StockNotification> findAllByOrderByCreatedAtDesc();
    
    @NonNull
    List<StockNotification> findAll();

    boolean existsByProductAndReadFalse(Product product);
    
    @Modifying
    @Query("UPDATE StockNotification n SET n.read = true, n.readAt = CURRENT_TIMESTAMP WHERE n.read = false")
    int markAllAsRead();
    
    @Modifying
    @Query("DELETE FROM StockNotification n WHERE n.read = true")
    int deleteAllRead();
    
    @Query("SELECT COUNT(n) FROM StockNotification n WHERE n.priority = 'HIGH' OR n.notificationType = 'OUT_OF_STOCK'")
    long countCriticalNotifications();
}
