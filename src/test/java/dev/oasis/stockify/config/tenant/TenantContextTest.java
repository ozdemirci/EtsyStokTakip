package dev.oasis.stockify.config.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TenantContext
 */
class TenantContextTest {

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldSetAndGetCurrentTenant() {
        // Given
        String tenantId = "test-tenant";

        // When
        TenantContext.setCurrentTenant(tenantId);

        // Then
        assertThat(TenantContext.getCurrentTenant()).isEqualTo(tenantId);
    }

    @Test
    void shouldReturnNullWhenNoTenantSet() {
        // When & Then
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void shouldClearTenantContext() {
        // Given
        TenantContext.setCurrentTenant("test-tenant");

        // When
        TenantContext.clear();

        // Then
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void shouldHandleConcurrentTenantContexts() throws InterruptedException {
        // Given
        String tenant1 = "tenant1";
        String tenant2 = "tenant2";
        
        // When - simulate concurrent access
        Thread thread1 = new Thread(() -> {
            TenantContext.setCurrentTenant(tenant1);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            assertThat(TenantContext.getCurrentTenant()).isEqualTo(tenant1);
        });

        Thread thread2 = new Thread(() -> {
            TenantContext.setCurrentTenant(tenant2);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            assertThat(TenantContext.getCurrentTenant()).isEqualTo(tenant2);
        });

        thread1.start();
        thread2.start();
        
        thread1.join();
        thread2.join();
        
        // Then - main thread should not be affected
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }
}
