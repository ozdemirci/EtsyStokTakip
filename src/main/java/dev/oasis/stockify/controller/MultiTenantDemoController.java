package dev.oasis.stockify.controller;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.model.ProductCategory;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.repository.ProductRepository;
import dev.oasis.stockify.repository.ProductCategoryRepository;
import dev.oasis.stockify.service.ProductCategoryService;
import dev.oasis.stockify.dto.ProductCategoryResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo controller to showcase multi-tenant schema isolation
 * This controller demonstrates how different tenants have completely isolated data
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@Slf4j
public class MultiTenantDemoController {

    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductCategoryService productCategoryService;
    private final DataSource dataSource;

    /**
     * Get current tenant information
     */
    @GetMapping("/current-tenant")
    public ResponseEntity<Map<String, Object>> getCurrentTenant() {
        String currentTenant = TenantContext.getCurrentTenant();
        Map<String, Object> response = new HashMap<>();
        
        response.put("currentTenant", currentTenant != null ? currentTenant : "NONE");
        response.put("threadInfo", Thread.currentThread().getName());
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get data for specific tenant (demonstrates tenant isolation)
     */
    @GetMapping("/tenant/{tenantId}/data")
    public ResponseEntity<Map<String, Object>> getTenantData(@PathVariable String tenantId) {
        log.info("🔍 Getting data for tenant: {}", tenantId);
        
        try {
            // Set tenant context
            TenantContext.setCurrentTenant(tenantId);
            
            // Get tenant-specific data
            List<AppUser> users = appUserRepository.findAll();
            List<Product> products = productRepository.findAll();
            
            Map<String, Object> response = new HashMap<>();
            response.put("tenantId", tenantId);
            response.put("userCount", users.size());
            response.put("productCount", products.size());
            response.put("users", users.stream()
                .map(u -> Map.of(
                    "id", u.getId(),
                    "username", u.getUsername(),
                    "role", u.getRole(),
                    "active", u.getIsActive()
                )).toList());
            response.put("products", products.stream()
                .map(p -> Map.of(
                    "id", p.getId(),
                    "sku", p.getSku(),
                    "title", p.getTitle(),
                    "category", p.getCategory(),
                    "stockLevel", p.getStockLevel()
                )).toList());
            
            log.info("✅ Retrieved {} users and {} products for tenant: {}", 
                users.size(), products.size(), tenantId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error getting data for tenant {}: {}", tenantId, e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get tenant data: " + e.getMessage()));
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * List all available schemas (tenants) in H2 database
     */
    @GetMapping("/schemas")
    public ResponseEntity<Map<String, Object>> getAllSchemas() {
        log.info("🗂️ Listing all database schemas");
        
        List<Map<String, Object>> schemas = new ArrayList<>();
        
        try (Connection connection = dataSource.getConnection()) {
            ResultSet schemasRs = connection.getMetaData().getSchemas();
            
            while (schemasRs.next()) {
                String schemaName = schemasRs.getString("TABLE_SCHEM");
                
                // Skip system schemas
                if (!isSystemSchema(schemaName)) {
                    Map<String, Object> schemaInfo = new HashMap<>();
                    schemaInfo.put("name", schemaName);
                    schemaInfo.put("isPublic", "public".equalsIgnoreCase(schemaName));
                    
                    // Get table count for this schema
                    try {
                        connection.setSchema(schemaName);
                        ResultSet tablesRs = connection.getMetaData().getTables(null, schemaName, null, new String[]{"TABLE"});
                        int tableCount = 0;
                        while (tablesRs.next()) {
                            tableCount++;
                        }
                        schemaInfo.put("tableCount", tableCount);
                        tablesRs.close();
                    } catch (SQLException e) {
                        log.warn("⚠️ Could not get table count for schema {}: {}", schemaName, e.getMessage());
                        schemaInfo.put("tableCount", -1);
                    }
                    
                    schemas.add(schemaInfo);
                }
            }
            schemasRs.close();
            
        } catch (SQLException e) {
            log.error("❌ Error listing schemas: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to list schemas: " + e.getMessage()));
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("schemas", schemas);
        response.put("totalCount", schemas.size());
        response.put("timestamp", System.currentTimeMillis());
        
        log.info("✅ Found {} schemas", schemas.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Debug endpoint to retrieve categories for the current tenant
     */
    @GetMapping("/tenant/categories")
    public ResponseEntity<Map<String, Object>> getTenantCategories() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🔍 Debugging categories for tenant: {}", tenantId);
        
        try {
            // Set tenant context
            TenantContext.setCurrentTenant(tenantId);
            
            // Get all categories for the tenant
            List<ProductCategory> categories = productCategoryRepository.findAll();
            
            Map<String, Object> response = new HashMap<>();
            response.put("tenantId", tenantId);
            response.put("categoryCount", categories.size());            response.put("categories", categories.stream()
                .map(c -> Map.of(
                    "id", c.getId(),
                    "name", c.getName(),
                    "description", c.getDescription(),
                    "hexColor", c.getHexColor(),
                    "isActive", c.getIsActive(),
                    "sortOrder", c.getSortOrder()
                )).toList());
            
            log.info("✅ Retrieved {} categories for tenant: {}", categories.size(), tenantId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error retrieving categories for tenant {}: {}", tenantId, e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve categories: " + e.getMessage()));
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isSystemSchema(String schemaName) {
        return schemaName.equalsIgnoreCase("INFORMATION_SCHEMA") ||
               schemaName.equalsIgnoreCase("SYSTEM_LOBS") ||
               schemaName.equalsIgnoreCase("SYS") ||
               schemaName.equalsIgnoreCase("SYSAUX");
    }
}
