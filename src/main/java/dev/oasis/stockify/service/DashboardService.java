package dev.oasis.stockify.service;

import dev.oasis.stockify.config.tenant.TenantContext;
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
        // Get tenant-specific counts
        long tenantUserCount = getTenantUserCount();
        
        return DashboardMetricsDTO.builder()
                .totalProducts(productRepository.count())
                .totalUsers(tenantUserCount)
                .totalInventoryValue(calculateTotalInventoryValue())
                .lowStockProducts(countLowStockProducts())
                .activeNotifications(notificationRepository.count())
                .monthlyRevenue(getMonthlyRevenue())
                .dailyRevenue(getDailyRevenue())
                .build();
    }

    private long getTenantUserCount() {
        // Count users in current tenant context
        String currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant == null || currentTenant.isEmpty()) {
            return userRepository.count(); // Fallback to total count
        }
        
        // For tenant-specific counting, we'll count all users since they're already filtered by tenant context
        // The repository operations are automatically scoped to the current tenant
        return userRepository.count();
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
