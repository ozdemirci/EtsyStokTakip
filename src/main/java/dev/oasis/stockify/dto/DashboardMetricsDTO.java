package dev.oasis.stockify.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMetricsDTO {
    
    private long totalProducts;
    private long activeProducts;
    private long totalUsers;
    private double totalInventoryValue;
    private long lowStockProducts;
    private long outOfStockProducts;
    private long activeNotifications;
    private long criticalNotifications;
    private double monthlyRevenue;
    private double dailyRevenue;
}
