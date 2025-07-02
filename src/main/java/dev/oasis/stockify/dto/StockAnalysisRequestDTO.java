package dev.oasis.stockify.dto;

import dev.oasis.stockify.model.StockMovement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for stock analysis request parameters
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAnalysisRequestDTO {
    
    private Long productId;
    private Integer days;
    private StockMovement.MovementType movementType;
    private Boolean includeAdjustments;
    private Boolean includeDamaged;
    private Boolean includeExpired;
    
    // Default values
    public Integer getDays() {
        return days != null ? days : 30;
    }
    
    public Boolean getIncludeAdjustments() {
        return includeAdjustments != null ? includeAdjustments : true;
    }
    
    public Boolean getIncludeDamaged() {
        return includeDamaged != null ? includeDamaged : true;
    }
    
    public Boolean getIncludeExpired() {
        return includeExpired != null ? includeExpired : true;
    }
}
