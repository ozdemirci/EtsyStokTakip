package dev.oasis.stockify.controller;

import dev.oasis.stockify.dto.TenantCreateDTO;
import dev.oasis.stockify.dto.TenantDTO;
import dev.oasis.stockify.service.TenantManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import java.util.List;

/**
 * Controller for tenant management operations
 * Provides both web UI and REST API endpoints
 */
@Slf4j
@Controller
@RequestMapping("/admin/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TenantManagementController {

    private final TenantManagementService tenantManagementService;

    /**
     * Display tenant management dashboard
     */
    @GetMapping
    public String tenantDashboard(Model model) {
        try {
            List<TenantDTO> tenants = tenantManagementService.getAllTenants();
            model.addAttribute("tenants", tenants);
            model.addAttribute("totalTenants", tenants.size());
            model.addAttribute("activeTenants", tenants.stream()
                    .mapToLong(t -> "ACTIVE".equals(t.getStatus()) ? 1 : 0).sum());
            
            return "admin/tenant-dashboard";
        } catch (Exception e) {
            log.error("❌ Error loading tenant dashboard: {}", e.getMessage());
            model.addAttribute("error", "Failed to load tenant information");
            return "error";
        }
    }

    /**
     * Show create tenant form
     */
    @GetMapping("/new")
    public String showCreateTenantForm(Model model) {
        model.addAttribute("tenantCreateDTO", new TenantCreateDTO());
        return "admin/tenant-form";
    }

    /**
     * Handle tenant creation
     */
    @PostMapping("/create")
    public String createTenant(@Valid @ModelAttribute TenantCreateDTO tenantCreateDTO,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        
        if (bindingResult.hasErrors()) {
            model.addAttribute("tenantCreateDTO", tenantCreateDTO);
            return "admin/tenant-form";
        }

        try {
            TenantDTO createdTenant = tenantManagementService.createTenant(tenantCreateDTO);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Tenant '" + createdTenant.getCompanyName() + "' created successfully with ID: " + createdTenant.getTenantId());
            
            log.info("✅ Tenant created successfully: {}", createdTenant.getTenantId());
            return "redirect:/admin/tenants";
            
        } catch (Exception e) {
            log.error("❌ Error creating tenant: {}", e.getMessage());
            model.addAttribute("error", "Failed to create tenant: " + e.getMessage());
            model.addAttribute("tenantCreateDTO", tenantCreateDTO);
            return "admin/tenant-form";
        }
    }

    /**
     * Show tenant details
     */
    @GetMapping("/{tenantId}")
    public String showTenantDetails(@PathVariable String tenantId, Model model) {
        try {
            TenantDTO tenant = tenantManagementService.getTenant(tenantId);
            model.addAttribute("tenant", tenant);
            return "admin/tenant-details";
        } catch (Exception e) {
            log.error("❌ Error loading tenant details for {}: {}", tenantId, e.getMessage());
            model.addAttribute("error", "Tenant not found");
            return "redirect:/admin/tenants";
        }
    }

    /**
     * Deactivate tenant
     */
    @PostMapping("/{tenantId}/deactivate")
    public String deactivateTenant(@PathVariable String tenantId, 
                                  RedirectAttributes redirectAttributes) {
        try {
            tenantManagementService.deactivateTenant(tenantId);
            redirectAttributes.addFlashAttribute("successMessage", 
                "Tenant " + tenantId + " has been deactivated");
        } catch (Exception e) {
            log.error("❌ Error deactivating tenant {}: {}", tenantId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Failed to deactivate tenant: " + e.getMessage());
        }
        return "redirect:/admin/tenants";
    }

    /**
     * Activate tenant
     */
    @PostMapping("/{tenantId}/activate")
    public String activateTenant(@PathVariable String tenantId, 
                                RedirectAttributes redirectAttributes) {
        try {
            tenantManagementService.activateTenant(tenantId);
            redirectAttributes.addFlashAttribute("successMessage", 
                "Tenant " + tenantId + " has been activated");
        } catch (Exception e) {
            log.error("❌ Error activating tenant {}: {}", tenantId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Failed to activate tenant: " + e.getMessage());
        }
        return "redirect:/admin/tenants";
    }

    // REST API Endpoints

    /**
     * REST API: Get all tenants
     */
    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<List<TenantDTO>> getAllTenantsApi() {
        try {
            List<TenantDTO> tenants = tenantManagementService.getAllTenants();
            return ResponseEntity.ok(tenants);
        } catch (Exception e) {
            log.error("❌ API Error retrieving tenants: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * REST API: Create tenant
     */
    @PostMapping("/api")
    @ResponseBody
    public ResponseEntity<TenantDTO> createTenantApi(@Valid @RequestBody TenantCreateDTO tenantCreateDTO) {
        try {
            TenantDTO createdTenant = tenantManagementService.createTenant(tenantCreateDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdTenant);
        } catch (Exception e) {
            log.error("❌ API Error creating tenant: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * REST API: Get tenant by ID
     */
    @GetMapping("/api/{tenantId}")
    @ResponseBody
    public ResponseEntity<TenantDTO> getTenantApi(@PathVariable String tenantId) {
        try {
            TenantDTO tenant = tenantManagementService.getTenant(tenantId);
            return ResponseEntity.ok(tenant);
        } catch (Exception e) {
            log.error("❌ API Error retrieving tenant {}: {}", tenantId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
