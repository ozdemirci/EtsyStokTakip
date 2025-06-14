package dev.oasis.stockify.repository;

import dev.oasis.stockify.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    
    List<AppUser> findByRole(String role);
    
    List<AppUser> findByIsActive(Boolean isActive);
    
    long countByIsActive(Boolean isActive);
    
    @Query("SELECT u FROM AppUser u WHERE u.canManageAllTenants = true")
    List<AppUser> findSuperAdmins();
    
    @Query("SELECT u FROM AppUser u WHERE u.isGlobalUser = true")
    List<AppUser> findGlobalUsers();
}
