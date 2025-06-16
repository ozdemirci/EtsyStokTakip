package dev.oasis.stockify.controller;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.ProductCreateDTO;
import dev.oasis.stockify.dto.ProductResponseDTO;
import dev.oasis.stockify.exception.FileOperationException;
import dev.oasis.stockify.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.*;
import java.util.List;
import java.util.Optional;

/**
 * Admin controller for product management operations
 * Provides tenant-aware product management for admin users
 */
@Slf4j
@Controller
@RequestMapping("/admin/products")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminProductController {
      private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Get current tenant ID from various sources
     */
    private String getCurrentTenantId(HttpServletRequest request) {
        // Try header first
        String tenantId = request.getHeader("X-Tenant-ID");
        log.debug("🏢 Tenant from header: {}", tenantId);

        // Try session if header is empty
        if (tenantId == null || tenantId.trim().isEmpty()) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                tenantId = (String) session.getAttribute("tenantId");
                log.debug("🏢 Tenant from session: {}", tenantId);
            }
        }

        // Try TenantContext as fallback
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = TenantContext.getCurrentTenant();
            log.debug("🏢 Tenant from context: {}", tenantId);
        }

        // Use default tenant if still empty
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = "public";
            log.warn("⚠️ No tenant found, defaulting to: {}", tenantId);
        }

        log.info("🎯 Using tenant ID: {}", tenantId);
        return tenantId;
    }

    /**
     * Display paginated and searchable list of products
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
        log.info("📦 Listing products for tenant: {}", tenantId);

        // Create sort object
        Sort sort = Sort.by(sortDir.equals("desc") ? 
            Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        
        Pageable pageable = PageRequest.of(page, size, sort);
          Page<ProductResponseDTO> products;
        if (search != null && !search.trim().isEmpty()) {
            products = productService.searchProducts(search, pageable);
            model.addAttribute("search", search);
            log.debug("🔍 Searching products with term: {} for tenant: {}", search, tenantId);
        } else {
            products = productService.getProductsPage(pageable);
            log.debug("📋 Listing all products for tenant: {}", tenantId);
        }

        model.addAttribute("products", products);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("totalPages", products.getTotalPages());
        model.addAttribute("totalElements", products.getTotalElements());
        model.addAttribute("tenantId", tenantId);

        log.debug("📊 Found {} products for tenant: {}", products.getTotalElements(), tenantId);
        return "admin/products";
    }

    /**
     * Show form for adding a new product
     */
    @GetMapping("/add")
    public String showAddProductForm(HttpServletRequest request, Model model) {
        String tenantId = getCurrentTenantId(request);
        log.info("➕ Showing add product form for tenant: {}", tenantId);
        
        model.addAttribute("product", new ProductCreateDTO());
        model.addAttribute("tenantId", tenantId);
        return "admin/product-form";
    }

    /**
     * Handle product creation
     */
    @PostMapping
    public String createProduct(@ModelAttribute ProductCreateDTO productCreateDTO,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        String tenantId = getCurrentTenantId(request);
        log.info("💾 Creating product for tenant: {}", tenantId);
          try {
            ProductResponseDTO createdProduct = productService.saveProduct(productCreateDTO);
            redirectAttributes.addFlashAttribute("successMessage", 
                "Product '" + createdProduct.getTitle() + "' created successfully!");
            log.info("✅ Product created successfully: {} for tenant: {}", 
                createdProduct.getTitle(), tenantId);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Failed to create product: " + e.getMessage());
            log.error("❌ Failed to create product for tenant: {}", tenantId, e);
        }
        
        return "redirect:/admin/products";
    }

    /**
     * Show form for editing an existing product
     */
    @GetMapping("/{id}/edit")
    public String showEditProductForm(@PathVariable Long id,
                                     HttpServletRequest request,
                                     Model model,
                                     RedirectAttributes redirectAttributes) {
        String tenantId = getCurrentTenantId(request);
        log.info("✏️ Showing edit form for product ID: {} for tenant: {}", id, tenantId);
          try {
            Optional<ProductResponseDTO> productOpt = productService.getProductById(id);
            if (productOpt.isPresent()) {
                ProductResponseDTO product = productOpt.get();
                model.addAttribute("product", product);
                model.addAttribute("tenantId", tenantId);
                return "admin/product-form";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Product not found");
                return "redirect:/admin/products";
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Product not found: " + e.getMessage());
            log.error("❌ Product not found with ID: {} for tenant: {}", id, tenantId, e);
            return "redirect:/admin/products";
        }
    }

    /**
     * Handle product update
     */
    @PostMapping("/{id}")
    public String updateProduct(@PathVariable Long id,
                               @ModelAttribute ProductCreateDTO productCreateDTO,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        String tenantId = getCurrentTenantId(request);
        log.info("🔄 Updating product ID: {} for tenant: {}", id, tenantId);
        
        try {
            ProductResponseDTO updatedProduct = productService.updateProduct(id, productCreateDTO);
            redirectAttributes.addFlashAttribute("successMessage", 
                "Product '" + updatedProduct.getTitle() + "' updated successfully!");
            log.info("✅ Product updated successfully: {} for tenant: {}", 
                updatedProduct.getTitle(), tenantId);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Failed to update product: " + e.getMessage());
            log.error("❌ Failed to update product ID: {} for tenant: {}", id, tenantId, e);
        }
        
        return "redirect:/admin/products";
    }

    /**
     * Handle product deletion
     */
    @PostMapping("/{id}/delete")
    public String deleteProduct(@PathVariable Long id,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        String tenantId = getCurrentTenantId(request);
        log.info("🗑️ Deleting product ID: {} for tenant: {}", id, tenantId);
        
        try {
            productService.deleteProduct(id);
            redirectAttributes.addFlashAttribute("successMessage", 
                "Product deleted successfully!");
            log.info("✅ Product deleted successfully with ID: {} for tenant: {}", id, tenantId);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Failed to delete product: " + e.getMessage());
            log.error("❌ Failed to delete product ID: {} for tenant: {}", id, tenantId, e);
        }
        
        return "redirect:/admin/products";
    }

    /**
     * Download CSV template for product import
     */
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        log.info("📥 Downloading product import template");
        
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=product_import_template.csv");

        try (InputStream templateStream = getClass().getClassLoader()
                .getResourceAsStream("static/templates/product_import_template.csv")) {
            
            if (templateStream == null) {
                throw new FileOperationException("Template file not found");
            }
            
            StreamUtils.copy(templateStream, response.getOutputStream());
            log.info("✅ Template downloaded successfully");
        } catch (IOException e) {
            log.error("❌ Failed to download template", e);
            throw new FileOperationException("Failed to download template: " + e.getMessage());
        }
    }

    /**
     * Handle CSV file import
     */
    @PostMapping("/import")
    public String importProducts(@RequestParam("file") MultipartFile file,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        String tenantId = getCurrentTenantId(request);
        log.info("📤 Importing products from file: {} for tenant: {}", 
            file.getOriginalFilename(), tenantId);
        
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a file to import.");
            return "redirect:/admin/products";
        }        try {
            // TODO: Implement import functionality
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Import functionality not yet implemented.");
            // List<ProductResponseDTO> importedProducts = productImportExportService.importFromCsv(file);
            // redirectAttributes.addFlashAttribute("successMessage", 
            //     "Successfully imported " + importedProducts.size() + " products!");
            // log.info("✅ Successfully imported {} products for tenant: {}", 
            //     importedProducts.size(), tenantId);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Failed to import products: " + e.getMessage());
            log.error("❌ Failed to import products for tenant: {}", tenantId, e);
        }

        return "redirect:/admin/products";
    }

    /**
     * Export products to CSV
     */
    @GetMapping("/export")
    public void exportProducts(HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        String tenantId = getCurrentTenantId(request);
        log.info("📤 Exporting products to CSV for tenant: {}", tenantId);
        
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=products_" + tenantId + ".csv");        try {
            List<ProductResponseDTO> products = productService.getAllProducts();
            
            // TODO: Implement export functionality
            response.getWriter().write("Export functionality not yet implemented");
            
            // productImportExportService.exportToCsv(products, response.getWriter());
            log.info("✅ Export requested for {} products for tenant: {}", 
                products.size(), tenantId);
        } catch (Exception e) {
            log.error("❌ Failed to export products for tenant: {}", tenantId, e);
            throw new FileOperationException("Failed to export products: " + e.getMessage());
        }
    }

    /**
     * Get low stock products
     */
    @GetMapping("/low-stock")
    public String getLowStockProducts(HttpServletRequest request, Model model) {
        String tenantId = getCurrentTenantId(request);
        log.info("⚠️ Getting low stock products for tenant: {}", tenantId);        try {
            // TODO: Implement low stock functionality with proper repository method
            List<ProductResponseDTO> lowStockProducts = productService.getAllProducts()
                .stream()
                .filter(p -> p.getStockLevel() <= p.getLowStockThreshold())
                .toList();
                
            model.addAttribute("products", lowStockProducts);
            model.addAttribute("tenantId", tenantId);
            model.addAttribute("isLowStockView", true);
            log.info("📊 Found {} low stock products for tenant: {}", 
                lowStockProducts.size(), tenantId);
            return "admin/products";
        } catch (Exception e) {
            log.error("❌ Failed to get low stock products for tenant: {}", tenantId, e);
            model.addAttribute("errorMessage", "Failed to load low stock products: " + e.getMessage());
            return "admin/products";
        }
    }
}
