package dev.oasis.stockify.util;

import dev.oasis.stockify.config.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class ServiceTenantUtilTest {

    @InjectMocks
    private ServiceTenantUtil serviceTenantUtil;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getCurrentTenant_whenTenantIsSet_shouldReturnTenant() {
        // Arrange
        TenantContext.setCurrentTenant("testTenant");

        // Act
        String result = serviceTenantUtil.getCurrentTenant();

        // Assert
        assertEquals("testTenant", result);
    }

    @Test
    void getCurrentTenant_whenTenantIsNotSet_shouldReturnNull() {
        // Act
        String result = serviceTenantUtil.getCurrentTenant();

        // Assert
        assertNull(result);
    }

    @Test
    void getCurrentTenantWithDefault_whenTenantIsSet_shouldReturnTenant() {
        // Arrange
        TenantContext.setCurrentTenant("testTenant");

        // Act
        String result = serviceTenantUtil.getCurrentTenant("defaultTenant", false);

        // Assert
        assertEquals("testTenant", result);
    }

    @Test
    void getCurrentTenantWithDefault_whenTenantIsNotSetAndNotFailOnMissing_shouldReturnDefault() {
        // Act
        String result = serviceTenantUtil.getCurrentTenant("defaultTenant", false);

        // Assert
        assertEquals("defaultTenant", result);
    }

    @Test
    void getCurrentTenantWithDefault_whenTenantIsNotSetAndFailOnMissing_shouldThrowException() {
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            serviceTenantUtil.getCurrentTenant("defaultTenant", true);
        });
    }

    @Test
    void setCurrentTenant_shouldSetTenantInContext() {
        // Act
        serviceTenantUtil.setCurrentTenant("newTenant");

        // Assert
        assertEquals("newTenant", TenantContext.getCurrentTenant());
    }

    @Test
    void clearCurrentTenant_shouldClearTenantFromContext() {
        // Arrange
        TenantContext.setCurrentTenant("testTenant");

        // Act
        serviceTenantUtil.clearCurrentTenant();

        // Assert
        assertNull(TenantContext.getCurrentTenant());
    }

    @Test
    void executeInTenant_shouldExecuteOperationInSpecifiedTenant() {
        // Arrange
        TenantContext.setCurrentTenant("originalTenant");

        // Act
        String result = serviceTenantUtil.executeInTenant("temporaryTenant", () -> {
            assertEquals("temporaryTenant", TenantContext.getCurrentTenant());
            return "success";
        });

        // Assert
        assertEquals("success", result);
        assertEquals("originalTenant", TenantContext.getCurrentTenant());
    }

    @Test
    void executeInTenant_whenExceptionThrown_shouldRestoreOriginalTenant() {
        // Arrange
        TenantContext.setCurrentTenant("originalTenant");

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            serviceTenantUtil.executeInTenant("temporaryTenant", () -> {
                assertEquals("temporaryTenant", TenantContext.getCurrentTenant());
                throw new RuntimeException("Test exception");
            });
        });

        // Verify original tenant is restored
        assertEquals("originalTenant", TenantContext.getCurrentTenant());
    }

    @Test
    void executeInTenant_whenNoOriginalTenant_shouldClearAfterExecution() {
        // Act
        String result = serviceTenantUtil.executeInTenant("temporaryTenant", () -> {
            assertEquals("temporaryTenant", TenantContext.getCurrentTenant());
            return "success";
        });

        // Assert
        assertEquals("success", result);
        assertNull(TenantContext.getCurrentTenant());
    }
}
