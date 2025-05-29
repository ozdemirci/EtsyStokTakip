package dev.oasis.stockify.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class DashboardMetricsDTO {
    private long totalProducts;
    private long totalUsers;
    private double totalInventoryValue;
    private long lowStockProducts;
    private long activeNotifications;
    private double monthlyRevenue;
    private double dailyRevenue;
}
