package dev.oasis.stockify.controller;

import dev.oasis.stockify.config.tenant.TenantContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String tenantId, Model model) {
        if (tenantId != null && !tenantId.isEmpty()) {
            TenantContext.setCurrentTenant(tenantId.toLowerCase());
        }
        model.addAttribute("tenantId", tenantId);
        return "login";
    }

    @GetMapping("/access-denied")
    public String accessDenied(Model model) {
        model.addAttribute("errorMessage", "Erişim reddedildi. Bu sayfaya erişim yetkiniz bulunmamaktadır.");
        model.addAttribute("currentTenant", TenantContext.getCurrentTenant());
        return "access-denied";
    }
}
