package dev.oasis.stockify.dto;

import dev.oasis.stockify.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for creating or updating a user
 */
@Data
public class UserCreateDTO {
    @NotBlank(message = "Kullanıcı adı boş olamaz")
    @Size(min = 3, max = 20, message = "Kullanıcı adı 3 ile 20 karakter arasında olmalıdır")
    private String username;    @NotBlank(message = "Şifre boş olamaz")
    @Size(min = 6, message = "Şifre en az 6 karakter olmalıdır")
    private String password;    
    
    @NotNull(message = "Rol boş olamaz")
    private Role role; // SUPER_ADMIN, ADMIN, USER
    
    private String email; // Optional email field
}