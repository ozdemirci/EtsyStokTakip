package dev.oasis.stockify.controller;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for subscription-related pages
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {
    
    private final SubscriptionService subscriptionService;
    
    @GetMapping("/trial-expired")
    public String trialExpired(Model model) {
        String tenantId = TenantContext.getCurrentTenant();
        model.addAttribute("tenantId", tenantId);
        
        SubscriptionService.PlanType currentPlan = subscriptionService.getTenantPlan();
        model.addAttribute("currentPlan", currentPlan.getCode());
        
        log.info("Trial expired page accessed for tenant: {}", tenantId);
        return "trial-expired";
    }
}
