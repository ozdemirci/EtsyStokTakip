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
    private long totalUsers;
    private double totalInventoryValue;
    private long lowStockProducts;
    private long activeNotifications;
    private double monthlyRevenue;
    private double dailyRevenue;
}
