package dev.oasis.stockify.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for tenant information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDTO {

    private String tenantId;
    private String companyName;
    private String adminEmail;
    private String status;
    private String description;
    private String industry;
    private String contactPhone;
    private String address;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivity;
    private boolean isActive;
    private long userCount;
    private long productCount;
}
