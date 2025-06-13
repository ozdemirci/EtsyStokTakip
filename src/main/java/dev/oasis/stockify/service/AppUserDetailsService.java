package dev.oasis.stockify.service;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(AppUserDetailsService.class);
    private final AppUserRepository appUserRepository;

    public AppUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            if (username == null || username.trim().isEmpty()) {
                throw new UsernameNotFoundException("Kullanıcı adı boş olamaz");
            }

            // Request'ten tenant ID'yi al
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attr.getRequest();
            String paramTenantId = request.getParameter("tenant_id");

            // Mevcut tenant context'i kontrol et
            String currentTenant = TenantContext.getCurrentTenant();

            // Final tenant ID'yi belirle
            final String tenantId;

            // Eğer tenant context zaten ayarlanmışsa, onu kullan
            if (currentTenant != null && !currentTenant.isEmpty()) {
                logger.debug("Using existing tenant context: {}", currentTenant);
                tenantId = currentTenant;
            } else if (paramTenantId == null || paramTenantId.trim().isEmpty()) {
                // Eğer tenant context ayarlanmamışsa ve form parametresi de yoksa hata ver
                throw new AuthenticationServiceException("Tenant ID boş olamaz");
            } else {
                // Tenant ID'yi küçük harfe çevir ve context'e ayarla
                tenantId = paramTenantId.toLowerCase();
                TenantContext.setCurrentTenant(tenantId);
            }

            logger.debug("Login attempt - Username: {}, Tenant: {}", username, tenantId);

            try {
                AppUser appUser = appUserRepository.findByUsername(username)
                        .orElseThrow(() -> new UsernameNotFoundException(
                                String.format("Kullanıcı bulunamadı: %s (Tenant: %s)", username, tenantId)));

                logger.debug("User found in database: {} for tenant: {}", username, tenantId);

                return User.withUsername(appUser.getUsername())
                        .password(appUser.getPassword())
                        .roles(appUser.getRole().toUpperCase())
                        .build();

            } catch (Exception e) {
                logger.error("Error during user authentication - Username: {}, Tenant: {}, Error: {}",
                           username, tenantId, e.getMessage());
                throw new UsernameNotFoundException("Kullanıcı bilgileri doğrulanamadı");
            }

        } catch (Exception e) {
            logger.error("Authentication error: {}", e.getMessage());
            // Hata durumunda TenantContext'i temizle
            TenantContext.clear();
            throw e;
        }
    }
}
