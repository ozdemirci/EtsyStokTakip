package dev.oasis.stockify.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * DTO for barcode scan requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarcodeScanRequestDTO {
    
    @NotBlank(message = "Scan code is required")
    private String scanCode;
    
    @NotNull(message = "Scan type is required")
    private ScanType scanType;
    
    @NotNull(message = "Action is required")
    private ScanAction action;
    
    @Positive(message = "Quantity must be positive")
    private Integer quantity;
    
    private String notes;
    
    /**
     * Type of scan (barcode or QR code)
     */
    public enum ScanType {
        BARCODE("Barcode"),
        QR_CODE("QR Code");
        
        private final String displayName;
        
        ScanType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * Action to perform after scan
     */
    public enum ScanAction {
        STOCK_IN("Stock In"),
        STOCK_OUT("Stock Out"),
        LOOKUP("Lookup Product"),
        ADJUSTMENT("Stock Adjustment");
        
        private final String displayName;
        
        ScanAction(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}
