package dev.oasis.stockify.config;

import dev.oasis.stockify.config.tenant.TenantHeaderFilter;
import dev.oasis.stockify.config.tenant.TenantSecurityFilter;
import dev.oasis.stockify.service.AppUserDetailsService;
import dev.oasis.stockify.config.tenant.TenantAwareAuthenticationSuccessHandler;
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
public class SecurityConfig {    private final AppUserDetailsService appUserDetailsService;
    private final TenantHeaderFilter tenantHeaderFilter;
    private final TenantSecurityFilter tenantSecurityFilter;
    private final TenantAwareAuthenticationSuccessHandler successHandler;

    public SecurityConfig(AppUserDetailsService appUserDetailsService,
                        TenantHeaderFilter tenantHeaderFilter,
                        TenantSecurityFilter tenantSecurityFilter,
                        TenantAwareAuthenticationSuccessHandler successHandler) {
        this.appUserDetailsService = appUserDetailsService;
        this.tenantHeaderFilter = tenantHeaderFilter;
        this.tenantSecurityFilter = tenantSecurityFilter;
        this.successHandler = successHandler;
    }

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
    }    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {        http
            .addFilterBefore(tenantHeaderFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(tenantSecurityFilter, TenantHeaderFilter.class)
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/error", "/h2-console/**").permitAll()
                .requestMatchers("/login*").permitAll()
                .requestMatchers("/superadmin/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/admin/tenants/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
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
            )
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/access-denied")
            )
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
