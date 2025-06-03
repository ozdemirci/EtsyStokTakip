package dev.oasis.stockify.service;

import dev.oasis.stockify.dto.DashboardMetricsDTO;
import dev.oasis.stockify.dto.DashboardStats;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.repository.ProductRepository;
import dev.oasis.stockify.repository.StockNotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final ProductRepository productRepository;
    private final AppUserRepository userRepository;
    private final StockNotificationRepository notificationRepository;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void initMetrics() {
        // Initialize metrics with default values
        meterRegistry.gauge("sales.monthly", 0.0);
        meterRegistry.gauge("sales.daily", 0.0);
    }

    public DashboardMetricsDTO getDashboardMetrics() {
        return DashboardMetricsDTO.builder()
                .totalProducts(productRepository.count())
                .totalUsers(userRepository.count())
                .totalInventoryValue(calculateTotalInventoryValue())
                .lowStockProducts(countLowStockProducts())
                .activeNotifications(notificationRepository.count())
                .monthlyRevenue(getMonthlyRevenue())
                .dailyRevenue(getDailyRevenue())
                .build();
    }

    private double calculateTotalInventoryValue() {
        return productRepository.findAll().stream()
                .map(product -> product.getPrice().multiply(BigDecimal.valueOf(product.getStockLevel())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .doubleValue();
    }

    private long countLowStockProducts() {
        return productRepository.findAll().stream()
                .filter(product -> product.getStockLevel() < product.getLowStockThreshold())
                .count();
    }

    private double getMonthlyRevenue() {
        try {
            return meterRegistry.get("sales.monthly").gauge().value();
        } catch (Exception e) {
            return 0.0; // Fallback value if metric is not found
        }
    }

    private double getDailyRevenue() {
        try {
            return meterRegistry.get("sales.daily").gauge().value();
        } catch (Exception e) {
            return 0.0; // Fallback value if metric is not found
        }
    }

    // Helper method to update revenue metrics (can be called from other services)
    public void updateRevenue(double dailyAmount, double monthlyAmount) {
        meterRegistry.gauge("sales.daily", dailyAmount);
        meterRegistry.gauge("sales.monthly", monthlyAmount);
    }

    public DashboardStats getDashboardStats() {
        List<Product> products = productRepository.findAll();

        long totalProducts = products.size();
        int totalStock = products.stream()
                .mapToInt(Product::getStockLevel)
                .sum();

        long lowStockCount = products.stream()
                .filter(Product::isLowStock)
                .count();

        return new DashboardStats(totalProducts, totalStock, lowStockCount);
    }
}
