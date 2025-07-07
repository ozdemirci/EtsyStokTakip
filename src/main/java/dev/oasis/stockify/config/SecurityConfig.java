package dev.oasis.stockify.config;

import dev.oasis.stockify.service.AppUserDetailsService;
import dev.oasis.stockify.config.security.TenantHeaderFilter;
import dev.oasis.stockify.config.security.TenantSecurityFilter;
import dev.oasis.stockify.config.tenant.TenantAwareAuthenticationSuccessHandler;
import dev.oasis.stockify.model.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
@Order(2) // This will be processed after JwtSecurityConfig
public class SecurityConfig {    
    private final AppUserDetailsService appUserDetailsService;
    private final TenantHeaderFilter tenantHeaderFilter;
    private final TenantSecurityFilter tenantSecurityFilter;
    private final TenantAwareAuthenticationSuccessHandler successHandler;
    private final PasswordEncoder passwordEncoder;    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(appUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        log.info("🔒 DaoAuthenticationProvider configured with PasswordEncoder: {}", passwordEncoder.getClass().getSimpleName());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }   
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("🔒 Configuring Web Security Filter Chain...");
        
        http
            .securityMatcher(request -> !request.getRequestURI().startsWith("/api"))  // Exclude API paths
            .addFilterBefore(tenantHeaderFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(tenantSecurityFilter, TenantHeaderFilter.class)
            .authenticationProvider(authenticationProvider()) // AuthenticationProvider'ı açıkça belirt
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth                // Public endpoints
                .requestMatchers("/", "/register", "/register/**", "/login*", "/css/**", "/js/**", "/images/**", "/error", "/trial-expired", "/actuator/**", "/favicon.ico").permitAll()
                // SUPER_ADMIN can access everything 
                .requestMatchers("/superadmin/**").hasRole(Role.SUPER_ADMIN.name())
                // ADMIN area - accessible by SUPER_ADMIN and ADMIN 
                .requestMatchers("/admin/**").hasAnyRole(Role.SUPER_ADMIN.name(), Role.ADMIN.name())
                // USER area - accessible by all authenticated users with appropriate roles
                .requestMatchers("/user/**").hasAnyRole(Role.SUPER_ADMIN.name(), Role.ADMIN.name(), Role.USER.name())
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(successHandler)
                .failureHandler((request, response, exception) -> {
                    log.error("❌ LOGIN FAILED - Username: {}, Tenant: {}, Error: {}", 
                             request.getParameter("username"), 
                             request.getParameter("tenant_id"), 
                             exception.getMessage());
                    response.sendRedirect("/login?error=true");
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )            .exceptionHandling(ex -> ex
                .accessDeniedPage("/access-denied")
            )
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin())
            );        log.info("✅ Web Security Filter Chain configured successfully");
        return http.build();
    }
}
