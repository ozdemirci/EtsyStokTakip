package dev.oasis.stockify.controller;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.RegistrationResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@Slf4j
public class LoginController {    
    
    @GetMapping("/login")
    public String login(@RequestParam(required = false) String tenantId, 
                       @RequestParam(required = false) String registered,
                       Model model) {
        
        // Check if we have a registration result from flash attributes
        RegistrationResultDTO registrationResult = (RegistrationResultDTO) model.asMap().get("registrationResult");
        
        // Use tenant ID from parameter or from registration result
        String effectiveTenantId = tenantId;
        if (effectiveTenantId == null && registrationResult != null) {
            effectiveTenantId = registrationResult.getTenantId();
        }
        
        if (effectiveTenantId != null && !effectiveTenantId.isEmpty()) {
            TenantContext.setCurrentTenant(effectiveTenantId.toLowerCase());
            model.addAttribute("tenantId", effectiveTenantId);
        }
        
        // Handle registration success message
        if ("true".equals(registered) && registrationResult != null) {
            model.addAttribute("registrationSuccess", true);
            model.addAttribute("successMessage", 
                registrationResult.getMessage() + 
                " Şirket ID'niz: " + registrationResult.getTenantId() + 
                " - Bu ID ile giriş yapınız.");
            log.info("✅ Registration completed for tenant {}, redirected to login", registrationResult.getTenantId());
        } else if ("true".equals(registered)) {
            model.addAttribute("registrationSuccess", true);
            model.addAttribute("successMessage", "Hesabınız başarıyla oluşturuldu! Giriş yapabilirsiniz.");
            log.info("✅ Registration completed, redirected to login");
        }
        
        return "login";
    }

    @GetMapping("/access-denied")
    public String accessDenied(Model model) {
        model.addAttribute("errorMessage", "Erişim reddedildi. Bu sayfaya erişim yetkiniz bulunmamaktadır.");
        model.addAttribute("currentTenant", TenantContext.getCurrentTenant());
        return "access-denied";
    }
}
