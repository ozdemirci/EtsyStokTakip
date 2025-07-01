package dev.oasis.stockify.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user registration form
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestDTO {
    
    @NotBlank(message = "Ad boş olamaz")
    @Size(min = 2, max = 50, message = "Ad 2 ile 50 karakter arasında olmalıdır")
    private String firstName;
    
    @NotBlank(message = "Soyad boş olamaz")
    @Size(min = 2, max = 50, message = "Soyad 2 ile 50 karakter arasında olmalıdır")
    private String lastName;
    
    @NotBlank(message = "Şirket adı boş olamaz")
    @Size(min = 2, max = 100, message = "Şirket adı 2 ile 100 karakter arasında olmalıdır")
    private String companyName;

    @NotBlank(message = "Kullanıcı adı boş olamaz")
    @Size(min = 3, max = 20, message = "Kullanıcı adı 3 ile 20 karakter arasında olmalıdır")
    private String username;
    
    @NotBlank(message = "E-posta adresi boş olamaz")
    @Email(message = "Geçerli bir e-posta adresi giriniz")
    private String email;
    
    @NotBlank(message = "Şifre boş olamaz")
    @Size(min = 6, max = 100, message = "Şifre en az 6 karakter olmalıdır")
    private String password;
    
    @NotBlank(message = "Şifre tekrar boş olamaz")
    private String confirmPassword;
    
    @NotBlank(message = "Plan seçimi yapmalısınız")
    private String selectedPlan; // trial, basic, premium, enterprise
    
    @Builder.Default
    private Boolean acceptTerms = false;
}
