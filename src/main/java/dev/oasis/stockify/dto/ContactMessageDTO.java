package dev.oasis.stockify.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * DTO for contact form submission
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactMessageDTO {
    
    @NotBlank(message = "Ad alanı boş olamaz")
    @Size(max = 100, message = "Ad en fazla 100 karakter olabilir")
    private String firstName;
    
    @NotBlank(message = "Soyad alanı boş olamaz")
    @Size(max = 100, message = "Soyad en fazla 100 karakter olabilir")
    private String lastName;
    
    @NotBlank(message = "E-posta alanı boş olamaz")
    @Email(message = "Geçerli bir e-posta adresi giriniz")
    @Size(max = 255, message = "E-posta en fazla 255 karakter olabilir")
    private String email;
    
    @Size(max = 20, message = "Telefon numarası en fazla 20 karakter olabilir")
    private String phone;
    
    @Size(max = 255, message = "Şirket adı en fazla 255 karakter olabilir")
    private String company;
    
    @NotBlank(message = "Konu seçimi zorunludur")
    private String subject;
    
    @NotBlank(message = "Mesaj alanı boş olamaz")
    @Size(min = 10, message = "Mesaj en az 10 karakter olmalıdır")
    @Size(max = 2000, message = "Mesaj en fazla 2000 karakter olabilir")
    private String message;
    
    // Additional fields for tracking
    private String ipAddress;
    private String userAgent;
}
