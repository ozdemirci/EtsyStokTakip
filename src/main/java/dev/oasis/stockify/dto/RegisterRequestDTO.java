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
    
    @NotBlank(message = "E-posta adresi boş olamaz")
    @Email(message = "Geçerli bir e-posta adresi giriniz")
    private String email;
    
    @NotBlank(message = "Şifre boş olamaz")
    @Size(min = 6, max = 100, message = "Şifre en az 6 karakter olmalıdır")
    private String password;
    
    @NotBlank(message = "Şifre tekrar boş olamaz")
    private String confirmPassword;
    
    @NotBlank(message = "Plan seçimi yapmalısınız")
    private String selectedPlan; // starter, professional, enterprise
    
    @Builder.Default
    private Boolean acceptTerms = false;
    
    // Helper method to generate username from email
    public String generateUsername() {
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf("@")).toLowerCase();
        }
        return null;
    }
}
