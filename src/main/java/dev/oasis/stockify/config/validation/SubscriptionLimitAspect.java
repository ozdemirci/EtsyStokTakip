package dev.oasis.stockify.config.validation;

import dev.oasis.stockify.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Aspect for validating subscription limits
 * Excludes DataLoader from subscription checks to allow initial data seeding
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class SubscriptionLimitAspect {
    
    private final SubscriptionService subscriptionService;
    
    /**
     * Check user creation limits
     * Excludes DataLoader from subscription limits
     */
    @Around("execution(* dev.oasis.stockify.service.AppUserService.saveUser(..)) && !within(dev.oasis.stockify.config.DataLoader)")
    public Object checkUserLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        // Check if call is coming from DataLoader via stack trace as backup
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (element.getClassName().contains("DataLoader")) {
                log.debug("🔧 DataLoader detected - bypassing user creation limit");
                return joinPoint.proceed();
            }
        }
        
        if (!subscriptionService.canCreateUser()) {
            log.warn("❌ User creation blocked - subscription limit reached");
            throw new RuntimeException("Kullanıcı oluşturma limiti aşıldı. Aboneliğinizi yükseltin.");
        }
        
        log.debug("✅ User creation allowed");
        return joinPoint.proceed();
    }
    
    /**
     * Check product creation limits
     * Excludes DataLoader from subscription limits
     */
    @Around("execution(* dev.oasis.stockify.service.ProductService.save*(..)) && !within(dev.oasis.stockify.config.DataLoader)")
    public Object checkProductLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        // Check if call is coming from DataLoader via stack trace as backup
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (element.getClassName().contains("DataLoader")) {
                log.debug("🔧 DataLoader detected - bypassing product creation limit");
                return joinPoint.proceed();
            }
        }
        
        if (!subscriptionService.canCreateProduct()) {
            log.warn("❌ Product creation blocked - subscription limit reached");
            throw new RuntimeException("Ürün oluşturma limiti aşıldı. Aboneliğinizi yükseltin.");
        }
        
        log.debug("✅ Product creation allowed");
        return joinPoint.proceed();
    }
}
