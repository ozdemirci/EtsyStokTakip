package com.yourcompany.etsystoktakip.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.yourcompany.etsystoktakip.service.AppUserDetailsService;

@Configuration
public class SecurityConfig {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
    
    @Autowired
    private AppUserDetailsService appUserDetailsService;

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(appUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        logger.debug("Configuring DaoAuthenticationProvider with AppUserDetailsService and BCryptPasswordEncoder");
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/error", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/css/**", "/js/**", "/images/**", "/webjars/**", "/users/add").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/depo/**").hasAnyRole("ADMIN", "DEPO")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .defaultSuccessUrl("/products", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout=true")
                .permitAll()
            )
            .authenticationProvider(authenticationProvider());

        logger.debug("Configured SecurityFilterChain with custom authentication provider");
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        logger.debug("Created new BCryptPasswordEncoder instance");
        return encoder;
    }
}
