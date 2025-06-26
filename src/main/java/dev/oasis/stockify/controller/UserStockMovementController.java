package dev.oasis.stockify.controller;

import dev.oasis.stockify.dto.StockMovementResponseDTO;
import dev.oasis.stockify.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
}
