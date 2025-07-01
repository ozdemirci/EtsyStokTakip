package dev.oasis.stockify.controller;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.ProductResponseDTO;
import dev.oasis.stockify.dto.ProductCategoryResponseDTO;
import dev.oasis.stockify.service.ProductService;
import dev.oasis.stockify.service.ProductCategoryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * User controller for product viewing operations
 * Provides read-only product access for regular users
 */
@Slf4j
@Controller
@RequestMapping("/user/products")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class UserProductController {

    private final ProductService productService;
    private final ProductCategoryService categoryService;

    /**
     * Ensure tenant context is set for each request
     */
    @ModelAttribute
    public void setupTenantContext(HttpServletRequest request) {
        String tenantId = getCurrentTenantId(request);
        TenantContext.setCurrentTenant(tenantId);
        log.debug("Set tenant context to: {}", tenantId);
    }

    /**
     * Get current tenant ID from various sources
     */
    private String getCurrentTenantId(HttpServletRequest request) {
        // 1) Try context
        String tenantId = TenantContext.getCurrentTenant();
        log.debug("1. Tenant from context: '{}'", tenantId);
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId.toLowerCase();
        }

        // 2) Try session without creating a new one
        var session = request.getSession(false);
        if (session != null) {
            tenantId = (String) session.getAttribute("tenantId");
            log.debug("2. Tenant from session: '{}'", tenantId);
            if (tenantId != null && !tenantId.isBlank()) {
                tenantId = tenantId.toLowerCase();
                TenantContext.setCurrentTenant(tenantId);
                return tenantId;
            }
        }

        // 3) Try header (support both header names)
        tenantId = request.getHeader("X-TenantId");
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = request.getHeader("X-Tenant-ID");
        }
        log.debug("3. Tenant from header: '{}'", tenantId);
        if (tenantId != null && !tenantId.isBlank()) {
            tenantId = tenantId.toLowerCase();
            TenantContext.setCurrentTenant(tenantId);
            return tenantId;
        }

        // 4) Try request parameter
        tenantId = request.getParameter("tenant_id");
        log.debug("4. Tenant from parameter: '{}'", tenantId);
        if (tenantId != null && !tenantId.isBlank()) {
            tenantId = tenantId.toLowerCase();
            TenantContext.setCurrentTenant(tenantId);
            return tenantId;
        }

        log.warn("⚠️ Could not determine tenant ID, using default 'public'");
        return "public";
    }

    /**
     * Validate and sanitize sort field to prevent injection attacks
     * Only allow predefined sort fields that exist in ProductResponseDTO
     */
    private String validateSortField(String sortBy) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return "title"; // Default sort
        }
        
        // Define allowed sort fields based on ProductResponseDTO fields
        return switch (sortBy.toLowerCase()) {
            case "id" -> "id";
            case "title" -> "title";
            case "description" -> "description";
            case "sku" -> "sku";
            case "category" -> "category";
            case "price" -> "price";
            case "stocklevel" -> "stockLevel";
            case "lowstockthreshold" -> "lowStockThreshold";
            case "etsyproductid" -> "etsyProductId";
            case "isactive" -> "isActive";
            case "isfeatured" -> "isFeatured";
            case "createdat" -> "createdAt";
            case "updatedat" -> "updatedAt";
            case "createdby" -> "createdBy";
            case "updatedby" -> "updatedBy";
            default -> {
                log.warn("⚠️ Invalid sort field '{}', using default 'title'", sortBy);
                yield "title";
            }
        };
    }

    /**
     * Display paginated and searchable list of products (read-only for users)
     */
    @GetMapping
    public String listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "title") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String tab,
            HttpServletRequest request,
            Model model,
            Authentication authentication) {
        
        String tenantId = getCurrentTenantId(request);
        log.info("📦 User viewing products for tenant: {}", tenantId);

        // Validate and sanitize sortBy parameter for security
        String validatedSortBy = validateSortField(sortBy);
        log.debug("🔄 Sort field validated: {} -> {}", sortBy, validatedSortBy);

        // Create sort object
        Sort sort = Sort.by(sortDir.equals("desc") ? 
            Sort.Direction.DESC : Sort.Direction.ASC, validatedSortBy);
        
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProductResponseDTO> products;
        if (search != null && !search.trim().isEmpty()) {
            products = productService.searchProducts(search, pageable);
            model.addAttribute("search", search);
            log.debug("🔍 User searching products with term: {} for tenant: {}", search, tenantId);
        } else {
            products = productService.getProductsPage(pageable);
            log.debug("📋 User listing all products for tenant: {}", tenantId);
        }        // Get counts for badges
        List<ProductResponseDTO> allProducts = productService.getAllProducts();
        long totalProducts = allProducts.size();
        long lowStockCount = allProducts.stream()
            .filter(p -> p.isLowStock()) // Use DTO method for accurate low stock detection
            .count();

        // Get categories for the categories tab
        List<ProductCategoryResponseDTO> categories;
        try {
            categories = categoryService.getAllCategories();
            log.debug("📋 Found {} categories for user view, tenant: {}", categories.size(), tenantId);
        } catch (Exception e) {
            log.error("❌ Failed to load categories for user view, tenant: {}", tenantId, e);
            categories = List.of(); // Empty list as fallback
        }

        // Add attributes to model
        model.addAttribute("products", products);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("sortBy", validatedSortBy); // Use validated sort field
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("totalPages", products.getTotalPages());
        model.addAttribute("totalElements", products.getTotalElements());
        model.addAttribute("tenantId", tenantId);
        model.addAttribute("totalProducts", totalProducts);
        model.addAttribute("lowStockCount", lowStockCount);
        model.addAttribute("categories", categories); // For categories tab
        model.addAttribute("activeTab", tab); // For JavaScript to know which tab to activate
        model.addAttribute("currentUser", authentication.getName());



        log.debug("📊 Found {} total products, {} low stock for tenant: {}", 
            totalProducts, lowStockCount, tenantId);

        log.debug("📊 Found {} products for tenant: {}", products.getTotalElements(), tenantId);
        return "user/products";
    }

    /**
     * Redirect to products page with low-stock tab active
     */
    @GetMapping("/low-stock")
    public String getLowStockProducts(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        String tenantId = getCurrentTenantId(request);
        log.info("⚠️ Redirecting to products page with low-stock tab for tenant: {}", tenantId);
        
        // Add parameter to indicate low-stock tab should be active
        redirectAttributes.addAttribute("tab", "low-stock");
        return "redirect:/user/products";
    }

    /**
     * Get low stock products as JSON data for AJAX calls
     */
    @GetMapping(value = "/low-stock-data", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<ProductResponseDTO> getLowStockProductsData(HttpServletRequest request) {
        String tenantId = getCurrentTenantId(request);
        log.info("📊 Getting low stock products data for tenant: {}", tenantId);
        
        try {
            List<ProductResponseDTO> lowStockProducts = productService.getAllProducts()
                .stream()
                .filter(p -> p.isLowStock()) // Use DTO method for accurate low stock detection
                .toList();
                
            log.info("✅ Found {} low stock products for tenant: {}",
                lowStockProducts.size(), tenantId);
            return lowStockProducts;
        } catch (Exception e) {
            log.error("❌ Failed to get low stock products data for tenant: {}", tenantId, e);
            return List.of(); // Return empty list on error
        }
    }

    /**
     * View product details (read-only for users)
     */
    @GetMapping("/{id}")
    public String viewProduct(@PathVariable Long id,
                             HttpServletRequest request,
                             Model model) {
        String tenantId = getCurrentTenantId(request);
        log.info("👁️ User viewing product {} for tenant: {}", id, tenantId);
        try {
            Optional<ProductResponseDTO> productOpt = productService.getProductById(id);
            if (productOpt.isEmpty()) {
                log.error("❌ Product not found with ID: {} for tenant: {}", id, tenantId);
                model.addAttribute("errorMessage", "Product not found");
                return "redirect:/user/products";
            }
            
            ProductResponseDTO product = productOpt.get();
            model.addAttribute("product", product);
            model.addAttribute("tenantId", tenantId);
            model.addAttribute("isReadOnly", true);
            
            log.info("✅ Product {} loaded successfully for tenant: {}", id, tenantId);
            return "user/product-view";
            
        } catch (Exception e) {
            log.error("❌ Product not found with ID: {} for tenant: {}", id, tenantId, e);
            model.addAttribute("errorMessage", "Product not found: " + e.getMessage());
            return "redirect:/user/products";
        }
    }

    /**
     * Handle exceptions globally for this controller
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal server error");
        log.error("API error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Get all products as JSON for AJAX (id, title, sku)
     */
    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getAllProductsForApi(HttpServletRequest request) {
        try {
            String tenantId = getCurrentTenantId(request);
            log.info("🔗 [API] Getting all products for AJAX for tenant: {}", tenantId);

            List<ProductResponseDTO> products = productService.getAllProducts();
            List<Map<String, Object>> result = products.stream()
                .map(p -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", p.getId());
                    map.put("title", p.getTitle());
                    map.put("sku", p.getSku());
                    return map;
                })
                .toList();

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ [API] Failed to get products for AJAX: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }
}
