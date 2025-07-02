package dev.oasis.stockify.model;

import lombok.Data;

import jakarta.persistence.*;
import java.util.Date;

/**
 * Tenant configuration entity for storing tenant-specific settings
 */
@Entity
@Table(name = "tenant_config")
@Data
public class TenantConfig {
    
    @Id
    @Column(name = "config_key")
    private String configKey;
    
    @Column(name = "config_value")
    private String configValue;
    
    @Column(name = "config_type")
    private String configType;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "created_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;
    
    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;
}
