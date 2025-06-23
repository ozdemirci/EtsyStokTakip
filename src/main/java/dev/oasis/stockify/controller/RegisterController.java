package dev.oasis.stockify.controller;

import dev.oasis.stockify.dto.RegisterRequestDTO;
import dev.oasis.stockify.dto.RegistrationResultDTO;
import dev.oasis.stockify.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for user registration
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class RegisterController {

    private final RegistrationService registrationService;

    /**
     * Show registration form
     */
    @GetMapping("/register")
    public String showRegisterForm(@RequestParam(required = false) String plan, Model model) {
        model.addAttribute("selectedPlan", plan != null ? plan : "starter");
        model.addAttribute("registerRequest", new RegisterRequestDTO());
        
        // Add plan information for display
        if (plan != null) {
            model.addAttribute("planFeatures", registrationService.getPlanFeatures(plan));
            model.addAttribute("planPrice", registrationService.getPlanPrice(plan));
        }
        
        return "register";
    }

    /**
     * Process registration form submission
     */
    @PostMapping("/register")
    public String processRegistration(@Valid @ModelAttribute("registerRequest") RegisterRequestDTO registerRequest,
                                    BindingResult bindingResult,
                                    RedirectAttributes redirectAttributes,
                                    Model model) {
        
        log.info("🎯 Processing registration for email: {}", registerRequest.getEmail());
        
        // Check for validation errors
        if (bindingResult.hasErrors()) {
            log.warn("❌ Validation errors in registration form");
            model.addAttribute("selectedPlan", registerRequest.getSelectedPlan());
            model.addAttribute("planFeatures", registrationService.getPlanFeatures(registerRequest.getSelectedPlan()));
            model.addAttribute("planPrice", registrationService.getPlanPrice(registerRequest.getSelectedPlan()));
            return "register";
        }
        
        // Additional validation
        if (!registerRequest.getPassword().equals(registerRequest.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "passwords.mismatch", "Şifreler eşleşmiyor");
            model.addAttribute("selectedPlan", registerRequest.getSelectedPlan());
            model.addAttribute("planFeatures", registrationService.getPlanFeatures(registerRequest.getSelectedPlan()));
            model.addAttribute("planPrice", registrationService.getPlanPrice(registerRequest.getSelectedPlan()));
            return "register";
        }
        
        if (!Boolean.TRUE.equals(registerRequest.getAcceptTerms())) {
            bindingResult.rejectValue("acceptTerms", "terms.required", "Kullanım şartlarını kabul etmelisiniz");
            model.addAttribute("selectedPlan", registerRequest.getSelectedPlan());
            model.addAttribute("planFeatures", registrationService.getPlanFeatures(registerRequest.getSelectedPlan()));
            model.addAttribute("planPrice", registrationService.getPlanPrice(registerRequest.getSelectedPlan()));
            return "register";
        }
          try {
            // Process registration
            RegistrationResultDTO registrationResult = registrationService.registerUserWithTenant(registerRequest);
            
            log.info("✅ Registration successful for: {}", registerRequest.getEmail());
            redirectAttributes.addFlashAttribute("registrationResult", registrationResult);
            redirectAttributes.addFlashAttribute("registeredEmail", registerRequest.getEmail());
            
            return "redirect:/login?registered=true&tenantId=" + registrationResult.getTenantId();
            
        } catch (Exception e) {
            log.error("❌ Registration failed for {}: {}", registerRequest.getEmail(), e.getMessage());
            
            String errorMessage = e.getMessage();
            if (errorMessage.contains("email") && errorMessage.contains("kayıtlı")) {
                bindingResult.rejectValue("email", "email.exists", errorMessage);
            } else {
                model.addAttribute("errorMessage", "Kayıt işlemi başarısız: " + errorMessage);
            }
            
            model.addAttribute("selectedPlan", registerRequest.getSelectedPlan());
            model.addAttribute("planFeatures", registrationService.getPlanFeatures(registerRequest.getSelectedPlan()));
            model.addAttribute("planPrice", registrationService.getPlanPrice(registerRequest.getSelectedPlan()));
            return "register";
        }
    }

    /**
     * AJAX endpoint to check if email is already registered
     */
    @GetMapping("/register/check-email")
    @ResponseBody
    public boolean checkEmailAvailability(@RequestParam String email) {
        try {
            return !registrationService.isEmailAlreadyRegistered(email);
        } catch (Exception e) {
            log.warn("Could not check email availability: {}", e.getMessage());
            return true; // Assume available if check fails
        }
    }

    /**
     * AJAX endpoint to validate company name
     */
    @GetMapping("/register/check-company")
    @ResponseBody
    public boolean checkCompanyNameAvailability(@RequestParam String companyName) {
        // For now, just check basic validity - you can add more sophisticated checks later
        return companyName != null && companyName.trim().length() >= 2;
    }
}
