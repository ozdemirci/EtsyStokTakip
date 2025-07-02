package dev.oasis.stockify.repository;

import dev.oasis.stockify.model.TenantConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for TenantConfig entity
 */
@Repository
public interface TenantConfigRepository extends JpaRepository<TenantConfig, String> {
    
    /**
     * Find tenant configuration by key
     * 
     * @param configKey The configuration key
     * @return Optional containing the tenant configuration if found
     */
    Optional<TenantConfig> findByConfigKey(String configKey);
}
