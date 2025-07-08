package dev.oasis.stockify.controller;

import dev.oasis.stockify.util.ControllerTenantUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for barcode scanner web interface
 */
@Controller
@RequestMapping("/admin/barcode")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
public class BarcodeScannerController {
    
    private final ControllerTenantUtil tenantResolutionUtil;
    
    /**
     * Ensure tenant context is set for all requests
     */
    @ModelAttribute
    public void setupTenantContext(HttpServletRequest request) {
        tenantResolutionUtil.setupTenantContext(request);
    }
    
    /**
     * Display barcode scanner interface
     */
    @GetMapping("/scanner")
    public String showBarcodeScanner(HttpServletRequest request, 
                                   Authentication authentication, 
                                   Model model) {
        
        String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
        log.info("📱 Showing barcode scanner interface for tenant: {}", tenantId);
        
        model.addAttribute("tenantId", tenantId);
        model.addAttribute("pageTitle", "Barcode Scanner");
        
        return "admin/barcode-scanner";
    }
}
