package dev.oasis.stockify.service;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.TenantCreateDTO;
import dev.oasis.stockify.dto.TenantDTO;
import dev.oasis.stockify.exception.TenantAlreadyExistsException;
import dev.oasis.stockify.exception.TenantNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for TenantManagementService
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TenantManagementServiceIntegrationTest {

    @Autowired
    private TenantManagementService tenantManagementService;

    @Autowired
    private DataSource dataSource;

    private static final String TEST_TENANT_ID = "test-tenant-123";
    private static final String TEST_TENANT_NAME = "Test Tenant Company";

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        cleanUpTestTenant();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        cleanUpTestTenant();
    }

    @Test
    void shouldCreateTenantSuccessfully() {
        // Given
        TenantCreateDTO createDTO = new TenantCreateDTO();
        createDTO.setTenantId(TEST_TENANT_ID);
        createDTO.setTenantName(TEST_TENANT_NAME);
        createDTO.setAdminUsername("testadmin");
        createDTO.setAdminPassword("password123");
        createDTO.setAdminEmail("admin@test.com");

        // When
        TenantDTO result = tenantManagementService.createTenant(createDTO);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTenantId()).isEqualTo(TEST_TENANT_ID);
        assertThat(result.getTenantName()).isEqualTo(TEST_TENANT_NAME);
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getAdminUsername()).isEqualTo("testadmin");

        // Verify tenant exists in database
        assertThat(tenantManagementService.getAllTenants())
                .extracting(TenantDTO::getTenantId)
                .contains(TEST_TENANT_ID);
    }

    @Test
    void shouldThrowExceptionWhenCreatingDuplicateTenant() {
        // Given
        TenantCreateDTO createDTO = new TenantCreateDTO();
        createDTO.setTenantId(TEST_TENANT_ID);
        createDTO.setTenantName(TEST_TENANT_NAME);
        createDTO.setAdminUsername("testadmin");
        createDTO.setAdminPassword("password123");
        createDTO.setAdminEmail("admin@test.com");

        // Create tenant first time
        tenantManagementService.createTenant(createDTO);

        // When & Then
        assertThatThrownBy(() -> tenantManagementService.createTenant(createDTO))
                .isInstanceOf(TenantAlreadyExistsException.class)
                .hasMessageContaining(TEST_TENANT_ID);
    }

    @Test
    void shouldGetTenantById() {
        // Given
        createTestTenant();

        // When
        TenantDTO result = tenantManagementService.getTenantById(TEST_TENANT_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTenantId()).isEqualTo(TEST_TENANT_ID);
        assertThat(result.getTenantName()).isEqualTo(TEST_TENANT_NAME);
    }

    @Test
    void shouldThrowExceptionWhenGettingNonExistentTenant() {
        // When & Then
        assertThatThrownBy(() -> tenantManagementService.getTenantById("non-existent"))
                .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void shouldDeactivateTenant() {
        // Given
        createTestTenant();

        // When
        tenantManagementService.deactivateTenant(TEST_TENANT_ID);

        // Then
        TenantDTO deactivatedTenant = tenantManagementService.getTenantById(TEST_TENANT_ID);
        assertThat(deactivatedTenant.getStatus()).isEqualTo("INACTIVE");
    }

    @Test
    void shouldActivateTenant() {
        // Given
        createTestTenant();
        tenantManagementService.deactivateTenant(TEST_TENANT_ID);

        // When
        tenantManagementService.activateTenant(TEST_TENANT_ID);

        // Then
        TenantDTO activatedTenant = tenantManagementService.getTenantById(TEST_TENANT_ID);
        assertThat(activatedTenant.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldGetAllTenants() {
        // Given
        createTestTenant();

        // When
        List<TenantDTO> tenants = tenantManagementService.getAllTenants();

        // Then
        assertThat(tenants).isNotEmpty();
        assertThat(tenants)
                .extracting(TenantDTO::getTenantId)
                .contains(TEST_TENANT_ID);
    }

    @Test
    void shouldDeleteTenant() {
        // Given
        createTestTenant();

        // When
        tenantManagementService.deleteTenant(TEST_TENANT_ID);

        // Then
        assertThatThrownBy(() -> tenantManagementService.getTenantById(TEST_TENANT_ID))
                .isInstanceOf(TenantNotFoundException.class);
    }

    private void createTestTenant() {
        TenantCreateDTO createDTO = new TenantCreateDTO();
        createDTO.setTenantId(TEST_TENANT_ID);
        createDTO.setTenantName(TEST_TENANT_NAME);
        createDTO.setAdminUsername("testadmin");
        createDTO.setAdminPassword("password123");
        createDTO.setAdminEmail("admin@test.com");
        
        tenantManagementService.createTenant(createDTO);
    }

    private void cleanUpTestTenant() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // Drop test tenant schema if exists
            statement.execute("DROP SCHEMA IF EXISTS " + TEST_TENANT_ID.toUpperCase() + " CASCADE");
            
            // Remove from public tenant registry if exists
            statement.execute("DELETE FROM tenant_registry WHERE tenant_id = '" + TEST_TENANT_ID + "'");
            
        } catch (SQLException e) {
            // Ignore cleanup errors in tests
        }
    }
}
