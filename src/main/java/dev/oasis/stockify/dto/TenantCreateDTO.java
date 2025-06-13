package dev.oasis.stockify.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating new tenants
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantCreateDTO {

    @NotBlank(message = "Company name is required")
    @Size(min = 2, max = 100, message = "Company name must be between 2 and 100 characters")
    private String companyName;

    @NotBlank(message = "Admin username is required")
    @Size(min = 3, max = 20, message = "Admin username must be between 3 and 20 characters")
    private String adminUsername;

    @NotBlank(message = "Admin password is required")
    @Size(min = 6, max = 100, message = "Admin password must be between 6 and 100 characters")
    private String adminPassword;

    @NotBlank(message = "Admin email is required")
    @Email(message = "Admin email must be valid")
    private String adminEmail;

    private String description;
    private String industry;
    private String contactPhone;
    private String address;
}
