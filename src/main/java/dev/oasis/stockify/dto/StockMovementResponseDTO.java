package dev.oasis.stockify.dto;

import dev.oasis.stockify.model.StockMovement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for displaying stock movement information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementResponseDTO {

    private Long id;
    private Long productId;
    private String productTitle;
    private String productSku;
    private StockMovement.MovementType movementType;
    private String movementTypeDisplay;
    private Integer quantity;
    private Integer previousStock;
    private Integer newStock;
    private String referenceId;
    private String notes;
    private Long createdBy;
    private String createdByUsername;
    private LocalDateTime createdAt;

    public LocalDateTime getTimestamp() {
        return createdAt;
    }

    public static StockMovementResponseDTO fromEntity(StockMovement movement) {
        return StockMovementResponseDTO.builder()
                .id(movement.getId())
                .productId(movement.getProduct().getId())
                .productTitle(movement.getProduct().getTitle())
                .productSku(movement.getProduct().getSku())
                .movementType(movement.getMovementType())
                .movementTypeDisplay(movement.getMovementType().getDisplayName())
                .quantity(movement.getQuantity())
                .previousStock(movement.getPreviousStock())
                .newStock(movement.getNewStock())
                .referenceId(movement.getReferenceId())
                .notes(movement.getNotes())
                .createdBy(movement.getCreatedBy())
                .createdAt(movement.getCreatedAt())
                .build();
    }
}
