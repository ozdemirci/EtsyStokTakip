package dev.oasis.stockify.controller;

import dev.oasis.stockify.dto.BulkStockMovementCreateDTO;
import dev.oasis.stockify.dto.StockMovementCreateDTO;
import dev.oasis.stockify.dto.StockMovementResponseDTO;
import dev.oasis.stockify.dto.ValidationErrorDTO;
import dev.oasis.stockify.model.StockMovement;
import dev.oasis.stockify.service.StockMovementService;
import dev.oasis.stockify.util.ControllerTenantUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controller for stock movement operations
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/stock-movements")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
@Slf4j
public class StockMovementController {

    private final StockMovementService stockMovementService;
    private final ControllerTenantUtil tenantResolutionUtil;

     

    @ModelAttribute
    public void setupTenantContext(HttpServletRequest request) {
        tenantResolutionUtil.setupTenantContext(request);
    }

    /**
     * Display stock movements page
     */
    @GetMapping
    public String stockMovementsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request,
            Authentication authentication,
            Model model) {

        log.info("📋 Displaying stock movements page - Page: {}, Size: {}", page, size);

        try {
            String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
            log.info("📋 Using tenant: {}", tenantId);
            
            Page<StockMovementResponseDTO> movements = stockMovementService.getAllStockMovements(page, size);
            StockMovementService.StockMovementStats stats = stockMovementService.getStockMovementStats();

            model.addAttribute("currentTenantId", tenantId);
            model.addAttribute("movements", movements);
            model.addAttribute("stats", stats);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", movements.getTotalPages());
            model.addAttribute("totalElements", movements.getTotalElements());
            model.addAttribute("movementTypes", StockMovement.MovementType.values());

            return "admin/stock-movements";

        } catch (Exception e) {
            log.error("❌ Error loading stock movements page: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to load stock movements: " + e.getMessage());
            return "admin/stock-movements";
        }
    }

    /**
     * Filter stock movements
     */
    @GetMapping("/filter")
    public String filterStockMovements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String movementType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest request,
            Authentication authentication,
            Model model) {

        log.info("🔍 Filtering stock movements - Page: {}, Size: {}, ProductId: {}, Type: {}, StartDate: {}, EndDate: {}",
                page, size, productId, movementType, startDate, endDate);

        try {
            String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
            log.info("🔍 Using tenant: {}", tenantId);
            
            Page<StockMovementResponseDTO> movements;
            if (productId != null || movementType != null || startDate != null || endDate != null) {
                // Convert to appropriate format or handle filters in the frontend
                // For now, just get all movements
                movements = stockMovementService.getAllStockMovements(page, size);
            } else {
                movements = stockMovementService.getAllStockMovements(page, size);
            }
            StockMovementService.StockMovementStats stats = stockMovementService.getStockMovementStats();

            model.addAttribute("currentTenantId", tenantId);
            model.addAttribute("movements", movements);
            model.addAttribute("stats", stats);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", movements.getTotalPages());
            model.addAttribute("totalElements", movements.getTotalElements());
            model.addAttribute("movementTypes", StockMovement.MovementType.values());
            model.addAttribute("filterActive", true);
            model.addAttribute("productId", productId);
            model.addAttribute("movementType", movementType);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);

            return "admin/stock-movements";

        } catch (Exception e) {
            log.error("❌ Error filtering stock movements: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to filter stock movements: " + e.getMessage());
            return "admin/stock-movements";
        }
    }

    /**
     * Create stock movement
     */
    @PostMapping
    @ResponseBody
    public ResponseEntity<?> createStockMovement(
            @RequestBody StockMovementCreateDTO movementCreateDTO,
            HttpServletRequest request,
            Authentication authentication) {

        log.info("➕ Creating stock movement for product: {}, quantity: {}, type: {}",
                movementCreateDTO.getProductId(), movementCreateDTO.getQuantity(), movementCreateDTO.getMovementType());

        try {
            String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
            log.info("➕ Using tenant: {}", tenantId);
            
            StockMovementResponseDTO createdMovement = stockMovementService.createStockMovement(movementCreateDTO);
            return ResponseEntity.ok(createdMovement);
        } catch (Exception e) {
            log.error("❌ Error creating stock movement: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                    new ValidationErrorDTO(0, "Failed to create stock movement: " + e.getMessage())
            );
        }
    }

    /**
     * Handle bulk stock movement upload
     */
    @PostMapping("/bulk-upload")
    @ResponseBody
    public ResponseEntity<?> bulkStockMovementUpload(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request,
            Authentication authentication) {

        log.info("📤 Processing bulk stock movement upload - File: {}, Size: {}KB",
                file.getOriginalFilename(), file.getSize() / 1024);

        try {
            String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
            log.info("📤 Using tenant: {}", tenantId);
            
            int processedCount = stockMovementService.importFromCsv(file);
            List<StockMovementResponseDTO> results = stockMovementService.getRecentMovements(processedCount);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Successfully processed " + results.size() + " stock movements",
                    "movements", results
            ));
        } catch (Exception e) {
            log.error("❌ Error processing bulk stock movement upload: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to process bulk upload: " + e.getMessage()
            ));
        }
    }

    /**
     * Process bulk stock movement data
     */
    @PostMapping("/bulk")
    @ResponseBody
    public ResponseEntity<?> processBulkStockMovements(
            @RequestBody List<BulkStockMovementCreateDTO> movements,
            HttpServletRequest request,
            Authentication authentication) {

        log.info("📦 Processing {} bulk stock movements", movements.size());

        try {
            String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
            log.info("📦 Using tenant: {}", tenantId);
            
            List<StockMovementResponseDTO> results = new java.util.ArrayList<>();
            for (BulkStockMovementCreateDTO bulkMovement : movements) {
                List<StockMovementResponseDTO> movementResults = stockMovementService.createBulkStockMovements(bulkMovement);
                results.addAll(movementResults);
            }
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Successfully processed " + results.size() + " stock movements",
                    "movements", results
            ));
        } catch (Exception e) {
            log.error("❌ Error processing bulk stock movements: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to process bulk movements: " + e.getMessage()
            ));
        }
    }
}
