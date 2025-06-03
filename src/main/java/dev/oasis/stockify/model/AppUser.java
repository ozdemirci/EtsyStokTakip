package dev.oasis.stockify.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Kullanıcı adı boş olamaz")
    @Size(min = 3, max = 20, message = "Kullanıcı adı 3 ile 20 karakter arasında olmalıdır")
    @Column(unique = true, nullable = false)
    private String username;

    @NotBlank(message = "Şifre boş olamaz")
    @Size(min = 6, message = "Şifre en az 6 karakter olmalıdır")
    @Column(nullable = false)
    private String password;

    @NotBlank(message = "Rol boş olamaz")
    private String role; // "ADMIN", "DEPO", "USER"

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
