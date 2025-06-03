package dev.oasis.stockify.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.TenantCreateDTO;
import dev.oasis.stockify.dto.TenantDTO;
import dev.oasis.stockify.service.TenantManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for TenantManagementController
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
class TenantManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TenantManagementService tenantManagementService;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void shouldShowTenantDashboard() throws Exception {
        // Given
        List<TenantDTO> mockTenants = Arrays.asList(
                createMockTenantDTO("tenant1", "Company 1", "ACTIVE"),
                createMockTenantDTO("tenant2", "Company 2", "ACTIVE")
        );
        when(tenantManagementService.getAllTenants()).thenReturn(mockTenants);

        // When & Then
        mockMvc.perform(get("/admin/tenants"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/tenant-dashboard"))
                .andExpect(model().attributeExists("tenants"))
                .andExpect(model().attribute("tenants", mockTenants));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldDenyAccessToNonSuperAdmin() throws Exception {
        // When & Then
        mockMvc.perform(get("/admin/tenants"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void shouldShowCreateTenantForm() throws Exception {
        // When & Then
        mockMvc.perform(get("/admin/tenants/create"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/tenant-form"))
                .andExpect(model().attributeExists("tenant"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void shouldCreateTenantSuccessfully() throws Exception {
        // Given
        TenantCreateDTO createDTO = new TenantCreateDTO();
        createDTO.setTenantId("new-tenant");
        createDTO.setTenantName("New Tenant Company");
        createDTO.setAdminUsername("admin");
        createDTO.setAdminPassword("password123");
        createDTO.setAdminEmail("admin@newtenant.com");

        TenantDTO responseDTO = createMockTenantDTO("new-tenant", "New Tenant Company", "ACTIVE");
        when(tenantManagementService.createTenant(any(TenantCreateDTO.class))).thenReturn(responseDTO);

        // When & Then
        mockMvc.perform(post("/admin/tenants/create")
                .with(csrf())
                .param("tenantId", "new-tenant")
                .param("tenantName", "New Tenant Company")
                .param("adminUsername", "admin")
                .param("adminPassword", "password123")
                .param("adminEmail", "admin@newtenant.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tenants?success=true"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void shouldShowTenantDetails() throws Exception {
        // Given
        String tenantId = "test-tenant";
        TenantDTO mockTenant = createMockTenantDTO(tenantId, "Test Company", "ACTIVE");
        when(tenantManagementService.getTenantById(tenantId)).thenReturn(mockTenant);

        // When & Then
        mockMvc.perform(get("/admin/tenants/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/tenant-details"))
                .andExpect(model().attributeExists("tenant"))
                .andExpect(model().attribute("tenant", mockTenant));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void shouldGetAllTenantsAsJson() throws Exception {
        // Given
        List<TenantDTO> mockTenants = Arrays.asList(
                createMockTenantDTO("tenant1", "Company 1", "ACTIVE"),
                createMockTenantDTO("tenant2", "Company 2", "INACTIVE")
        );
        when(tenantManagementService.getAllTenants()).thenReturn(mockTenants);

        // When & Then
        mockMvc.perform(get("/admin/tenants/api/tenants")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tenantId").value("tenant1"))
                .andExpect(jsonPath("$[0].tenantName").value("Company 1"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void shouldCreateTenantViaApi() throws Exception {
        // Given
        TenantCreateDTO createDTO = new TenantCreateDTO();
        createDTO.setTenantId("api-tenant");
        createDTO.setTenantName("API Tenant");
        createDTO.setAdminUsername("apiadmin");
        createDTO.setAdminPassword("password123");
        createDTO.setAdminEmail("admin@api.com");

        TenantDTO responseDTO = createMockTenantDTO("api-tenant", "API Tenant", "ACTIVE");
        when(tenantManagementService.createTenant(any(TenantCreateDTO.class))).thenReturn(responseDTO);

        // When & Then
        mockMvc.perform(post("/admin/tenants/api/tenants")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tenantId").value("api-tenant"))
                .andExpect(jsonPath("$.tenantName").value("API Tenant"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void shouldActivateTenant() throws Exception {
        // When & Then
        mockMvc.perform(put("/admin/tenants/api/tenants/test-tenant/activate")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("Tenant activated successfully"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void shouldDeactivateTenant() throws Exception {
        // When & Then
        mockMvc.perform(put("/admin/tenants/api/tenants/test-tenant/deactivate")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("Tenant deactivated successfully"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void shouldDeleteTenant() throws Exception {
        // When & Then
        mockMvc.perform(delete("/admin/tenants/api/tenants/test-tenant")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("Tenant deleted successfully"));
    }

    @Test
    void shouldRequireAuthenticationForTenantRoutes() throws Exception {
        // When & Then
        mockMvc.perform(get("/admin/tenants"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    private TenantDTO createMockTenantDTO(String tenantId, String tenantName, String status) {
        TenantDTO dto = new TenantDTO();
        dto.setTenantId(tenantId);
        dto.setTenantName(tenantName);
        dto.setStatus(status);
        dto.setAdminUsername("admin");
        dto.setCreatedAt(LocalDateTime.now());
        dto.setUpdatedAt(LocalDateTime.now());
        return dto;
    }
}
