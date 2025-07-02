package dev.oasis.stockify.util;

import dev.oasis.stockify.config.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TenantResolutionUtilTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpSession session;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private TenantResolutionUtil tenantResolutionUtil;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.clear();
        when(request.getSession()).thenReturn(session);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void resolveTenantId_fromTenantContext_shouldReturnTenantId() {
        // Arrange
        TenantContext.setCurrentTenant("contextTenant");

        // Act
        String result = tenantResolutionUtil.resolveTenantId(request, authentication, false);

        // Assert
        assertEquals("contexttenant", result);
    }

    @Test
    void resolveTenantId_fromSession_shouldReturnTenantId() {
        // Arrange
        when(session.getAttribute("tenantId")).thenReturn("sessionTenant");

        // Act
        String result = tenantResolutionUtil.resolveTenantId(request, authentication, false);

        // Assert
        assertEquals("sessiontenant", result);
    }

    @Test
    void resolveTenantId_fromHeader_shouldReturnTenantId() {
        // Arrange
        when(request.getHeader("X-TenantId")).thenReturn("headerTenant");

        // Act
        String result = tenantResolutionUtil.resolveTenantId(request, authentication, false);

        // Assert
        assertEquals("headertenant", result);
    }

    @Test
    void resolveTenantId_fromParameter_shouldReturnTenantId() {
        // Arrange
        when(request.getParameter("tenant_id")).thenReturn("paramTenant");

        // Act
        String result = tenantResolutionUtil.resolveTenantId(request, authentication, false);

        // Assert
        assertEquals("paramtenant", result);
    }

    @Test
    void resolveTenantId_whenNoTenantFoundAndNotFailOnMissing_shouldReturnPublic() {
        // Act
        String result = tenantResolutionUtil.resolveTenantId(request, authentication, false);

        // Assert
        assertEquals("public", result);
    }

    @Test
    void resolveTenantId_whenNoTenantFoundAndFailOnMissing_shouldThrowException() {
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            tenantResolutionUtil.resolveTenantId(request, authentication, true);
        });
    }

    @Test
    void resolveTenantId_defaultMethod_shouldCallWithFalseFailOnMissing() {
        // Arrange
        when(request.getParameter("tenant_id")).thenReturn("paramTenant");

        // Act
        String result = tenantResolutionUtil.resolveTenantId(request, authentication);

        // Assert
        assertEquals("paramtenant", result);
    }

    @Test
    void getCurrentTenant_shouldReturnTenantFromContext() {
        // Arrange
        TenantContext.setCurrentTenant("testTenant");

        // Act
        String result = tenantResolutionUtil.getCurrentTenant();

        // Assert
        assertEquals("testTenant", result);
    }

    @Test
    void setupTenantContext_shouldSetTenantInContext() {
        // Arrange
        when(request.getParameter("tenant_id")).thenReturn("paramTenant");

        // Act
        tenantResolutionUtil.setupTenantContext(request);

        // Assert
        assertEquals("paramtenant", TenantContext.getCurrentTenant());
    }

    @Test
    void setCurrentTenant_shouldSetTenantInContext() {
        // Act
        tenantResolutionUtil.setCurrentTenant("explicitTenant");

        // Assert
        assertEquals("explicitTenant", TenantContext.getCurrentTenant());
    }

    @Test
    void clearCurrentTenant_shouldClearTenantFromContext() {
        // Arrange
        TenantContext.setCurrentTenant("testTenant");

        // Act
        tenantResolutionUtil.clearCurrentTenant();

        // Assert
        assertNull(TenantContext.getCurrentTenant());
    }
}
