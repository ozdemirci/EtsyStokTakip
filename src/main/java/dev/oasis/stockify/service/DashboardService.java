package dev.oasis.stockify.service;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.DashboardMetricsDTO;
import dev.oasis.stockify.dto.DashboardStats;
import dev.oasis.stockify.dto.StockMovementResponseDTO;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.repository.ProductRepository;
import dev.oasis.stockify.repository.StockNotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {
    private final ProductRepository productRepository;
    private final AppUserRepository userRepository;
    private final StockNotificationRepository notificationRepository;
    private final StockMovementService stockMovementService;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void initMetrics() {
        // Initialize metrics with default values
        meterRegistry.gauge("sales.monthly", 0.0);
        meterRegistry.gauge("sales.daily", 0.0);
    }    public DashboardMetricsDTO getDashboardMetrics() {
        String currentTenant = TenantContext.getCurrentTenant();
        log.debug("🏢 Getting dashboard metrics for tenant: {}", currentTenant);
        
        // Get tenant-specific counts
        long tenantUserCount = getTenantUserCount();
        long productCount = getTenantProductCount();
        
        log.debug("📊 Dashboard metrics - Tenant: {}, Users: {}, Products: {}", 
                 currentTenant, tenantUserCount, productCount);
        
        return DashboardMetricsDTO.builder()
                .totalProducts(productCount)
                .activeProducts(countActiveProducts())
                .totalUsers(tenantUserCount)
                .totalInventoryValue(calculateTotalInventoryValue())
                .lowStockProducts(countLowStockProducts())
                .activeNotifications(notificationRepository.count())
                .monthlyRevenue(getMonthlyRevenue())
                .dailyRevenue(getDailyRevenue())
                .build();
    }

    /**
     * Get recent stock movements for dashboard
     */
    public List<StockMovementResponseDTO> getRecentStockMovements() {
        String currentTenant = TenantContext.getCurrentTenant();
        log.debug("📋 Fetching recent stock movements for tenant: {}", currentTenant);
        
        return stockMovementService.getRecentMovements(10); // Last 10 movements
    }

    /**
     * Get stock movement statistics for dashboard
     */
    public StockMovementService.StockMovementStats getStockMovementStats() {
        String currentTenant = TenantContext.getCurrentTenant();
        log.debug("📊 Fetching stock movement statistics for tenant: {}", currentTenant);
        
        return stockMovementService.getStockMovementStats();
    }
      private long getTenantProductCount() {
        String currentTenant = TenantContext.getCurrentTenant();
        log.debug("📦 Counting products for tenant: {}", currentTenant);
        
        if (currentTenant == null || currentTenant.isEmpty()) {
            log.warn("⚠️ No tenant context set, returning total product count");
            return productRepository.count();
        }
        
        // Try both approaches: automatic multi-tenant filtering and manual tenant filtering
        long autoCount = productRepository.count();
        // Temporarily disable manual count until database migration is done
        // long manualCount = productRepository.countByTenantId(currentTenant);
        long manualCount = 0;
        
        log.debug("📦 Product count comparison for tenant {}: auto={}, manual={}", 
                 currentTenant, autoCount, manualCount);
        
        // Use auto count for now
        long finalCount = autoCount;
        log.debug("📦 Final product count for tenant: {} = {}", currentTenant, finalCount);
        
        return finalCount;
    }private long getTenantUserCount() {
        String currentTenant = TenantContext.getCurrentTenant();
        log.debug("👥 Counting users for tenant: {}", currentTenant);
        
        if (currentTenant == null || currentTenant.isEmpty()) {
            log.warn("⚠️ No tenant context set, returning total user count");
            return userRepository.count();
        }
        
        // Try both approaches: automatic multi-tenant filtering and manual tenant filtering
        long autoCount = userRepository.count();
        // Temporarily disable manual count until database migration is done
        // long manualCount = userRepository.countByPrimaryTenant(currentTenant);
        long manualCount = 0;
        
        log.debug("👥 User count comparison for tenant {}: auto={}, manual={}", 
                 currentTenant, autoCount, manualCount);
        
        // Use auto count for now
        long finalCount = autoCount;
        log.debug("👥 Final user count for tenant: {} = {}", currentTenant, finalCount);
        
        return finalCount;
    }private double calculateTotalInventoryValue() {
        String currentTenant = TenantContext.getCurrentTenant();
        List<Product> products = productRepository.findAll();
        log.debug("📦 Calculating inventory value for tenant: {} - Found {} products", 
                 currentTenant, products.size());
        
        return products.stream()
                .map(product -> product.getPrice().multiply(BigDecimal.valueOf(product.getStockLevel())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .doubleValue();
    }

    private long countLowStockProducts() {
        String currentTenant = TenantContext.getCurrentTenant();
        List<Product> products = productRepository.findAll();
        log.debug("📦 Counting low stock products for tenant: {} - Total products: {}",
                 currentTenant, products.size());

        return products.stream()
                .filter(product -> product.getStockLevel() < product.getLowStockThreshold())
                .count();
    }

    private long countActiveProducts() {
        String currentTenant = TenantContext.getCurrentTenant();
        long count = productRepository.countByIsActive(true);
        log.debug("🔁 Counting active products for tenant: {} = {}", currentTenant, count);
        return count;
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
