package dev.oasis.stockify.controller;

import dev.oasis.stockify.dto.BulkStockMovementCreateDTO;
import dev.oasis.stockify.dto.StockMovementCreateDTO;
import dev.oasis.stockify.dto.StockMovementResponseDTO;
import dev.oasis.stockify.model.StockMovement;
import dev.oasis.stockify.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller for stock movement operations
 */
@Controller
@RequestMapping("/admin/stock-movements")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class StockMovementController {

    private final StockMovementService stockMovementService;

    /**
     * Display stock movements page
     */
    @GetMapping
    public String stockMovementsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        
        log.info("📋 Displaying stock movements page - Page: {}, Size: {}", page, size);
        
        try {
            Page<StockMovementResponseDTO> movements = stockMovementService.getAllStockMovements(page, size);
            StockMovementService.StockMovementStats stats = stockMovementService.getStockMovementStats();
            
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
     * Create new stock movement (AJAX)
     */
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<?> createStockMovement(@RequestBody StockMovementCreateDTO dto) {
        log.info("🔄 Creating stock movement for product ID: {}", dto.getProductId());
        
        try {
            StockMovementResponseDTO response = stockMovementService.createStockMovement(dto);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error creating stock movement: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Get stock movements by product (AJAX)
     */
    @GetMapping("/product/{productId}")
    @ResponseBody
    public ResponseEntity<List<StockMovementResponseDTO>> getStockMovementsByProduct(@PathVariable Long productId) {
        log.info("📋 Fetching stock movements for product ID: {}", productId);
        
        try {
            List<StockMovementResponseDTO> movements = stockMovementService.getStockMovementsByProduct(productId);
            return ResponseEntity.ok(movements);
            
        } catch (Exception e) {
            log.error("❌ Error fetching stock movements for product {}: {}", productId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get stock movement statistics (AJAX)
     */
    @GetMapping("/stats")
    @ResponseBody
    public ResponseEntity<StockMovementService.StockMovementStats> getStockMovementStats() {
        log.info("📊 Fetching stock movement statistics");
        
        try {
            StockMovementService.StockMovementStats stats = stockMovementService.getStockMovementStats();
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("❌ Error fetching stock movement statistics: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }


    /**-------------------------------------------------------------------------------------
     * Toplu stok hareketi
     */

    @GetMapping("/bulk")
    public String bulk(Model model) {
        return "admin/bulk-stock-movements";
    }

    /**
     * Toplu stok hareketi girişi
     */
    @PostMapping("/bulk-create")
    @ResponseBody
    public ResponseEntity<?> createBulkMovements(@RequestBody BulkStockMovementCreateDTO bulkDto) {
        try {
        List<StockMovementResponseDTO> response = stockMovementService.createBulkStockMovements(bulkDto);
        return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error bulk creating stock movement: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }



        
    }

 /**
     * Bugünün hareketlerini getirir
     */
    @GetMapping("/bulk/today")
    public List<StockMovementResponseDTO> getTodaysMovements() {
        return stockMovementService.getTodaysStockMovements();
    }

    /**
     * Tarih aralığına göre hareket getirir
     */
    @GetMapping("/bulk/by-date")
    public List<StockMovementResponseDTO> getMovementsByDateRange(
            @RequestParam("start")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return stockMovementService.getStockMovementsByDateRange(start, end);
    }

    
    


}
