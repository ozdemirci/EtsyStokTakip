package dev.oasis.stockify.service;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.StockMovementCreateDTO;
import dev.oasis.stockify.dto.StockMovementResponseDTO;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.model.StockMovement;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.repository.ProductRepository;
import dev.oasis.stockify.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing stock movements
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final AppUserRepository appUserRepository;

    /**
     * Create a new stock movement
     */
    @Transactional
    public StockMovementResponseDTO createStockMovement(StockMovementCreateDTO dto) {
        String currentTenant = TenantContext.getCurrentTenant();
        log.info("🔄 Creating stock movement for product ID: {} in tenant: {}", dto.getProductId(), currentTenant);

        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + dto.getProductId()));

        Integer previousStock = product.getStockLevel();
        Integer newStock = calculateNewStock(previousStock, dto.getMovementType(), dto.getQuantity());

        // Validate new stock level
        if (newStock < 0) {
            throw new IllegalArgumentException("Stock level cannot be negative. Current: " + previousStock + ", Change: " + dto.getQuantity());
        }

        // Create stock movement record
        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setMovementType(dto.getMovementType());
        movement.setQuantity(dto.getQuantity());
        movement.setPreviousStock(previousStock);
        movement.setNewStock(newStock);
        movement.setReferenceId(dto.getReferenceId());
        movement.setNotes(dto.getNotes());
        movement.setCreatedBy(dto.getCreatedBy());

        // Save movement record
        StockMovement savedMovement = stockMovementRepository.save(movement);

        // Update product stock level
        product.setStockLevel(newStock);
        productRepository.save(product);

        log.info("✅ Stock movement created - Product: {}, Type: {}, Quantity: {}, Previous: {} -> New: {} for tenant: {}",
                product.getTitle(), dto.getMovementType(), dto.getQuantity(), previousStock, newStock, currentTenant);

        return convertToResponseDTO(savedMovement);
    }

    /**
     * Get all stock movements with pagination
     */
    public Page<StockMovementResponseDTO> getAllStockMovements(int page, int size) {
        String currentTenant = TenantContext.getCurrentTenant();
        log.debug("📋 Fetching stock movements for tenant: {} - Page: {}, Size: {}", currentTenant, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StockMovement> movements = stockMovementRepository.findRecent(pageable);

        return movements.map(this::convertToResponseDTO);
    }

    /**
     * Get stock movements by product ID
     */
    public List<StockMovementResponseDTO> getStockMovementsByProduct(Long productId) {
        String currentTenant = TenantContext.getCurrentTenant();
        log.debug("📋 Fetching stock movements for product ID: {} in tenant: {}", productId, currentTenant);

        List<StockMovement> movements = stockMovementRepository.findByProductId(productId);
        return movements.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get recent stock movements for dashboard
     */
    public List<StockMovementResponseDTO> getRecentMovements(int limit) {
        String currentTenant = TenantContext.getCurrentTenant();
        log.debug("📋 Fetching recent {} stock movements for tenant: {}", limit, currentTenant);

        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StockMovement> movements = stockMovementRepository.findRecent(pageable);

        return movements.getContent().stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get stock movements by date range
     */
    public List<StockMovementResponseDTO> getStockMovementsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        String currentTenant = TenantContext.getCurrentTenant();
        log.debug("📋 Fetching stock movements between {} and {} for tenant: {}", startDate, endDate, currentTenant);

        List<StockMovement> movements = stockMovementRepository.findByDateRange(startDate, endDate);
        return movements.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get stock movement statistics
     */
    public StockMovementStats getStockMovementStats() {
        String currentTenant = TenantContext.getCurrentTenant();
        log.debug("📊 Calculating stock movement statistics for tenant: {}", currentTenant);

        long totalMovements = stockMovementRepository.countTotal();
        long inMovements = stockMovementRepository.countByMovementType(StockMovement.MovementType.IN);
        long outMovements = stockMovementRepository.countByMovementType(StockMovement.MovementType.OUT);
        long adjustments = stockMovementRepository.countByMovementType(StockMovement.MovementType.ADJUSTMENT);

        return new StockMovementStats(totalMovements, inMovements, outMovements, adjustments);
    }

    /**
     * Calculate new stock based on movement type and quantity
     */
    private Integer calculateNewStock(Integer currentStock, StockMovement.MovementType movementType, Integer quantity) {
        return switch (movementType) {
            case IN, RETURN -> currentStock + quantity;
            case OUT, DAMAGED, EXPIRED -> currentStock - quantity;
            case ADJUSTMENT -> quantity; // For adjustments, quantity is the new stock level
            case TRANSFER -> currentStock; // Transfer might need special handling
        };
    }

    /**
     * Convert entity to response DTO
     */
    private StockMovementResponseDTO convertToResponseDTO(StockMovement movement) {
        StockMovementResponseDTO dto = StockMovementResponseDTO.fromEntity(movement);

        // Add username if available
        if (movement.getCreatedBy() != null) {
            appUserRepository.findById(movement.getCreatedBy())
                    .ifPresent(user -> dto.setCreatedByUsername(user.getUsername()));
        }

        return dto;
    }

    /**
     * Inner class for stock movement statistics
     */
    public static class StockMovementStats {
        private final long totalMovements;
        private final long inMovements;
        private final long outMovements;
        private final long adjustments;

        public StockMovementStats(long totalMovements, long inMovements, long outMovements, long adjustments) {
            this.totalMovements = totalMovements;
            this.inMovements = inMovements;
            this.outMovements = outMovements;
            this.adjustments = adjustments;
        }

        public long getTotalMovements() { return totalMovements; }
        public long getInMovements() { return inMovements; }
        public long getOutMovements() { return outMovements; }
        public long getAdjustments() { return adjustments; }
    }
}
