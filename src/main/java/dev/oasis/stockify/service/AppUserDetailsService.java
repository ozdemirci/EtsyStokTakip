package dev.oasis.stockify.service;

import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.util.ServiceTenantUtil;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;
    private final DataSource dataSource;
    private final ServiceTenantUtil serviceTenantUtil;

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
            String currentTenant = serviceTenantUtil.getCurrentTenant();

            // Final tenant ID'yi belirle
            final String tenantId;

            // Eğer tenant context zaten ayarlanmışsa, onu kullan
            if (currentTenant != null && !currentTenant.isEmpty()) {
                log.debug("Using existing tenant context: {}", currentTenant);
                tenantId = currentTenant;
            } else if (paramTenantId == null || paramTenantId.trim().isEmpty()) {
                // Eğer tenant context ayarlanmamışsa ve form parametresi de yoksa hata ver
                throw new AuthenticationServiceException("Tenant ID boş olamaz");
            } else {
                // Tenant ID'yi küçük harfe çevir ve context'e ayarla
                tenantId = paramTenantId.toLowerCase();
                serviceTenantUtil.setCurrentTenant(tenantId);
            }

            log.info("🔐 Login attempt - Username: {}, Tenant: {}", username, tenantId);

            // Check if tenant schema exists before attempting login
            if (!tenantSchemaExists(tenantId)) {
                log.error("❌ Tenant schema does not exist: {}", tenantId);
                throw new UsernameNotFoundException(
                        String.format("Kurum ID bulunamadı: %s. Lütfen geçerli bir Kurum ID girin.", tenantId));
            }
            log.info("✅ Tenant schema exists: {}", tenantId);

            try {
                // Only allow username-based login (not email)
                log.info("🔍 Searching for user: {} in tenant: {}", username, tenantId);
                AppUser appUser = appUserRepository.findByUsername(username).orElse(null);
                
                if (appUser == null) {
                    log.error("❌ User not found: {} in tenant: {}", username, tenantId);
                    throw new UsernameNotFoundException(
                            String.format("Kullanıcı bulunamadı: %s (Tenant: %s)", username, tenantId));
                }
                log.info("✅ User found: {} in tenant: {}", username, tenantId);

                // Check if user is active
                if (appUser.getIsActive() == null || !appUser.getIsActive()) {
                    log.warn("❌ Inactive user attempted to login: {} for tenant: {}", username, tenantId);
                    throw new UsernameNotFoundException("Kullanıcı hesabı aktif değil");
                }
                log.info("✅ User is active: {} in tenant: {}", username, tenantId);

                log.info("✅ Creating UserDetails for: {} in tenant: {}", username, tenantId);
                UserDetails userDetails = User.withUsername(appUser.getUsername())
                        .password(appUser.getPassword())
                        .roles(appUser.getRole().getCode())
                        .build();

                log.info("🔑 UserDetails created - Username: {}, Password hash: {}, Roles: {}", 
                        userDetails.getUsername(), 
                        userDetails.getPassword().substring(0, Math.min(10, userDetails.getPassword().length())) + "...",
                        userDetails.getAuthorities());

                // DON'T clear tenant context here - let Spring Security handle authentication first
                // serviceTenantUtil.clearCurrentTenant();
                return userDetails;

            } catch (UsernameNotFoundException | AuthenticationServiceException e) {
                log.error("Error during user authentication - Username: {}, Tenant: {}, Error: {}",
                          username, tenantId, e.getMessage());
                throw e; // Spesifik hatayı yeniden fırlat
            } catch (Exception e) {
                log.error("Unexpected error during user authentication - Username: {}, Tenant: {}, Error: {}",
                          username, tenantId, e.getMessage());
                throw new RuntimeException("Beklenmeyen bir hata oluştu", e);
            }

        } catch (Exception e) {
            log.error("Authentication error: {}", e.getMessage());
            // Hata durumunda TenantContext'i temizle
            serviceTenantUtil.clearCurrentTenant();
            throw e;
        }
    }

    /**
     * Check if tenant schema exists in database
     */
    private boolean tenantSchemaExists(String tenantId) {
        try (Connection connection = dataSource.getConnection()) {
            String sql = "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, tenantId.toLowerCase());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBoolean(1);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Error checking tenant schema existence for {}: {}", tenantId, e.getMessage());
        }
        return false;
    }
}
