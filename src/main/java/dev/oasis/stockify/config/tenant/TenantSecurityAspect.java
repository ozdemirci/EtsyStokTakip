package dev.oasis.stockify.config.tenant;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TenantSecurityAspect {    @Around("execution(* dev.oasis.stockify.service..*(..)) && !within(dev.oasis.stockify.service.AppUserDetailsService) && !within(dev.oasis.stockify.service.SuperAdminService)")
    public Object enforceTenantSecurity(ProceedingJoinPoint joinPoint) throws Throwable {
        String currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant == null) {
            throw new IllegalStateException("No tenant context found");
        }
        return joinPoint.proceed();
    }
}
