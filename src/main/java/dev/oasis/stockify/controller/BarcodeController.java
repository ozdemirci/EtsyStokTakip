package dev.oasis.stockify.controller;

import dev.oasis.stockify.dto.BarcodeScanRequestDTO;
import dev.oasis.stockify.dto.BarcodeScanResponseDTO;
import dev.oasis.stockify.dto.ProductResponseDTO;
import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.service.BarcodeService;
import dev.oasis.stockify.util.ControllerTenantUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for barcode scanning operations
 */
@RestController
@RequestMapping("/api/barcode")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
public class BarcodeController {
    
    private final BarcodeService barcodeService;
    private final ControllerTenantUtil tenantResolutionUtil;
    private final AppUserRepository appUserRepository;
    
    /**
     * Ensure tenant context is set for all requests
     */
    @ModelAttribute
    public void setupTenantContext(HttpServletRequest request) {
        tenantResolutionUtil.setupTenantContext(request);
    }
    
    /**
     * Find product by barcode or QR code
     */
    @GetMapping("/lookup")
    public ResponseEntity<Map<String, Object>> lookupProduct(
            @RequestParam String scanCode,
            @RequestParam String scanType,
            HttpServletRequest request,
            Authentication authentication) {
        
        String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
        log.info("🔍 Product lookup request for tenant: {} with {} code: {}", tenantId, scanType, scanCode);
        
        try {
            BarcodeScanRequestDTO.ScanType type = BarcodeScanRequestDTO.ScanType.valueOf(scanType.toUpperCase());
            Optional<ProductResponseDTO> productOpt = barcodeService.findProductByScanCode(scanCode, type);
            
            Map<String, Object> response = new HashMap<>();
            if (productOpt.isPresent()) {
                response.put("success", true);
                response.put("message", "Product found");
                response.put("product", productOpt.get());
                log.info("✅ Product found: {} for {} code: {}", productOpt.get().getTitle(), scanType, scanCode);
            } else {
                response.put("success", false);
                response.put("message", "No product found for " + scanType.toLowerCase() + ": " + scanCode);
                response.put("product", null);
                log.warn("❌ No product found for {} code: {}", scanType, scanCode);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid scan type: {}", scanType);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Invalid scan type: " + scanType);
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("❌ Error during product lookup: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error during lookup: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Process barcode scan for stock operations
     */
    @PostMapping("/scan")
    public ResponseEntity<BarcodeScanResponseDTO> processScan(
            @Valid @RequestBody BarcodeScanRequestDTO scanRequest,
            HttpServletRequest request,
            Authentication authentication,
            Principal principal) {
        
        String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
        log.info("📱 Barcode scan request for tenant: {} - {} scan: {} with action: {}", 
                tenantId, 
                scanRequest.getScanType().getDisplayName(), 
                scanRequest.getScanCode(), 
                scanRequest.getAction().getDisplayName());
        
        try {
            // Get current user ID
            Long userId = null;
            if (principal != null) {
                Optional<AppUser> currentUser = appUserRepository.findByUsername(principal.getName());
                if (currentUser.isPresent()) {
                    userId = currentUser.get().getId();
                }
            }
            
            if (userId == null) {
                log.error("❌ Could not determine current user for scan operation");
                return ResponseEntity.badRequest()
                        .body(BarcodeScanResponseDTO.error("User authentication required"));
            }
            
            // Process the scan
            BarcodeScanResponseDTO response = barcodeService.processScan(scanRequest, userId);
            
            if (response.isSuccess()) {
                log.info("✅ Scan processed successfully: {}", response.getMessage());
                return ResponseEntity.ok(response);
            } else {
                log.warn("⚠️ Scan processing failed: {}", response.getMessage());
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            log.error("❌ Error processing scan: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(BarcodeScanResponseDTO.error("Error processing scan: " + e.getMessage()));
        }
    }
    
    /**
     * Check if barcode is available (for product creation/editing)
     */
    @GetMapping("/check/barcode")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Boolean>> checkBarcodeAvailability(
            @RequestParam String barcode,
            @RequestParam(required = false) Long excludeProductId,
            HttpServletRequest request,
            Authentication authentication) {
        
        String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
        log.debug("🔍 Checking barcode availability for tenant: {} - barcode: {}", tenantId, barcode);
        
        try {
            boolean available = barcodeService.isBarcodeAvailable(barcode, excludeProductId);
            
            Map<String, Boolean> response = new HashMap<>();
            response.put("available", available);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error checking barcode availability: {}", e.getMessage(), e);
            Map<String, Boolean> errorResponse = new HashMap<>();
            errorResponse.put("available", false);
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Check if QR code is available (for product creation/editing)
     */
    @GetMapping("/check/qrcode")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Boolean>> checkQrCodeAvailability(
            @RequestParam String qrCode,
            @RequestParam(required = false) Long excludeProductId,
            HttpServletRequest request,
            Authentication authentication) {
        
        String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
        log.debug("🔍 Checking QR code availability for tenant: {} - qrCode: {}", tenantId, qrCode);
        
        try {
            boolean available = barcodeService.isQrCodeAvailable(qrCode, excludeProductId);
            
            Map<String, Boolean> response = new HashMap<>();
            response.put("available", available);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error checking QR code availability: {}", e.getMessage(), e);
            Map<String, Boolean> errorResponse = new HashMap<>();
            errorResponse.put("available", false);
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Get scan action types for UI
     */
    @GetMapping("/actions")
    public ResponseEntity<Map<String, Object>> getScanActions() {
        Map<String, Object> response = new HashMap<>();
        
        Map<String, String> scanTypes = new HashMap<>();
        for (BarcodeScanRequestDTO.ScanType type : BarcodeScanRequestDTO.ScanType.values()) {
            scanTypes.put(type.name(), type.getDisplayName());
        }
        
        Map<String, String> scanActions = new HashMap<>();
        for (BarcodeScanRequestDTO.ScanAction action : BarcodeScanRequestDTO.ScanAction.values()) {
            scanActions.put(action.name(), action.getDisplayName());
        }
        
        response.put("scanTypes", scanTypes);
        response.put("scanActions", scanActions);
        
        return ResponseEntity.ok(response);
    }
}
