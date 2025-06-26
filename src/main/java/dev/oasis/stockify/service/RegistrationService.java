package dev.oasis.stockify.service;

import dev.oasis.stockify.dto.RegisterRequestDTO;
import dev.oasis.stockify.dto.RegistrationResultDTO;
import dev.oasis.stockify.dto.TenantCreateDTO;
import dev.oasis.stockify.dto.TenantDTO;
import dev.oasis.stockify.model.PlanType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for handling user registration and tenant creation
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RegistrationService {
      private final TenantManagementService tenantManagementService;
    private final AppUserService appUserService;
    private final SubscriptionService subscriptionService;
      /**
     * Register a new user with company/tenant
     * 
     * @param registerRequest registration form data
     * @return registration result with tenant ID and other info
     */
    @Transactional
    public RegistrationResultDTO registerUserWithTenant(RegisterRequestDTO registerRequest) {
        log.info("🚀 Starting registration for company: {}", registerRequest.getCompanyName());
        
        try {
            // Validate terms acceptance
            if (!Boolean.TRUE.equals(registerRequest.getAcceptTerms())) {
                throw new RuntimeException("Kullanım şartlarını kabul etmelisiniz");
            }
            
            // Validate password confirmation
            if (!registerRequest.getPassword().equals(registerRequest.getConfirmPassword())) {
                throw new RuntimeException("Şifreler eşleşmiyor");
            }
            
            // Check if email already exists
            if (isEmailAlreadyRegistered(registerRequest.getEmail())) {
                throw new RuntimeException("Bu e-posta adresi zaten kayıtlı");
            }

            // Check if username already exists
            if (appUserService.existsByUsername(registerRequest.getUsername())) {
                throw new RuntimeException("Bu kullanıcı adı zaten kayıtlı");
            }

            String username = registerRequest.getUsername();
            
            // Create tenant
            TenantCreateDTO tenantCreateDTO = TenantCreateDTO.builder()
                    .companyName(registerRequest.getCompanyName())
                    .adminUsername(username)
                    .adminPassword(registerRequest.getPassword())
                    .adminEmail(registerRequest.getEmail())
                    .description("Yeni kayıt - " + registerRequest.getSelectedPlan() + " planı")
                    .build();            TenantDTO createdTenant = tenantManagementService.createTenant(tenantCreateDTO);
            log.info("✅ Tenant created successfully: {}", createdTenant.getTenantId());
            
            // Set subscription plan limits
            subscriptionService.setTenantPlan(createdTenant.getTenantId(), registerRequest.getSelectedPlan());
            log.info("✅ Subscription plan {} set for tenant {}", registerRequest.getSelectedPlan(), createdTenant.getTenantId());
            
            return RegistrationResultDTO.builder()
                    .message("Hesabınız başarıyla oluşturuldu!")
                    .tenantId(createdTenant.getTenantId())
                    .username(username)
                    .email(registerRequest.getEmail())
                    .build();
            
        } catch (Exception e) {
            log.error("❌ Registration failed for {}: {}", registerRequest.getEmail(), e.getMessage());
            throw new RuntimeException("Kayıt işlemi başarısız: " + e.getMessage());
        }
    }
      /**
     * Check if email is already registered across all tenants
     */
    public boolean isEmailAlreadyRegistered(String email) {
        try {
            // Check across all tenants - this would need a global user check
            // For now, we'll do a simple check in the current tenant context
            return appUserService.existsByEmail(email);
        } catch (Exception e) {
            log.warn("Could not check email existence: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if username is already registered across all tenants
     */
    public boolean isUsernameAlreadyRegistered(String username) {
        try {
            return appUserService.existsByUsername(username);
        } catch (Exception e) {
            log.warn("Could not check username existence: {}", e.getMessage());
            return false;
        }
    }
    
      /**
     * Get plan features for display
     */
    public String getPlanFeatures(String plan) {
        PlanType planType = PlanType.fromCode(plan);
        return planType.getFeaturesDescription();
    }
    
    /**
     * Get plan price for display
     */
    public String getPlanPrice(String plan) {
        PlanType planType = PlanType.fromCode(plan);
        return planType.getPriceDescription();
    }
}
