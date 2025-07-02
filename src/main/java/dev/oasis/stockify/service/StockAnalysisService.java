package dev.oasis.stockify.service;

import dev.oasis.stockify.dto.StockAnalysisDTO;
import dev.oasis.stockify.dto.StockAnalysisRequestDTO;
import dev.oasis.stockify.dto.StockMovementResponseDTO;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.model.StockMovement;
import dev.oasis.stockify.repository.ProductRepository;
import dev.oasis.stockify.repository.StockMovementRepository;
import dev.oasis.stockify.util.ServiceTenantUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for stock analysis and predictions
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StockAnalysisService {

    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final ServiceTenantUtil serviceTenantUtil;

    /**
     * Generate comprehensive stock analysis
     */
    public StockAnalysisDTO generateStockAnalysis(StockAnalysisRequestDTO request) {
        String currentTenant = serviceTenantUtil.getCurrentTenant();
        log.info("📊 Generating stock analysis for tenant: {}, productId: {}, days: {}", 
                currentTenant, request.getProductId(), request.getDays());

        LocalDateTime startDate = LocalDateTime.now().minusDays(request.getDays());
        LocalDateTime endDate = LocalDateTime.now();

        // Get filtered movements
        List<StockMovement> movements = getFilteredMovements(request, startDate, endDate);
        
        // Calculate summary statistics
        int totalMovements = movements.size();
        int totalIn = calculateTotalIn(movements);
        int totalOut = calculateTotalOut(movements);
        int netChange = totalIn - totalOut;

        // Generate stock prediction
        StockAnalysisDTO.StockPredictionDTO prediction = generateStockPrediction(request, movements);

        // Generate usage trends
        List<StockAnalysisDTO.UsageTrendDTO> trends = generateUsageTrends(movements, request.getDays());

        // Convert movements to DTOs
        List<StockMovementResponseDTO> movementDTOs = movements.stream()
                .map(StockMovementResponseDTO::fromEntity)
                .collect(Collectors.toList());

        log.info("📊 Analysis complete - Total movements: {}, In: {}, Out: {}, Net: {}", 
                totalMovements, totalIn, totalOut, netChange);

        return StockAnalysisDTO.builder()
                .totalMovements(totalMovements)
                .totalIn(totalIn)
                .totalOut(totalOut)
                .netChange(netChange)
                .prediction(prediction)
                .trends(trends)
                .movements(movementDTOs)
                .build();
    }

    /**
     * Get filtered movements based on request parameters
     */
    private List<StockMovement> getFilteredMovements(StockAnalysisRequestDTO request, 
                                                   LocalDateTime startDate, 
                                                   LocalDateTime endDate) {
        
        if (request.getProductId() != null && request.getMovementType() != null) {
            return stockMovementRepository.findByProductIdAndMovementTypeAndCreatedAtBetween(
                    request.getProductId(), request.getMovementType(), startDate, endDate);
        } else if (request.getProductId() != null) {
            return stockMovementRepository.findByProductIdAndCreatedAtBetween(
                    request.getProductId(), startDate, endDate);
        } else if (request.getMovementType() != null) {
            return stockMovementRepository.findByMovementTypeAndCreatedAtBetween(
                    request.getMovementType(), startDate, endDate);
        } else {
            return stockMovementRepository.findByCreatedAtBetween(startDate, endDate);
        }
    }

    /**
     * Calculate total stock in movements
     */
    private int calculateTotalIn(List<StockMovement> movements) {
        return movements.stream()
                .filter(m -> isInboundMovement(m.getMovementType()))
                .mapToInt(StockMovement::getQuantity)
                .sum();
    }

    /**
     * Calculate total stock out movements
     */
    private int calculateTotalOut(List<StockMovement> movements) {
        return movements.stream()
                .filter(m -> isOutboundMovement(m.getMovementType()))
                .mapToInt(m -> Math.abs(m.getQuantity()))
                .sum();
    }

    /**
     * Generate stock prediction based on historical data
     */
    private StockAnalysisDTO.StockPredictionDTO generateStockPrediction(StockAnalysisRequestDTO request, 
                                                                       List<StockMovement> movements) {
        
        if (request.getProductId() == null) {
            return StockAnalysisDTO.StockPredictionDTO.builder()
                    .currentStock(0)
                    .avgDailyUsage(0.0)
                    .daysRemaining(0)
                    .riskLevel("UNKNOWN")
                    .build();
        }

        // Get current stock level
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));
        
        Integer currentStock = product.getStockLevel();

        // Calculate average daily outbound usage
        List<StockMovement> outboundMovements = movements.stream()
                .filter(m -> isOutboundMovement(m.getMovementType()))
                .collect(Collectors.toList());

        double avgDailyUsage = 0.0;
        if (!outboundMovements.isEmpty()) {
            int totalOutbound = outboundMovements.stream()
                    .mapToInt(m -> Math.abs(m.getQuantity()))
                    .sum();
            avgDailyUsage = (double) totalOutbound / request.getDays();
        }

        // Calculate days remaining
        Integer daysRemaining = 0;
        LocalDate estimatedDepletionDate = null;
        String riskLevel = "LOW";

        if (avgDailyUsage > 0 && currentStock > 0) {
            daysRemaining = (int) Math.ceil(currentStock / avgDailyUsage);
            estimatedDepletionDate = LocalDate.now().plusDays(daysRemaining);

            // Determine risk level
            if (daysRemaining <= 7) {
                riskLevel = "CRITICAL";
            } else if (daysRemaining <= 30) {
                riskLevel = "HIGH";
            } else if (daysRemaining <= 60) {
                riskLevel = "MEDIUM";
            } else {
                riskLevel = "LOW";
            }
        } else if (currentStock <= 0) {
            riskLevel = "CRITICAL";
        }

        return StockAnalysisDTO.StockPredictionDTO.builder()
                .currentStock(currentStock)
                .avgDailyUsage(avgDailyUsage)
                .daysRemaining(daysRemaining)
                .estimatedDepletionDate(estimatedDepletionDate)
                .riskLevel(riskLevel)
                .build();
    }

    /**
     * Generate daily usage trends
     */
    private List<StockAnalysisDTO.UsageTrendDTO> generateUsageTrends(List<StockMovement> movements, int days) {
        Map<LocalDate, Integer> dailyUsage = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        // Initialize all days with 0 usage
        for (int i = 0; i < Math.min(days, 14); i++) {
            LocalDate date = LocalDate.now().minusDays(i);
            dailyUsage.put(date, 0);
        }

        // Calculate actual daily usage (outbound movements only)
        movements.stream()
                .filter(m -> isOutboundMovement(m.getMovementType()))
                .forEach(movement -> {
                    LocalDate date = movement.getCreatedAt().toLocalDate();
                    if (dailyUsage.containsKey(date)) {
                        dailyUsage.put(date, dailyUsage.get(date) + Math.abs(movement.getQuantity()));
                    }
                });

        // Convert to trend DTOs and sort by date
        return dailyUsage.entrySet().stream()
                .map(entry -> StockAnalysisDTO.UsageTrendDTO.builder()
                        .date(entry.getKey().format(formatter))
                        .usage(entry.getValue())
                        .movementType("OUT")
                        .build())
                .sorted((a, b) -> {
                    LocalDate dateA = LocalDate.parse(a.getDate(), formatter);
                    LocalDate dateB = LocalDate.parse(b.getDate(), formatter);
                    return dateB.compareTo(dateA); // Most recent first
                })
                .collect(Collectors.toList());
    }

    /**
     * Check if movement type is inbound (increases stock)
     */
    private boolean isInboundMovement(StockMovement.MovementType type) {
        return type == StockMovement.MovementType.IN || 
               type == StockMovement.MovementType.RETURN ||
               (type == StockMovement.MovementType.ADJUSTMENT); // Adjustments can be positive
    }

    /**
     * Check if movement type is outbound (decreases stock)
     */
    private boolean isOutboundMovement(StockMovement.MovementType type) {
        return type == StockMovement.MovementType.OUT ||
               type == StockMovement.MovementType.TRANSFER ||
               type == StockMovement.MovementType.DAMAGED ||
               type == StockMovement.MovementType.EXPIRED;
    }
}
