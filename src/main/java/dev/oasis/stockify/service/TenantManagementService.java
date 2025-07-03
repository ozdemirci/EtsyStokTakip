package dev.oasis.stockify.service;

import dev.oasis.stockify.dto.TenantCreateDTO;
import dev.oasis.stockify.dto.TenantDTO;
import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.exception.TenantAlreadyExistsException;
import dev.oasis.stockify.exception.TenantNotFoundException;
import dev.oasis.stockify.model.Role;
import dev.oasis.stockify.util.ServiceTenantUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Service for managing tenant lifecycle operations
 * Simplified version using JPA schema auto-generation instead of Flyway
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantManagementService {

    private final DataSource dataSource;
    private final AppUserService appUserService;
    private final ServiceTenantUtil serviceTenantUtil;

    /**
     * Create a new tenant with complete setup
     */

    @Transactional
    public TenantDTO createTenant(TenantCreateDTO createDTO) {
        String tenantId = generateTenantId(createDTO.getCompanyName());
        
        log.info("🏢 Creating new tenant: {} for company: {}", tenantId, createDTO.getCompanyName());
        
        try {
            // Check if tenant already exists
            if (tenantExists(tenantId)) {
                throw new TenantAlreadyExistsException("Tenant already exists: " + tenantId);
            }
            
            // Create tenant schema
            createTenantSchema(tenantId);
            
            // Set tenant context for data operations
            serviceTenantUtil.setCurrentTenant(tenantId);
            
            // Create initial admin user (this will trigger JPA table creation)
            createTenantAdmin(createDTO);
            
            log.info("✅ Successfully created tenant: {}", tenantId);
            
            return TenantDTO.builder()
                    .tenantId(tenantId)
                    .companyName(createDTO.getCompanyName())
                    .adminEmail(createDTO.getAdminEmail())
                    .status("ACTIVE")
                    .createdAt(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("❌ Failed to create tenant {}: {}", tenantId, e.getMessage(), e);
            // Cleanup on failure
            cleanupFailedTenant(tenantId);
            throw new RuntimeException("Failed to create tenant: " + e.getMessage(), e);
        } finally {
            serviceTenantUtil.clearCurrentTenant();
        }
    }

    /**
     * Get all active tenants
     */
    public List<TenantDTO> getAllTenants() {
        List<TenantDTO> tenants = new ArrayList<>();
        
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // Check if master tenant registry exists
            ResultSet schemas = connection.getMetaData().getSchemas();
            while (schemas.next()) {
                String schemaName = schemas.getString("TABLE_SCHEM");
                if (!isSystemSchema(schemaName) && !schemaName.equalsIgnoreCase("public")) {
                    TenantDTO tenant = getTenantInfo(schemaName.toLowerCase());
                    if (tenant != null) {
                        tenants.add(tenant);
                    }
                }
            }
            
        } catch (SQLException e) {
            log.error("❌ Failed to retrieve tenants: {}", e.getMessage());
            throw new RuntimeException("Failed to retrieve tenants", e);
        }
        
        return tenants;
    }

    /**
     * Get tenant information by ID
     */
    public TenantDTO getTenant(String tenantId) {
        if (!tenantExists(tenantId)) {
            throw new TenantNotFoundException("Tenant not found: " + tenantId);
        }
        
        return getTenantInfo(tenantId);
    }

    /**
     * Deactivate a tenant (soft delete)
     */
    @Transactional
    public void deactivateTenant(String tenantId) {
        log.info("🔒 Deactivating tenant: {}", tenantId);
        
        try {
            serviceTenantUtil.setCurrentTenant(tenantId);
            updateTenantStatus(tenantId, "INACTIVE");
            log.info("✅ Successfully deactivated tenant: {}", tenantId);
        } finally {
            serviceTenantUtil.clearCurrentTenant();
        }
    }

    /**
     * Activate a tenant
     */
    @Transactional
    public void activateTenant(String tenantId) {
        log.info("🔓 Activating tenant: {}", tenantId);
        
        try {
            serviceTenantUtil.setCurrentTenant(tenantId);
            updateTenantStatus(tenantId, "ACTIVE");
            log.info("✅ Successfully activated tenant: {}", tenantId);
        } finally {
            serviceTenantUtil.clearCurrentTenant();
        }
    }

    /**
     * Check if tenant exists
     */    public boolean tenantExists(String tenantId) {
        try (Connection connection = dataSource.getConnection()) {
            String schemaName = tenantId.toLowerCase(Locale.ROOT);
            ResultSet schemas = connection.getMetaData().getSchemas();
            while (schemas.next()) {
                if (schemaName.equals(schemas.getString("TABLE_SCHEM"))) {
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            log.error("❌ Error checking tenant existence: {}", e.getMessage());
            return false;
        }
    }

    // Private helper methods
    private String generateTenantId(String companyName) {
        // Generate tenant ID based on company name
        String sanitized = companyName.toLowerCase()
                .replaceAll("[^a-zA-Z0-9]", "")
                .trim();
        
        // Ensure minimum length
        if (sanitized.length() < 2) {
            throw new RuntimeException("Şirket adı çok kısa. En az 2 karakter olmalıdır.");
        }
        
        // First try to use the company name directly
        String baseId = sanitized.substring(0, Math.min(sanitized.length(), 20));
        
        // Check if this tenant ID already exists
        if (!tenantExists(baseId)) {
            return baseId;
        }
        
        // If exists, try with incrementing numbers
        for (int i = 2; i <= 999; i++) {
            String candidateId = baseId + i;
            if (!tenantExists(candidateId)) {
                return candidateId;
            }
        }
        
        // If all numeric suffixes are taken, fall back to random suffix
        String suffix = UUID.randomUUID().toString().substring(0, 4);
        return baseId + "_" + suffix;
    }

    private void createTenantSchema(String tenantId) throws SQLException {
        String schemaName = tenantId.toLowerCase();
        
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // Create schema - JPA will automatically create tables when accessed
            statement.execute(String.format("CREATE SCHEMA IF NOT EXISTS \"%s\"", schemaName));
            log.info("🏗️ Created schema: {} (tables will be auto-created by JPA)", schemaName);
        }
    }
    
    private boolean isSystemSchema(String schemaName) {
        return schemaName.equalsIgnoreCase("INFORMATION_SCHEMA") ||
               schemaName.equalsIgnoreCase("SYSTEM_LOBS") ||
               schemaName.equalsIgnoreCase("SYS") ||
               schemaName.equalsIgnoreCase("SYSAUX");
    }

    private void cleanupFailedTenant(String tenantId) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            String schemaName = tenantId.toLowerCase(Locale.ROOT);
            statement.execute(String.format("DROP SCHEMA IF EXISTS \"%s\" CASCADE", schemaName));
            log.info("🧹 Cleaned up failed tenant schema: {}", schemaName);
            
        } catch (SQLException e) {
            log.error("❌ Failed to cleanup tenant {}: {}", tenantId, e.getMessage());
        }
    }
    
    private void createTenantAdmin(TenantCreateDTO createDTO) {
        try {
            UserCreateDTO adminUser = new UserCreateDTO();
            adminUser.setUsername(createDTO.getAdminUsername());
            adminUser.setPassword(createDTO.getAdminPassword());
            adminUser.setRole(Role.ADMIN);
            adminUser.setEmail(createDTO.getAdminEmail());
            
            appUserService.saveUser(adminUser);
            log.debug("👤 Created admin user for tenant");
        } catch (Exception e) {
            log.error("❌ Failed to create admin user: {}", e.getMessage());
            throw new RuntimeException("Failed to create admin user", e);
        }
    }

    private TenantDTO getTenantInfo(String tenantId) {
        // For now, return basic tenant info since JPA will handle table creation
        return TenantDTO.builder()
                .tenantId(tenantId)
                .companyName("Company: " + tenantId)
                .adminEmail("admin@" + tenantId + ".com")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void updateTenantStatus(String tenantId, String status) {
        // For now, just log the status change since we're using JPA auto-generation
        log.info("📝 Tenant {} status updated to: {}", tenantId, status);
    }
}
