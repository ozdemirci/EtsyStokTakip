package dev.oasis.stockify.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO for stock analysis results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAnalysisDTO {
    
    private Integer totalMovements;
    private Integer totalIn;
    private Integer totalOut;
    private Integer netChange;
    private StockPredictionDTO prediction;
    private List<UsageTrendDTO> trends;
    private List<StockMovementResponseDTO> movements;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockPredictionDTO {
        private Integer currentStock;
        private Double avgDailyUsage;
        private Integer daysRemaining;
        private LocalDate estimatedDepletionDate;
        private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageTrendDTO {
        private String date;
        private Integer usage;
        private String movementType;
    }
}
