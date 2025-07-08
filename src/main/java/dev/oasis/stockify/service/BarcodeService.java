package dev.oasis.stockify.service;

import dev.oasis.stockify.dto.BarcodeScanRequestDTO;
import dev.oasis.stockify.dto.BarcodeScanResponseDTO;
import dev.oasis.stockify.dto.ProductResponseDTO;
import dev.oasis.stockify.dto.StockMovementCreateDTO;
import dev.oasis.stockify.mapper.ProductMapper;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.model.StockMovement;
import dev.oasis.stockify.repository.ProductRepository;
import dev.oasis.stockify.util.ServiceTenantUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for barcode scanning operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BarcodeService {
    
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ProductService productService;
    private final StockMovementService stockMovementService;
    private final ServiceTenantUtil serviceTenantUtil;
    
    /**
     * Find product by barcode or QR code
     */
    public Optional<ProductResponseDTO> findProductByScanCode(String scanCode, BarcodeScanRequestDTO.ScanType scanType) {
        log.info("🔍 Searching for product with {} code: {}", scanType.getDisplayName(), scanCode);
        
        String currentTenant = serviceTenantUtil.getCurrentTenant();
        if (currentTenant == null || currentTenant.isEmpty()) {
            log.warn("⚠️ No tenant context available for barcode scan");
            return Optional.empty();
        }
        
        Optional<Product> product;
        if (scanType == BarcodeScanRequestDTO.ScanType.BARCODE) {
            product = productRepository.findByBarcodeAndScanEnabledTrue(scanCode);
        } else {
            product = productRepository.findByQrCodeAndScanEnabledTrue(scanCode);
        }
        
        if (product.isPresent()) {
            log.info("✅ Product found: {} for {} code: {}", product.get().getTitle(), scanType.getDisplayName(), scanCode);
            return Optional.of(productMapper.toDto(product.get()));
        } else {
            log.warn("❌ No product found for {} code: {}", scanType.getDisplayName(), scanCode);
            return Optional.empty();
        }
    }
    
    /**
     * Process barcode scan for stock operations
     */
    @Transactional
    public BarcodeScanResponseDTO processScan(BarcodeScanRequestDTO scanRequest, Long userId) {
        log.info("📱 Processing {} scan: {} with action: {}", 
                scanRequest.getScanType().getDisplayName(), 
                scanRequest.getScanCode(), 
                scanRequest.getAction().getDisplayName());
        
        String currentTenant = serviceTenantUtil.getCurrentTenant();
        if (currentTenant == null || currentTenant.isEmpty()) {
            return BarcodeScanResponseDTO.error("No tenant context available for scan processing");
        }
        
        try {
            // Find product by scan code
            Optional<ProductResponseDTO> productOpt = findProductByScanCode(scanRequest.getScanCode(), scanRequest.getScanType());
            
            if (productOpt.isEmpty()) {
                String message = String.format("Product not found for %s: %s", 
                        scanRequest.getScanType().getDisplayName().toLowerCase(), 
                        scanRequest.getScanCode());
                return BarcodeScanResponseDTO.error(message);
            }
            
            ProductResponseDTO product = productOpt.get();
            
            // Handle different scan actions
            switch (scanRequest.getAction()) {
                case LOOKUP:
                    return handleLookup(scanRequest, product);
                
                case STOCK_IN:
                case STOCK_OUT:
                case ADJUSTMENT:
                    return handleStockMovement(scanRequest, product, userId);
                
                default:
                    return BarcodeScanResponseDTO.error("Unsupported scan action: " + scanRequest.getAction());
            }
            
        } catch (Exception e) {
            log.error("❌ Error processing scan: {}", e.getMessage(), e);
            return BarcodeScanResponseDTO.error("Error processing scan: " + e.getMessage());
        }
    }
    
    /**
     * Handle product lookup action
     */
    private BarcodeScanResponseDTO handleLookup(BarcodeScanRequestDTO scanRequest, ProductResponseDTO product) {
        log.info("👁️ Product lookup for: {}", product.getTitle());
        
        BarcodeScanResponseDTO response = BarcodeScanResponseDTO.success(
                "Product found: " + product.getTitle(), product);
        response.setScanCode(scanRequest.getScanCode());
        response.setScanType(scanRequest.getScanType());
        response.setAction(scanRequest.getAction());
        
        return response;
    }
    
    /**
     * Handle stock movement actions
     */
    private BarcodeScanResponseDTO handleStockMovement(BarcodeScanRequestDTO scanRequest, 
                                                      ProductResponseDTO product, 
                                                      Long userId) {
        log.info("📦 Processing stock movement for product: {} with action: {}", 
                product.getTitle(), scanRequest.getAction().getDisplayName());
        
        if (scanRequest.getQuantity() == null || scanRequest.getQuantity() <= 0) {
            return BarcodeScanResponseDTO.error("Valid quantity is required for stock operations");
        }
        
        try {
            // Determine movement type based on action
            StockMovement.MovementType movementType;
            switch (scanRequest.getAction()) {
                case STOCK_IN:
                    movementType = StockMovement.MovementType.IN;
                    break;
                case STOCK_OUT:
                    movementType = StockMovement.MovementType.OUT;
                    break;
                case ADJUSTMENT:
                    movementType = StockMovement.MovementType.ADJUSTMENT;
                    break;
                default:
                    return BarcodeScanResponseDTO.error("Invalid stock movement action");
            }
            
            // Create stock movement
            StockMovementCreateDTO movementDTO = StockMovementCreateDTO.builder()
                    .productId(product.getId())
                    .movementType(movementType)
                    .quantity(scanRequest.getQuantity())
                    .referenceId("SCAN_" + System.currentTimeMillis())
                    .notes(String.format("Scanned %s: %s - %s", 
                            scanRequest.getScanType().getDisplayName().toLowerCase(),
                            scanRequest.getScanCode(),
                            scanRequest.getNotes() != null ? scanRequest.getNotes() : ""))
                    .createdBy(userId)
                    .build();
            
            // Process the stock movement
            var movementResponse = stockMovementService.createStockMovement(movementDTO);
            
            // Get updated product data
            Optional<ProductResponseDTO> updatedProductOpt = productService.getProductById(product.getId());
            ProductResponseDTO updatedProduct = updatedProductOpt.orElse(product);
            
            // Create successful response
            BarcodeScanResponseDTO response = BarcodeScanResponseDTO.success(
                    String.format("Stock %s completed. %s: %d → %d", 
                            scanRequest.getAction().getDisplayName().toLowerCase(),
                            product.getTitle(),
                            movementResponse.getPreviousStock(),
                            movementResponse.getNewStock()),
                    updatedProduct);
            
            response.setScanCode(scanRequest.getScanCode());
            response.setScanType(scanRequest.getScanType());
            response.setAction(scanRequest.getAction());
            response.setQuantity(scanRequest.getQuantity());
            response.setNotes(scanRequest.getNotes());
            response.setPreviousStock(movementResponse.getPreviousStock());
            response.setNewStock(movementResponse.getNewStock());
            
            log.info("✅ Stock movement completed: {} {} for product: {}", 
                    scanRequest.getAction().getDisplayName(), 
                    scanRequest.getQuantity(), 
                    product.getTitle());
            
            return response;
            
        } catch (Exception e) {
            log.error("❌ Error creating stock movement: {}", e.getMessage(), e);
            return BarcodeScanResponseDTO.error("Failed to process stock movement: " + e.getMessage());
        }
    }
    
    /**
     * Check if a barcode is available (not already used)
     */
    public boolean isBarcodeAvailable(String barcode, Long excludeProductId) {
        String currentTenant = serviceTenantUtil.getCurrentTenant();
        if (currentTenant == null || currentTenant.isEmpty()) {
            log.warn("⚠️ No tenant context available for barcode availability check");
            return false;
        }
        
        if (barcode == null || barcode.trim().isEmpty()) {
            return true; // Empty barcode is considered available
        }
        
        Optional<Product> existingProduct;
        if (excludeProductId != null) {
            existingProduct = productRepository.findByBarcodeAndIdNot(barcode, excludeProductId);
        } else {
            existingProduct = productRepository.findByBarcode(barcode);
        }
        
        return existingProduct.isEmpty();
    }
    
    /**
     * Check if a QR code is available (not already used)
     */
    public boolean isQrCodeAvailable(String qrCode, Long excludeProductId) {
        String currentTenant = serviceTenantUtil.getCurrentTenant();
        if (currentTenant == null || currentTenant.isEmpty()) {
            log.warn("⚠️ No tenant context available for QR code availability check");
            return false;
        }
        
        if (qrCode == null || qrCode.trim().isEmpty()) {
            return true; // Empty QR code is considered available
        }
        
        Optional<Product> existingProduct;
        if (excludeProductId != null) {
            existingProduct = productRepository.findByQrCodeAndIdNot(qrCode, excludeProductId);
        } else {
            existingProduct = productRepository.findByQrCode(qrCode);
        }
        
        return existingProduct.isEmpty();
    }
}
