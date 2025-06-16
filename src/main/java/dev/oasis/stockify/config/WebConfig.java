package dev.oasis.stockify.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // TenantInterceptor is disabled as TenantHeaderFilter already handles tenant context
        // registry.addInterceptor(tenantInterceptor)
        //         .excludePathPatterns("/css/**", "/js/**", "/images/**", "/error", "/h2-console/**", 
        //                            "/actuator/**", "/login", "/logout", "/admin/tenants/api/**");
    }
}
