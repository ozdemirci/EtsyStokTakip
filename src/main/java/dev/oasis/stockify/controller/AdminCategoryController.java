package dev.oasis.stockify.controller;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.ProductCategoryCreateDTO;
import dev.oasis.stockify.dto.ProductCategoryResponseDTO;
import dev.oasis.stockify.service.ProductCategoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
// import org.springframework.stereotype.Controller; - Disabled
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

/**
 * Admin controller for category management operations
 * NOTE: This controller is disabled. Category management is now integrated into AdminProductController.
 */
@Slf4j
// @Controller - Disabled: Category management moved to AdminProductController
@RequestMapping("/admin/categories")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final ProductCategoryService categoryService;

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
     * List all categories
     */
    @GetMapping
    public String listCategories(HttpServletRequest request, Model model) {
        String tenantId = getCurrentTenantId(request);
        log.info("📂 Listing categories for tenant: {}", tenantId);

        List<ProductCategoryResponseDTO> categories = categoryService.getAllCategories();
        model.addAttribute("categories", categories);
        model.addAttribute("tenantId", tenantId);
        return "admin/categories";
    }

    /**
     * Show form for adding a new category
     */
    @GetMapping("/add")
    public String showAddCategoryForm(HttpServletRequest request, Model model) {
        String tenantId = getCurrentTenantId(request);
        log.info("➕ Showing add category form for tenant: {}", tenantId);

        model.addAttribute("category", new ProductCategoryCreateDTO());
        model.addAttribute("tenantId", tenantId);
        return "admin/category-form";
    }

    /**
     * Handle category creation
     */
    @PostMapping
    public String createCategory(@ModelAttribute ProductCategoryCreateDTO categoryCreateDTO,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        String tenantId = getCurrentTenantId(request);
        log.info("💾 Creating category for tenant: {}", tenantId);

        try {
            ProductCategoryResponseDTO createdCategory = categoryService.createCategory(categoryCreateDTO);
            redirectAttributes.addFlashAttribute("successMessage", 
                "Category '" + createdCategory.getName() + "' created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Failed to create category: " + e.getMessage());
        }
        
        return "redirect:/admin/categories";
    }

    /**
     * Show form for editing an existing category
     */
    @GetMapping("/{id}/edit")
    public String showEditCategoryForm(@PathVariable Long id,
                                     HttpServletRequest request,
                                     Model model,
                                     RedirectAttributes redirectAttributes) {
        String tenantId = getCurrentTenantId(request);
        log.info("✏️ Showing edit form for category ID: {} for tenant: {}", id, tenantId);
          
        try {
            Optional<ProductCategoryResponseDTO> categoryOpt = categoryService.getCategoryById(id);
            if (categoryOpt.isPresent()) {
                ProductCategoryResponseDTO category = categoryOpt.get();
                model.addAttribute("category", category);
                model.addAttribute("tenantId", tenantId);
                return "admin/category-form";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Category not found");
                return "redirect:/admin/categories";
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Category not found: " + e.getMessage());
            return "redirect:/admin/categories";
        }
    }

    /**
     * Handle category update
     */
    @PostMapping("/{id}")
    public String updateCategory(@PathVariable Long id,
                               @ModelAttribute ProductCategoryCreateDTO categoryCreateDTO,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        String tenantId = getCurrentTenantId(request);
        log.info("🔄 Updating category ID: {} for tenant: {}", id, tenantId);
        
        try {
            ProductCategoryResponseDTO updatedCategory = categoryService.updateCategory(id, categoryCreateDTO);
            redirectAttributes.addFlashAttribute("successMessage", 
                "Category '" + updatedCategory.getName() + "' updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Failed to update category: " + e.getMessage());
        }
        
        return "redirect:/admin/categories";
    }

    /**
     * Handle category deletion
     */
    @PostMapping("/{id}/delete")
    public String deleteCategory(@PathVariable Long id,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        String tenantId = getCurrentTenantId(request);
        log.info("🗑️ Deleting category ID: {} for tenant: {}", id, tenantId);
        
        try {
            categoryService.deleteCategory(id);
            redirectAttributes.addFlashAttribute("successMessage", "Category deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Failed to delete category: " + e.getMessage());
        }
        
        return "redirect:/admin/categories";
    }
}
