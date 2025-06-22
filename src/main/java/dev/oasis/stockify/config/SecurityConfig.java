package dev.oasis.stockify.config;

import dev.oasis.stockify.config.tenant.TenantHeaderFilter;
import dev.oasis.stockify.config.tenant.TenantSecurityFilter;
import dev.oasis.stockify.service.AppUserDetailsService;
import dev.oasis.stockify.config.tenant.TenantAwareAuthenticationSuccessHandler;
import dev.oasis.stockify.model.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final AppUserDetailsService appUserDetailsService;
    private final TenantHeaderFilter tenantHeaderFilter;
    private final TenantSecurityFilter tenantSecurityFilter;   
    private final TenantAwareAuthenticationSuccessHandler successHandler;

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(appUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }   
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("🔒 Configuring Security Filter Chain...");
        
        http
            .addFilterBefore(tenantHeaderFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(tenantSecurityFilter, TenantHeaderFilter.class)             .csrf(csrf -> csrf.disable())            .authorizeHttpRequests(auth -> auth                // Public endpoints
                .requestMatchers("/", "/register", "/login*", "/css/**", "/js/**", "/images/**", "/error", "/h2-console/**").permitAll()               
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
            );

        log.info("✅ Security Filter Chain configured successfully");
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
