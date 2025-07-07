package dev.oasis.stockify.config.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
@Order(1) // This will be processed before the main SecurityConfig
public class JwtSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("🔒 Configuring JWT API Security Filter Chain...");
        
        http
            .securityMatcher("/api/**")  // This configuration applies only to /api/** paths
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()  // Auth endpoints are public
                .requestMatchers("/api/admin/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                .requestMatchers("/api/user/**").hasAnyRole("SUPER_ADMIN", "ADMIN", "USER")
                .requestMatchers("/api/superadmin/**").hasRole("SUPER_ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    log.error("❌ JWT Authentication failed: {}", authException.getMessage());
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    log.error("❌ JWT Access denied: {}", accessDeniedException.getMessage());
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied");
                })
            );

        log.info("✅ JWT API Security Filter Chain configured successfully");
        return http.build();
    }
}
