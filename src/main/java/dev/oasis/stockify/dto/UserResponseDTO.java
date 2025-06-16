package dev.oasis.stockify.dto;

import dev.oasis.stockify.model.Role;
import lombok.Data;

/**
 * DTO for displaying user information
 * Excludes sensitive information like passwords
 */
@Data
public class UserResponseDTO {
    private Long id;
    private String username;
    private Role role;
    private String email;
    private Boolean isActive;
}