package dev.oasis.stockify.controller;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.ProductCreateDTO;
import dev.oasis.stockify.dto.ProductResponseDTO;
import dev.oasis.stockify.dto.ProductCategoryResponseDTO;
import dev.oasis.stockify.dto.ProductCategoryCreateDTO;
import dev.oasis.stockify.dto.QuickRestockRequestDTO;
import dev.oasis.stockify.dto.QuickRestockResponseDTO;
import dev.oasis.stockify.exception.FileOperationException;
import dev.oasis.stockify.service.ProductService;
import dev.oasis.stockify.service.ProductCategoryService;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.model.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.*;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admin controller for product management operations
 * Provides tenant-aware product management for admin users
 */
@Slf4j
@Controller
@RequestMapping("/admin/products")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminProductController {      private final ProductService productService;
      private final ProductCategoryService categoryService;
      private final AppUserRepository appUserRepository;

    /**
     * Test endpoint for JavaScript debugging
     */
    @GetMapping("/test-js")
    public String testJavaScript() {
        log.info("📊 Serving JavaScript test page");
        return "admin/test-js";
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
        }        // Don't default to "public" - this breaks tenant isolation!
        if (tenantId == null || tenantId.trim().isEmpty()) {
            log.error("❌ CRITICAL: No tenant found in request! This violates tenant isolation.");
            throw new IllegalStateException("Tenant ID is required for admin operations. Session may have expired.");
        }

        log.info("🎯 Using tenant ID: {}", tenantId);
        return tenantId;
    }    /**
     * Display paginated and searchable list of products
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
            Model model) {
          try {
            log.info("🚀 Step 1: listProducts method started");
            String tenantId = getCurrentTenantId(request);
            log.info("📦 Step 2: Listing products for tenant: {}", tenantId);

            // Create sort object
            Sort sort = Sort.by(sortDir.equals("desc") ? 
                Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
            log.debug("📊 Step 3: Created sort: {} {}", sortBy, sortDir);
            
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<ProductResponseDTO> products;
            
            if (search != null && !search.trim().isEmpty()) {
                log.debug("🔍 Step 4a: Searching products with term: {} for tenant: {}", search, tenantId);
                products = productService.searchProducts(search, pageable);
                model.addAttribute("search", search);
            } else {
                log.debug("� Step 4b: Listing all products for tenant: {}", tenantId);
                products = productService.getProductsPage(pageable);
            }
            log.info("� Step 5: Retrieved {} products (page {} of {})", 
                products.getNumberOfElements(), page + 1, products.getTotalPages());

            // Get counts for badges
            log.debug("📊 Step 6: Getting all products for badge counts");
            List<ProductResponseDTO> allProducts = productService.getAllProducts();
            long totalProducts = allProducts.size();
            log.debug("📊 Step 7: Calculating low stock count");
            long lowStockCount = allProducts.stream()
                .filter(p -> p.getStockLevel() <= p.getLowStockThreshold())
                .count();
            log.info("📊 Step 8: Badge counts - Total: {}, Low Stock: {}", totalProducts, lowStockCount);

            // Get categories for the categories tab
            log.debug("📋 Step 9: Getting categories");
            List<ProductCategoryResponseDTO> categories = categoryService.getAllCategories();
            log.info("📋 Step 10: Found {} categories", categories.size());

            model.addAttribute("products", products);
            model.addAttribute("currentPage", page);
            model.addAttribute("pageSize", size);
            model.addAttribute("sortBy", sortBy);
            model.addAttribute("sortDir", sortDir);
            model.addAttribute("totalPages", products.getTotalPages());
            model.addAttribute("totalElements", products.getTotalElements());
            model.addAttribute("tenantId", tenantId);
            model.addAttribute("totalProducts", totalProducts);
            model.addAttribute("lowStockCount", lowStockCount);
            model.addAttribute("activeTab", tab); // For JavaScript to know which tab to activate
            model.addAttribute("categories", categories); // For categories tab

            log.debug("📊 Found {} total products, {} low stock, {} categories for tenant: {}", 
                totalProducts, lowStockCount, categories.size(), tenantId);
            return "admin/products";
            
        } catch (Exception e) {
            log.error("❌ Failed to load products page: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Failed to load products: " + e.getMessage());
            model.addAttribute("products", Page.empty());
            model.addAttribute("totalProducts", 0);
            model.addAttribute("lowStockCount", 0);
            model.addAttribute("categories", List.of());
            return "admin/products";
        }
    }

    /**
     * Show form for adding a new product
     */    @GetMapping("/add")
    public String showAddProductForm(HttpServletRequest request, Model model) {
        String tenantId = getCurrentTenantId(request);
        log.info("➕ Showing add product form for tenant: {}", tenantId);
        
        model.addAttribute("product", new ProductCreateDTO());
        model.addAttribute("tenantId", tenantId);
        model.addAttribute("categories", categoryService.getAllActiveCategories());
        return "admin/product-form";
    }    /**
     * Handle product creation
     */
    @PostMapping
    public String createProduct(@ModelAttribute ProductCreateDTO productCreateDTO,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes,
                               Principal principal) {
        String tenantId = getCurrentTenantId(request);
        log.info("💾 Creating product for tenant: {}", tenantId);
          try {
            // Set created_by information from authenticated user
            if (principal != null) {
                Optional<AppUser> currentUser = appUserRepository.findByUsername(principal.getName());
                if (currentUser.isPresent()) {
                    productCreateDTO.setCreatedBy(currentUser.get().getId());
                    productCreateDTO.setUpdatedBy(currentUser.get().getId());
                }
            }
            
            // Set timestamps
            productCreateDTO.setCreatedAt(LocalDateTime.now());
            productCreateDTO.setUpdatedAt(LocalDateTime.now());
            
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
        log.info("✏️ Showing edit form for product ID: {} for tenant: {}", id, tenantId);          try {
            Optional<ProductResponseDTO> productOpt = productService.getProductById(id);
            if (productOpt.isPresent()) {
                ProductResponseDTO product = productOpt.get();
                model.addAttribute("product", product);
                model.addAttribute("tenantId", tenantId);
                model.addAttribute("categories", categoryService.getAllActiveCategories());
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
    }    /**
     * Handle product update
     */
    @PostMapping("/{id}")
    public String updateProduct(@PathVariable Long id,
                               @ModelAttribute ProductCreateDTO productCreateDTO,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes,
                               Principal principal) {
        String tenantId = getCurrentTenantId(request);
        log.info("🔄 Updating product ID: {} for tenant: {}", id, tenantId);
        
        try {
            // Set updated_by information from authenticated user
            if (principal != null) {
                Optional<AppUser> currentUser = appUserRepository.findByUsername(principal.getName());
                if (currentUser.isPresent()) {
                    productCreateDTO.setUpdatedBy(currentUser.get().getId());
                }
            }
            
            // Set timestamp
            productCreateDTO.setUpdatedAt(LocalDateTime.now());
            
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
    }    /**
     * Redirect to products page with low-stock tab active
     */
    @GetMapping("/low-stock")
    public String getLowStockProducts(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        String tenantId = getCurrentTenantId(request);
        log.info("⚠️ Redirecting to products page with low-stock tab for tenant: {}", tenantId);
        
        // Add parameter to indicate low-stock tab should be active
        redirectAttributes.addAttribute("tab", "low-stock");
        return "redirect:/admin/products";
    }    /**
     * Get low stock products as JSON data for AJAX calls
     */
    @GetMapping("/low-stock-data")
    @ResponseBody
    public ResponseEntity<List<ProductResponseDTO>> getLowStockProductsData(HttpServletRequest request) {
        log.info("🔍 AJAX Call: getLowStockProductsData started");
        
        try {
            log.debug("📋 Step 1: Getting current tenant ID");
            String tenantId = getCurrentTenantId(request);
            log.info("📊 Step 2: Getting low stock products data for tenant: {}", tenantId);
            
            log.debug("📦 Step 3: Calling productService.getAllProducts()");
            List<ProductResponseDTO> allProducts = productService.getAllProducts();
            log.info("📦 Step 4: Found {} total products for tenant: {}", allProducts.size(), tenantId);
            
            log.debug("🔽 Step 5: Filtering low stock products");
            List<ProductResponseDTO> lowStockProducts = allProducts.stream()
                .filter(p -> {
                    boolean isLowStock = p.getStockLevel() <= p.getLowStockThreshold();
                    log.debug("🔍 Product {} - Stock: {}, Threshold: {}, IsLowStock: {}", 
                        p.getTitle(), p.getStockLevel(), p.getLowStockThreshold(), isLowStock);
                    return isLowStock;
                })
                .toList();
                
            log.info("🔍 Step 6: Found {} low stock products for tenant: {}", 
                lowStockProducts.size(), tenantId);
            
            log.debug("✅ Step 7: Returning successful response");
            return ResponseEntity.ok(lowStockProducts);
            
        } catch (Exception e) {
            log.error("❌ CRITICAL: Failed to get low stock products data at step: {}", 
                e.getStackTrace()[0].getMethodName(), e);
            log.error("❌ Full error details: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(List.of()); // Return empty list with error status
        }
    }

    // Category management endpoints integrated into products page

    /**
     * Handle category creation from products page
     */
    @PostMapping("/categories")
    public String createCategory(@ModelAttribute ProductCategoryCreateDTO categoryCreateDTO,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        String tenantId = getCurrentTenantId(request);
        log.info("💾 Creating category for tenant: {}", tenantId);

        try {
            ProductCategoryResponseDTO savedCategory = categoryService.createCategory(categoryCreateDTO);
            redirectAttributes.addFlashAttribute("successMessage", 
                "Category '" + savedCategory.getName() + "' created successfully!");
            log.info("✅ Category created successfully with ID: {} for tenant: {}", 
                savedCategory.getId(), tenantId);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Failed to create category: " + e.getMessage());
            log.error("❌ Failed to create category for tenant: {}", tenantId, e);
        }

        return "redirect:/admin/products?tab=categories";
    }    /**
     * Get category details for editing
     */    @GetMapping("/categories/{id}")
    @ResponseBody
    public ResponseEntity<ProductCategoryResponseDTO> getCategoryDetails(@PathVariable Long id, HttpServletRequest request) {
        try {
            String tenantId = getCurrentTenantId(request);
            log.info("📝 Getting category details for ID: {} for tenant: {}", id, tenantId);
            
            Optional<ProductCategoryResponseDTO> category = categoryService.getCategoryById(id);
            if (category.isPresent()) {
                return ResponseEntity.ok(category.get());
            } else {
                log.warn("⚠️ Category not found with ID: {} for tenant: {}", id, tenantId);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("❌ Failed to get category details for ID: {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Handle category update from products page
     */
    @PostMapping("/categories/{id}")
    public String updateCategory(@PathVariable Long id,
                               @ModelAttribute ProductCategoryCreateDTO categoryUpdateDTO,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        String tenantId = getCurrentTenantId(request);
        log.info("📝 Updating category ID: {} for tenant: {}", id, tenantId);

        try {
            ProductCategoryResponseDTO updatedCategory = categoryService.updateCategory(id, categoryUpdateDTO);
            redirectAttributes.addFlashAttribute("successMessage", 
                "Category '" + updatedCategory.getName() + "' updated successfully!");
            log.info("✅ Category updated successfully with ID: {} for tenant: {}", id, tenantId);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Failed to update category: " + e.getMessage());
            log.error("❌ Failed to update category ID: {} for tenant: {}", id, tenantId, e);
        }

        return "redirect:/admin/products?tab=categories";
    }

    /**
     * Handle category deletion from products page
     */
    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        String tenantId = getCurrentTenantId(request);
        log.info("🗑️ Deleting category ID: {} for tenant: {}", id, tenantId);

        try {
            categoryService.deleteCategory(id);
            redirectAttributes.addFlashAttribute("successMessage", "Category deleted successfully!");
            log.info("✅ Category deleted successfully with ID: {} for tenant: {}", id, tenantId);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Failed to delete category: " + e.getMessage());
            log.error("❌ Failed to delete category ID: {} for tenant: {}", id, tenantId, e);
        }

        return "redirect:/admin/products?tab=categories";
    }    /**
     * Get categories list for AJAX requests
     */
    @GetMapping("/categories/list")
    @ResponseBody
    public ResponseEntity<List<ProductCategoryResponseDTO>> getCategoriesList(HttpServletRequest request) {
        try {
            String tenantId = getCurrentTenantId(request);
            log.info("📋 Getting categories list for tenant: {}", tenantId);
            
            List<ProductCategoryResponseDTO> categories = categoryService.getAllCategories();
            log.info("📊 Found {} categories for tenant: {}", categories.size(), tenantId);
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            log.error("❌ Failed to get categories list: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(List.of()); // Return empty list with error status
        }
    }

    /**
     * Handle quick restock requests from the admin panel
     */
    @PostMapping("/quick-restock")
    @ResponseBody
    public ResponseEntity<?> quickRestock(@RequestBody @Valid QuickRestockRequestDTO request, 
                                        HttpServletRequest httpRequest) {
        String tenantId = getCurrentTenantId(httpRequest);
        log.info("🔄 Quick restock request for product ID: {} with quantity: {} operation: {} for tenant: {}", 
                request.getProductId(), request.getQuantity(), request.getOperation(), tenantId);

        try {
            // Validate operation
            if (!request.getOperation().equalsIgnoreCase("ADD") && !request.getOperation().equalsIgnoreCase("SET")) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", true, "message", "Invalid operation. Use 'ADD' or 'SET'"));
            }

            // Perform quick restock
            QuickRestockResponseDTO response = productService.quickRestock(
                request.getProductId(), 
                request.getQuantity(), 
                request.getOperation()
            );

            log.info("✅ Quick restock completed successfully for product ID: {} for tenant: {}", 
                    request.getProductId(), tenantId);

            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Invalid quick restock request for tenant: {} - {}", tenantId, e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", true, "message", e.getMessage()));
                
        } catch (RuntimeException e) {
            log.error("❌ Quick restock failed for product ID: {} for tenant: {}", 
                     request.getProductId(), tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", true, "message", "Failed to update stock: " + e.getMessage()));
                
        } catch (Exception e) {
            log.error("❌ Unexpected error during quick restock for tenant: {}", tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", true, "message", "An unexpected error occurred"));
        }
    }
}
