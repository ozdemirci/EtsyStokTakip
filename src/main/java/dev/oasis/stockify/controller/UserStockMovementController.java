package dev.oasis.stockify.controller;

import dev.oasis.stockify.dto.BulkStockMovementCreateDTO;
import dev.oasis.stockify.dto.StockMovementCreateDTO;
import dev.oasis.stockify.dto.StockMovementResponseDTO;
import dev.oasis.stockify.dto.ValidationErrorDTO;
import dev.oasis.stockify.service.StockMovementService;
import dev.oasis.stockify.config.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.List;

/**
 * Controller for user stock movement operations (read-only)
 */
@Controller
@RequestMapping("/user/stock-movements")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
@Slf4j
public class UserStockMovementController {

    private final StockMovementService stockMovementService;

    /**
     * Display stock movements page for users (read-only)
     */
    @GetMapping
    public String stockMovementsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        
        log.info("📋 User viewing stock movements page - Page: {}, Size: {}", page, size);
        
        try {
            Page<StockMovementResponseDTO> movements = stockMovementService.getAllStockMovements(page, size);
            StockMovementService.StockMovementStats stats = stockMovementService.getStockMovementStats();
            
            model.addAttribute("movements", movements);
            model.addAttribute("stats", stats);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", movements.getTotalPages());
            model.addAttribute("totalElements", movements.getTotalElements());
            
            return "user/stock-movements";
            
        } catch (Exception e) {
            log.error("❌ Error loading stock movements page for user: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to load stock movements: " + e.getMessage());
            return "user/stock-movements";
        }
    }

    /**
     * Get stock movements by product (AJAX) - read-only for users
     */
    @GetMapping("/product/{productId}")
    @ResponseBody
    public ResponseEntity<List<StockMovementResponseDTO>> getStockMovementsByProduct(@PathVariable Long productId) {
        log.info("📋 User fetching stock movements for product ID: {}", productId);
        
        try {
            List<StockMovementResponseDTO> movements = stockMovementService.getStockMovementsByProduct(productId);
            return ResponseEntity.ok(movements);
            
        } catch (Exception e) {
            log.error("❌ Error fetching stock movements for product {} by user: {}", productId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get stock movement statistics (AJAX) - read-only for users
     */
    @GetMapping("/stats")
    @ResponseBody
    public ResponseEntity<StockMovementService.StockMovementStats> getStockMovementStats() {
        log.info("📊 User fetching stock movement statistics");

        try {
            StockMovementService.StockMovementStats stats = stockMovementService.getStockMovementStats();
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("❌ Error fetching stock movement statistics for user: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create new stock movement (AJAX)
     */
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<?> createStockMovement(@RequestBody StockMovementCreateDTO dto) {
        log.info("🔄 User creating stock movement for product ID: {}", dto.getProductId());
        try {
            StockMovementResponseDTO response = stockMovementService.createStockMovement(dto);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error creating stock movement for user: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/validate")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> validateMovement(@RequestBody StockMovementCreateDTO dto) {
        List<String> errors = stockMovementService.validateStockMovement(dto);
        return ResponseEntity.ok(java.util.Map.of(
                "valid", errors.isEmpty(),
                "errors", errors
        ));
    }

    /**
     * Bulk stock movement creation
     */
    @PostMapping("/bulk-create")
    @ResponseBody
    public ResponseEntity<?> createBulkMovements(@RequestBody BulkStockMovementCreateDTO bulkDto) {
        try {
            List<StockMovementResponseDTO> response = stockMovementService.createBulkStockMovements(bulkDto);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error bulk creating stock movement for user: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/bulk-validate")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> validateBulk(@RequestBody BulkStockMovementCreateDTO bulkDto) {
        List<ValidationErrorDTO> errors = stockMovementService.validateBulkStockMovements(bulkDto);
        return ResponseEntity.ok(java.util.Map.of(
                "valid", errors.isEmpty(),
                "errors", errors
        ));
    }

    @PostMapping("/upload-csv")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> uploadCsv(@RequestParam("file") MultipartFile file) {
        try {
            stockMovementService.importFromCsv(file);
            return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Başarıyla yüklendi"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", "Yükleme hatası: " + e.getMessage()));
        }
    }

    @PostMapping("/validate-csv")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> validateCsv(@RequestParam("file") MultipartFile file) {
        try {
            List<ValidationErrorDTO> errors = stockMovementService.validateCsv(file);
            return ResponseEntity.ok(java.util.Map.of(
                    "valid", errors.isEmpty(),
                    "errors", errors
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("valid", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/bulk/today")
    @ResponseBody
    public List<StockMovementResponseDTO> getTodaysMovements() {
        return stockMovementService.getTodaysStockMovements();
    }

    @GetMapping("/bulk/by-date")
    @ResponseBody
    public List<StockMovementResponseDTO> getMovementsByDateRange(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate end) {
        return stockMovementService.getStockMovementsByDateRange(start, end);
    }

     

    private String getCurrentTenantId(HttpServletRequest request) {
        String currentTenantId = TenantContext.getCurrentTenant();
        if (currentTenantId != null && !currentTenantId.isEmpty()) {
            return currentTenantId;
        }

        currentTenantId = (String) request.getSession().getAttribute("tenantId");
        if (currentTenantId != null && !currentTenantId.isEmpty()) {
            return currentTenantId;
        }

        currentTenantId = request.getHeader("X-TenantId");
        if (currentTenantId != null && !currentTenantId.isEmpty()) {
            return currentTenantId.toLowerCase();
        }

        currentTenantId = request.getParameter("tenant_id");
        if (currentTenantId != null && !currentTenantId.isEmpty()) {
            return currentTenantId.toLowerCase();
        }

        return "public";
    }
}
