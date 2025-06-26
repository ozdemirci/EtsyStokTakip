package dev.oasis.stockify.dto;

import dev.oasis.stockify.model.StockMovement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating stock movements
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementCreateDTO {

    private Long productId;
    private StockMovement.MovementType movementType;
    private Integer quantity;
    private String referenceId;
    private String notes;
    private Long createdBy;
}
