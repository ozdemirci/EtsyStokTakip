package dev.oasis.stockify.service;

import dev.oasis.stockify.dto.BulkStockMovementCreateDTO;
import dev.oasis.stockify.dto.StockMovementCreateDTO;
import dev.oasis.stockify.dto.StockMovementResponseDTO;
import dev.oasis.stockify.dto.ValidationErrorDTO;
import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.model.StockMovement;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.repository.ProductRepository;
import dev.oasis.stockify.repository.StockMovementRepository;
import dev.oasis.stockify.util.ServiceTenantUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.opencsv.CSVReader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
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
    private final StockNotificationService stockNotificationService;
    private final ServiceTenantUtil serviceTenantUtil;

    /**
     * Create a new stock movement
     */
    @Transactional
    public StockMovementResponseDTO createStockMovement(StockMovementCreateDTO dto) {
        String currentTenant = serviceTenantUtil.getCurrentTenant();
        log.info("🔄 Creating stock movement for product ID: {} in tenant: {}", dto.getProductId(), currentTenant);

        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + dto.getProductId()));

        Integer previousStock = product.getStockLevel();
        Integer newStock = calculateNewStock(previousStock, dto.getMovementType(), dto.getQuantity());

        // Validate new stock level
        if (newStock < 0) {
            throw new IllegalArgumentException(
                    "Stock level cannot be negative. Current: " + previousStock + ", Change: " + dto.getQuantity());
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

        //  setCreatedBy
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            // Kullanıcı adını veya ID'sini al
            String username = authentication.getName();
            // Kullanıcıyı veritabanından bul ve ID'sini ata
            AppUser user = appUserRepository.findByUsername(username).orElse(null);
            if (user != null) {
                movement.setCreatedBy(user.getId());
            }
        }        

        // Save movement record
        StockMovement savedMovement = stockMovementRepository.save(movement);
        

        // Update product stock level
        product.setStockLevel(newStock);
        productRepository.save(product);
        stockNotificationService.checkAndCreateLowStockNotification(product);

        log.info(
                "✅ Stock movement created - Product: {}, Type: {}, Quantity: {}, Previous: {} -> New: {} for tenant: {}",
                product.getTitle(), dto.getMovementType(), dto.getQuantity(), previousStock, newStock, currentTenant);

        return convertToResponseDTO(savedMovement);
    }

    /**
     * Get all stock movements with pagination
     */
    public Page<StockMovementResponseDTO> getAllStockMovements(int page, int size) {
        String currentTenant = serviceTenantUtil.getCurrentTenant();
        log.debug("📋 Fetching stock movements for tenant: {} - Page: {}, Size: {}", currentTenant, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StockMovement> movements = stockMovementRepository.findRecent(pageable);

        return movements.map(this::convertToResponseDTO);
    }

    public Page<StockMovementResponseDTO> getStockMovements(int page,
                                                            int size,
                                                            String sortBy,
                                                            String sortDir,
                                                            String search,
                                                            StockMovement.MovementType type) {
        String tenant = serviceTenantUtil.getCurrentTenant();
        log.debug("📋 Fetching stock movements for tenant: {} page {} size {} sortBy {} sortDir {} search '{}' type {}",
                tenant, page, size, sortBy, sortDir, search, type);

        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = switch (sortBy) {
            case "productName" -> "product.title";
            case "type" -> "movementType";
            case "quantity" -> "quantity";
            default -> "createdAt";
        };

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));
        Page<StockMovement> pageResult = stockMovementRepository.searchMovements(
                (search != null && !search.isBlank()) ? search : null,
                type,
                pageable);

        return pageResult.map(this::convertToResponseDTO);
    }

    /**
     * Get stock movements by product ID
     */
    public List<StockMovementResponseDTO> getStockMovementsByProduct(Long productId) {
        String currentTenant = serviceTenantUtil.getCurrentTenant();
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
        String currentTenant = serviceTenantUtil.getCurrentTenant();
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
        String currentTenant = serviceTenantUtil.getCurrentTenant();
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
        String currentTenant = serviceTenantUtil.getCurrentTenant();
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

        public long getTotalMovements() {
            return totalMovements;
        }

        public long getInMovements() {
            return inMovements;
        }

        public long getOutMovements() {
            return outMovements;
        }

        public long getAdjustments() {
            return adjustments;
        }
    }

    /**
     * Get today's stock movements
     */
    public List<StockMovementResponseDTO> getTodaysStockMovements() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        List<StockMovement> movements = stockMovementRepository
                .findByCreatedAtBetweenOrderByCreatedAtDesc(startOfDay, endOfDay);

        return movements.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get stock movements by date range (inclusive)
     */
    public List<StockMovementResponseDTO> getStockMovementsByDateRange(LocalDate start, LocalDate end) {
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        List<StockMovement> movements = stockMovementRepository
                .findByCreatedAtBetweenOrderByCreatedAtDesc(startDateTime, endDateTime);

        return movements.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Toplu hareket oluşturur
     */
    @Transactional
    public List<StockMovementResponseDTO> createBulkStockMovements(BulkStockMovementCreateDTO bulkDto) {
        return bulkDto.getMovements().stream()
                .map(dto -> {
                    StockMovementResponseDTO response = createStockMovement(dto);
                    productRepository.findById(dto.getProductId())
                            .ifPresent(stockNotificationService::checkAndCreateLowStockNotification);
                    return response;
                })
                .collect(Collectors.toList());
    }

    public List<ValidationErrorDTO> validateBulkStockMovements(BulkStockMovementCreateDTO bulkDto) {
        List<ValidationErrorDTO> errors = new ArrayList<>();
        List<StockMovementCreateDTO> list = bulkDto.getMovements();
        for (int i = 0; i < list.size(); i++) {
            List<String> val = validateStockMovement(list.get(i));
            if (!val.isEmpty()) {
                errors.add(new ValidationErrorDTO(i, String.join("; ", val)));
            }
        }
        return errors;
    }

    public List<String> validateStockMovement(StockMovementCreateDTO dto) {
        List<String> errors = new ArrayList<>();
        if (dto.getProductId() == null) {
            errors.add("Product ID is required");
            return errors;
        }
        Product product = productRepository.findById(dto.getProductId()).orElse(null);
        if (product == null) {
            errors.add("Product not found: " + dto.getProductId());
            return errors;
        }
        if (dto.getMovementType() == null) {
            errors.add("Movement type is required");
        }
        if (dto.getQuantity() == null) {
            errors.add("Quantity is required");
        } else {
            Integer newStock = calculateNewStock(product.getStockLevel(), dto.getMovementType(), dto.getQuantity());
            if (newStock < 0) {
                errors.add("Negative resulting stock for product " + product.getTitle());
            }
        }
        return errors;
    }

    public List<ValidationErrorDTO> validateCsv(MultipartFile file) throws IOException, com.opencsv.exceptions.CsvValidationException {
        List<ValidationErrorDTO> errors = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            String[] nextLine;
            int index = 0;
            reader.readNext();
            while ((nextLine = reader.readNext()) != null) {
                StockMovementCreateDTO dto = new StockMovementCreateDTO();
                dto.setProductId(Long.parseLong(nextLine[0]));
                dto.setMovementType(StockMovement.MovementType.valueOf(nextLine[1]));
                dto.setQuantity(Integer.parseInt(nextLine[2]));
                dto.setReferenceId(nextLine.length > 3 ? nextLine[3] : null);
                List<String> val = validateStockMovement(dto);
                if (!val.isEmpty()) {
                    errors.add(new ValidationErrorDTO(index, String.join("; ", val)));
                }
                index++;
            }
        }
        return errors;
    }

    public int importFromCsv(MultipartFile file) throws IOException, com.opencsv.exceptions.CsvValidationException {
        int count = 0;
        try (CSVReader reader = new CSVReader(new java.io.InputStreamReader(file.getInputStream()))) {
            String[] nextLine;
            // Başlık satırını atla
            reader.readNext();
            while ((nextLine = reader.readNext()) != null) {
                // Örnek: [productId, movementType, quantity, notes]
                StockMovementCreateDTO dto = new StockMovementCreateDTO();
                dto.setProductId(Long.parseLong(nextLine[0]));
                dto.setMovementType(StockMovement.MovementType.valueOf(nextLine[1]));
                dto.setQuantity(Integer.parseInt(nextLine[2]));
                dto.setNotes(nextLine.length > 3 ? nextLine[3] : null);
                // Gerekirse diğer alanlar
                createStockMovement(dto);
                count++;
            }
        }
        return count;
    }

    /**
     * Get all stock movements for the current user (for user dashboard/table)
     */
    public List<StockMovementResponseDTO> getMovementsForUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new ArrayList<>();
        }
        String username = authentication.getName();
        AppUser user = appUserRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return new ArrayList<>();
        }
        // Fetch all products for this user (if needed, or fetch all movements created by this user)
        List<StockMovement> movements = stockMovementRepository.findByCreatedBy(user.getId());
        return movements.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }
}
