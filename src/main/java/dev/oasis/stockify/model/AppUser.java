package dev.oasis.stockify.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotBlank(message = "Kullanıcı adı boş olamaz")
    @Size(min = 3, max = 20, message = "Kullanıcı adı 3 ile 20 karakter arasında olmalıdır")
    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @NotBlank(message = "Şifre boş olamaz")
    @Size(min = 6, message = "Şifre en az 6 karakter olmalıdır")
    @Column(name = "password", nullable = false)
    private String password;

    @NotBlank(message = "Rol boş olamaz")
    @Column(name = "role")
    private String role; // "SUPER_ADMIN", "ADMIN", "DEPO", "USER"

    // Super Admin specific fields
    @Column(name = "can_manage_all_tenants")
    private Boolean canManageAllTenants = false;
    
    @Column(name = "accessible_tenants", length = 1000)
    private String accessibleTenants; // Comma-separated list of tenant names for SUPER_ADMIN
    
    @Column(name = "is_global_user")
    private Boolean isGlobalUser = false; // Can access multiple tenants
    
    @Column(name = "primary_tenant", length = 50)
    private String primaryTenant; // Primary tenant for this user

    // Audit fields
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        // Set defaults for SUPER_ADMIN
        if ("SUPER_ADMIN".equals(role)) {
            canManageAllTenants = true;
            isGlobalUser = true;
            accessibleTenants = "public,stockify,acme_corp,global_trade,artisan_crafts,tech_solutions";
            if (primaryTenant == null) {
                primaryTenant = "stockify";
            }
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Helper methods for Super Admin
    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(role);
    }
    
    public boolean canAccessTenant(String tenantName) {
        if (isSuperAdmin() && canManageAllTenants) {
            return true;
        }
        
        if (accessibleTenants != null) {
            return Set.of(accessibleTenants.split(",")).contains(tenantName);
        }
        
        return false;
    }
    
    public Set<String> getAccessibleTenantsList() {
        if (accessibleTenants != null) {
            return Set.of(accessibleTenants.split(","));
        }
        return Set.of();
    }
}
