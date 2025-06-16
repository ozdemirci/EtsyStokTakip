package dev.oasis.stockify.repository;

import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    
    List<AppUser> findByRole(Role role);
    
    List<AppUser> findByIsActive(Boolean isActive);
    
    long countByIsActive(Boolean isActive);
    
    @Query("SELECT u FROM AppUser u WHERE u.canManageAllTenants = true")
    List<AppUser> findSuperAdmins();
    
    @Query("SELECT u FROM AppUser u WHERE u.isGlobalUser = true")
    List<AppUser> findGlobalUsers();
    
    // Tenant-aware queries (fallback if multi-tenant schema doesn't work)
    @Query("SELECT u FROM AppUser u WHERE u.primaryTenant = :tenantId")
    List<AppUser> findByPrimaryTenant(@Param("tenantId") String tenantId);
    
    @Query("SELECT COUNT(u) FROM AppUser u WHERE u.primaryTenant = :tenantId")
    long countByPrimaryTenant(@Param("tenantId") String tenantId);
    
    @Query("SELECT COUNT(u) FROM AppUser u WHERE u.primaryTenant = :tenantId AND u.isActive = :isActive")
    long countByPrimaryTenantAndIsActive(@Param("tenantId") String tenantId, @Param("isActive") Boolean isActive);
}
