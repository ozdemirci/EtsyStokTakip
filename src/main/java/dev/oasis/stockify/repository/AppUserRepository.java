package dev.oasis.stockify.repository;

import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    
    Optional<AppUser> findByEmail(String email);
    
    List<AppUser> findByRole(Role role);
    
    List<AppUser> findByIsActive(Boolean isActive);
    
    long countByIsActive(Boolean isActive);
    
    @Query("SELECT u FROM AppUser u WHERE u.canManageAllTenants = true")
    List<AppUser> findSuperAdmins();
    
    @Query("SELECT u FROM AppUser u WHERE u.isGlobalUser = true")
    List<AppUser> findGlobalUsers();
    
    // Username existence checks
    boolean existsByUsername(String username);
    
    @Query("SELECT COUNT(u) > 0 FROM AppUser u WHERE u.username = :username AND u.primaryTenant = :tenantId")
    boolean existsByUsernameAndPrimaryTenant(@Param("username") String username, @Param("tenantId") String tenantId);

    // Email existence checks
    boolean existsByEmail(String email);
    
    @Query("SELECT COUNT(u) > 0 FROM AppUser u WHERE u.email = :email AND u.primaryTenant = :tenantId")
    boolean existsByEmailAndPrimaryTenant(@Param("email") String email, @Param("tenantId") String tenantId);

    // Tenant-aware queries (fallback if multi-tenant schema doesn't work)
    @Query("SELECT u FROM AppUser u WHERE u.primaryTenant = :tenantId")
    List<AppUser> findByPrimaryTenant(@Param("tenantId") String tenantId);
    
    @Query("SELECT COUNT(u) FROM AppUser u WHERE u.primaryTenant = :tenantId")
    long countByPrimaryTenant(@Param("tenantId") String tenantId);
    
    @Query("SELECT COUNT(u) FROM AppUser u WHERE u.primaryTenant = :tenantId AND u.isActive = :isActive")
    long countByPrimaryTenantAndIsActive(@Param("tenantId") String tenantId, @Param("isActive") Boolean isActive);
    
    @Query("SELECT u FROM AppUser u WHERE u.isActive = true")
    List<AppUser> findAllActiveUsers();
    
    @Query("SELECT u FROM AppUser u WHERE u.isActive = true")
    Page<AppUser> findAllActiveUsers(Pageable pageable);
    
    @Query("SELECT u FROM AppUser u WHERE u.isActive = true AND u.primaryTenant = :tenantId")
    List<AppUser> findActiveUsersByPrimaryTenant(@Param("tenantId") String tenantId);
    
    @Query("SELECT u FROM AppUser u WHERE u.isActive = true AND u.primaryTenant = :tenantId")
    Page<AppUser> findActiveUsersByPrimaryTenant(@Param("tenantId") String tenantId, Pageable pageable);
    
    @Query("SELECT u FROM AppUser u WHERE u.isActive = true AND u.username LIKE %:search%")
    List<AppUser> searchActiveUsers(@Param("search") String search);
    
    @Query("SELECT u FROM AppUser u WHERE u.isActive = true AND u.username LIKE %:search%")
    Page<AppUser> searchActiveUsers(@Param("search") String search, Pageable pageable);
}
