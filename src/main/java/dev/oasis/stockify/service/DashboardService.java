package dev.oasis.stockify.service;

import dev.oasis.stockify.dto.DashboardMetricsDTO;
import dev.oasis.stockify.dto.DashboardStatsDTO;
import dev.oasis.stockify.dto.StockMovementResponseDTO;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.repository.ProductRepository;
import dev.oasis.stockify.repository.StockNotificationRepository;
import dev.oasis.stockify.util.ServiceTenantUtil;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
    private final ServiceTenantUtil serviceTenantUtil;

    @PostConstruct
    public void initMetrics() {
        // Initialize metrics with default values
        meterRegistry.gauge("sales.monthly", 0.0);
        meterRegistry.gauge("sales.daily", 0.0);
    }   
    
    public DashboardMetricsDTO getDashboardMetrics() {
        String currentTenant = serviceTenantUtil.getCurrentTenant();
        log.debug("🏢 Getting dashboard metrics for tenant: {}", currentTenant);
        
        // Get tenant-specific counts
        long tenantUserCount = getTenantUserCount();
        long productCount = getTenantProductCount();        
        Long criticalNotifications = notificationRepository.countCriticalNotifications();
        
        
          
        log.debug("📊 Dashboard metrics - Tenant: {}, Users: {}, Products: {}", 
                 currentTenant, tenantUserCount, productCount);
        
        return DashboardMetricsDTO.builder()
                .totalProducts(productCount)
                .activeProducts(countActiveProducts())
                .totalUsers(tenantUserCount)
                .totalInventoryValue(calculateTotalInventoryValue())
                .lowStockProducts(countLowStockProducts())
                .outOfStockProducts(countOutOfStockProducts())
                .activeNotifications(notificationRepository.count())
                .criticalNotifications(criticalNotifications)
                .monthlyRevenue(getMonthlyRevenue())
                .dailyRevenue(getDailyRevenue())
                .build();
    }

    /**
     * Get recent stock movements for dashboard
     */
    public List<StockMovementResponseDTO> getRecentStockMovements() {
        String currentTenant = serviceTenantUtil.getCurrentTenant();
        log.debug("📋 Fetching recent stock movements for tenant: {}", currentTenant);
        
        return stockMovementService.getRecentMovements(10); // Last 10 movements
    }

    /**
     * Get stock movement statistics for dashboard
     */
    public StockMovementService.StockMovementStats getStockMovementStats() {
        String currentTenant = serviceTenantUtil.getCurrentTenant();
        log.debug("📊 Fetching stock movement statistics for tenant: {}", currentTenant);
        
        return stockMovementService.getStockMovementStats();
    }
    
    private long getTenantProductCount() {
        String currentTenant = serviceTenantUtil.getCurrentTenant();
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
    }
    
    private long getTenantUserCount() {
        String currentTenant = serviceTenantUtil.getCurrentTenant();
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
    }    private double calculateTotalInventoryValue() {
        String currentTenant = serviceTenantUtil.getCurrentTenant();
        log.debug("� Calculating total inventory value for tenant: {}", currentTenant);
        
        // Use repository method to calculate inventory value directly in database for better performance
        Double value = productRepository.calculateTotalInventoryValue();
        double totalValue = value != null ? value : 0.0;
        
        log.debug("💰 Total inventory value for tenant: {} = {}", currentTenant, totalValue);
        return totalValue;
    }

    private long countLowStockProducts() {
        String currentTenant = serviceTenantUtil.getCurrentTenant();
        log.debug("📦 Counting low stock products for tenant: {}", currentTenant);
        
        // Use repository method to count low stock products directly in database for better performance
        long count = productRepository.countLowStockProducts();
        log.debug("📦 Found {} low stock products for tenant: {}", count, currentTenant);
        
        return count;
    }

    private long countOutOfStockProducts() {
        String currentTenant = serviceTenantUtil.getCurrentTenant();
        log.debug("📦 Counting out of stock products for tenant: {}", currentTenant);
        
        // Use repository method to count out of stock products directly in database for better performance
        long count = productRepository.countOutOfStockProducts();
        log.debug("📦 Found {} out of stock products for tenant: {}", count, currentTenant);
        
        return count;
    }

    private long countActiveProducts() {
        String currentTenant = serviceTenantUtil.getCurrentTenant();
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

    public DashboardStatsDTO getDashboardStats() {
        List<Product> products = productRepository.findAll();

        long totalProducts = products.size();
        int totalStock = products.stream()
                .mapToInt(Product::getStockLevel)
                .sum();

        long lowStockCount = products.stream()
                .filter(Product::isLowStock)
                .count();

        return new DashboardStatsDTO(totalProducts, totalStock, lowStockCount);
    }
}
