package dev.oasis.stockify.controller;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.ProductResponseDTO;
import dev.oasis.stockify.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * User controller for product viewing operations
 * Provides read-only product access for regular users
 */
@Slf4j
@Controller
@RequestMapping("/user/products")
@PreAuthorize("hasRole('USER')")
public class UserProductController {

    private final ProductService productService;

    public UserProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Get current tenant ID from various sources
     */
    private String getCurrentTenantId(HttpServletRequest request) {
        // First, try to get from current tenant context
        String tenantId = TenantContext.getCurrentTenant();
        log.debug("🏢 Tenant from context: {}", tenantId);
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            tenantId = tenantId.trim().toLowerCase();
            log.debug("🎯 Using tenant from context: {}", tenantId);
            return tenantId;
        }

        // Try to get from session (stored during login)
        tenantId = (String) request.getSession().getAttribute("tenantId");
        log.debug("🏢 Tenant from session: {}", tenantId);
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            tenantId = tenantId.trim().toLowerCase();
            TenantContext.setCurrentTenant(tenantId);
            log.debug("🎯 Using tenant from session: {}", tenantId);
            return tenantId;
        }

        // Try to get from header
        tenantId = request.getHeader("X-TenantId");
        log.debug("🏢 Tenant from header: {}", tenantId);
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            tenantId = tenantId.trim().toLowerCase();
            TenantContext.setCurrentTenant(tenantId);
            log.debug("🎯 Using tenant from header: {}", tenantId);
            return tenantId;
        }

        // Try to get from parameter
        tenantId = request.getParameter("tenant_id");
        log.debug("🏢 Tenant from parameter: {}", tenantId);
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            tenantId = tenantId.trim().toLowerCase();
            TenantContext.setCurrentTenant(tenantId);
            log.debug("🎯 Using tenant from parameter: {}", tenantId);
            return tenantId;
        }

        // Try to get from current context one more time
        tenantId = TenantContext.getCurrentTenant();
        log.debug("🏢 Tenant from context: {}", tenantId);

        // Use default tenant if still empty
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = "public";
            log.warn("⚠️ No tenant found, defaulting to: {}", tenantId);
        }

        log.info("🎯 Using tenant ID: {}", tenantId);
        return tenantId;
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
            HttpServletRequest request,
            Model model) {
        
        String tenantId = getCurrentTenantId(request);
        log.info("📦 User viewing products for tenant: {}", tenantId);

        // Create sort object
        Sort sort = Sort.by(sortDir.equals("desc") ? 
            Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProductResponseDTO> products;
        if (search != null && !search.trim().isEmpty()) {
            products = productService.searchProducts(search, pageable);
            model.addAttribute("search", search);
            log.debug("🔍 User searching products with term: {} for tenant: {}", search, tenantId);
        } else {
            products = productService.getProductsPage(pageable);
            log.debug("📋 User listing all products for tenant: {}", tenantId);
        }

        // Add attributes to model
        model.addAttribute("products", products);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("totalPages", products.getTotalPages());
        model.addAttribute("totalElements", products.getTotalElements());
        model.addAttribute("tenantId", tenantId);

        log.debug("📊 Found {} products for tenant: {}", products.getTotalElements(), tenantId);
        return "user/products";
    }

    /**
     * Get low stock products (read-only for users)
     */
    @GetMapping("/low-stock")
    public String getLowStockProducts(HttpServletRequest request, Model model) {
        String tenantId = getCurrentTenantId(request);
        log.info("⚠️ User checking low stock products for tenant: {}", tenantId);        try {
            // For user view, we'll show all products and filter for low stock on the frontend
            // Since getLowStockProducts(Pageable) doesn't exist, we'll use getProductsPage and filter
            Pageable pageable = PageRequest.of(0, 100, Sort.by("quantity").ascending());
            Page<ProductResponseDTO> allProducts = productService.getProductsPage(pageable);
              // Filter for low stock products using isLowStock() method
            List<ProductResponseDTO> lowStockProducts = allProducts.getContent().stream()
                .filter(ProductResponseDTO::isLowStock)
                .collect(Collectors.toList());
            
            // Create a new Page object for low stock products
            Page<ProductResponseDTO> lowStockPage = new PageImpl<>(
                lowStockProducts, pageable, lowStockProducts.size());

            model.addAttribute("products", lowStockPage);
            model.addAttribute("currentPage", 0);
            model.addAttribute("pageSize", 20);
            model.addAttribute("sortBy", "quantity");
            model.addAttribute("sortDir", "asc");
            model.addAttribute("totalPages", lowStockPage.getTotalPages());
            model.addAttribute("totalElements", lowStockPage.getTotalElements());
            model.addAttribute("tenantId", tenantId);
            model.addAttribute("isLowStockView", true);            log.info("📊 Found {} low stock products for tenant: {}", 
                lowStockPage.getTotalElements(), tenantId);
            
            return "user/products";
            
        } catch (Exception e) {
            log.error("❌ Failed to get low stock products for tenant: {}", tenantId, e);
            model.addAttribute("errorMessage", "Failed to load low stock products: " + e.getMessage());
            return "user/products";
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
        log.info("👁️ User viewing product {} for tenant: {}", id, tenantId);        try {
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
}
