package dev.oasis.stockify.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotBlank(message = "Kullanıcı adı boş olamaz")
    @Size(min = 3, max = 20, message = "Kullanıcı adı 3 ile 20 karakter arasında olmalıdır")
    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @NotBlank(message = "Şifre boş olamaz")
    @Size(min = 6, message = "Şifre en az 6 karakter olmalıdır")
    @Column(name = "password", nullable = false)
    private String password;

    @NotBlank(message = "Rol boş olamaz")
    @Column(name = "role")
    private String role; // "ADMIN", "DEPO", "USER"

    // Audit fields
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }


}
