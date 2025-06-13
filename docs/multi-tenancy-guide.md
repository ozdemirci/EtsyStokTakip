# STOCKIFY Multi-Tenant Architecture Guide

## Overview

STOCKIFY implements a comprehensive **schema-based multi-tenancy** architecture that provides complete data isolation between different tenant organizations. Each tenant operates in its own database schema with identical table structures but completely separate data.

## Architecture Components

### 1. Tenant Context Management
- **TenantContext**: Thread-local storage for current tenant identifier
- **TenantHeaderFilter**: Extracts tenant ID from HTTP headers
- **TenantInterceptor**: Web request-level tenant management

### 2. Database Schema Isolation
- **SchemaMultiTenantConnectionProvider**: Manages database connections per tenant schema
- **CurrentTenantIdentifierResolverImpl**: Resolves tenant ID for Hibernate
- **Dynamic Schema Creation**: Automatic schema creation for new tenants

### 3. Security & Validation
- **TenantSecurityFilter**: Validates tenant existence and access rights
- **Cross-Tenant Access Prevention**: Ensures data isolation
- **Super Admin Role**: Cross-tenant management capabilities

### 4. Tenant Lifecycle Management
- **TenantManagementService**: Complete tenant CRUD operations
- **Dynamic Tenant Registration**: Runtime tenant creation
- **Tenant Configuration**: Per-tenant settings and preferences

## Schema Structure

Each tenant schema contains identical table structures:

```sql
-- Tenant Schema: ACME_CORP, GLOBAL_TRADE, etc.
CREATE SCHEMA IF NOT EXISTS {TENANT_NAME};

-- Tables per tenant schema:
- app_user (tenant-specific users)
- product (tenant-specific products)
- stock_notification (tenant-specific notifications)
- tenant_config (tenant-specific configurations)
```

## Configuration

### application-dev.properties
```properties
# H2 Database for Development
spring.datasource.url=jdbc:h2:mem:stockifydb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false

# Multi-tenancy Hibernate Configuration
spring.jpa.properties.hibernate.multiTenancy=SCHEMA
spring.jpa.properties.hibernate.tenant_identifier_resolver=dev.oasis.stockify.config.tenant.CurrentTenantIdentifierResolverImpl
spring.jpa.properties.hibernate.multi_tenant_connection_provider=dev.oasis.stockify.config.tenant.SchemaMultiTenantConnectionProvider
```

### application-prod.properties
```properties
# PostgreSQL for Production
spring.datasource.url=jdbc:postgresql://localhost:5432/stockify
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

# Same multi-tenancy configuration as dev
spring.jpa.properties.hibernate.multiTenancy=SCHEMA
spring.jpa.properties.hibernate.tenant_identifier_resolver=dev.oasis.stockify.config.tenant.CurrentTenantIdentifierResolverImpl
spring.jpa.properties.hibernate.multi_tenant_connection_provider=dev.oasis.stockify.config.tenant.SchemaMultiTenantConnectionProvider
```

## Usage Examples

### 1. Tenant-Specific Data Access
```java
// Set tenant context
TenantContext.setCurrentTenant("acme_corp");

// All repository operations now work on ACME_CORP schema
List<Product> products = productRepository.findAll();
List<AppUser> users = userRepository.findAll();

// Clear context when done
TenantContext.clear();
```

### 2. HTTP Header-Based Tenant Routing
```http
GET /api/products
X-TenantId: acme_corp
```

### 3. Tenant Management API
```http
# Create new tenant
POST /api/tenants
{
  "companyName": "New Company",
  "adminUsername": "admin",
  "adminPassword": "password123",
  "adminEmail": "admin@company.com"
}

# List all tenants
GET /api/tenants

# Get specific tenant
GET /api/tenants/acme_corp
```

## Data Isolation Verification

### 1. Schema Inspection
```http
GET /api/demo/schemas
```
Returns all database schemas showing tenant isolation.

### 2. Tenant Data Comparison
```http
GET /api/demo/compare/acme_corp/vs/global_trade
```
Demonstrates that tenants have completely separate data.

### 3. Cross-Tenant Access Test
```http
# Request ACME_CORP data
GET /api/demo/tenant/acme_corp/data
X-TenantId: acme_corp

# Request GLOBAL_TRADE data
GET /api/demo/tenant/global_trade/data  
X-TenantId: global_trade
```
Each request returns different data sets, proving isolation.

## Security Considerations

### 1. Tenant Validation
- All tenant IDs are validated before database access
- Invalid tenants receive 404 responses
- Tenant existence is verified via schema checking

### 2. Access Control
- Each tenant has its own admin users
- Super admin can access all tenants
- Regular users are restricted to their tenant's data

### 3. Data Isolation
- Database-level isolation via schemas
- Application-level validation
- No shared data between tenants (except super admin)

## Monitoring & Debugging

### 1. Tenant Context Logging
```properties
logging.level.dev.oasis.stockify.config.tenant=TRACE
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} [tenant:%X{tenantId}] %-5level %logger{36} - %msg%n
```

### 2. H2 Console (Development)
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:stockifydb`
- Username: `sa`
- Password: (empty)

### 3. Actuator Endpoints
- Health: `/actuator/health`
- Metrics: `/actuator/metrics`
- Custom tenant metrics available

## Deployment

### Development
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Production
```bash
# Set environment variables
export DB_USERNAME=your_db_user
export DB_PASSWORD=your_db_password
export SPRING_PROFILES_ACTIVE=prod

# Run application
mvn spring-boot:run
```

## Troubleshooting

### Common Issues

1. **Schema Not Found**
   - Ensure tenant exists in database
   - Check tenant ID case sensitivity
   - Verify schema creation in logs

2. **Data Not Isolated**
   - Check TenantContext is properly set
   - Verify tenant header is being sent
   - Ensure connection provider is switching schemas

3. **Performance Issues**
   - Monitor connection pool usage
   - Check schema switching frequency
   - Consider connection caching strategies

### Debug Commands
```bash
# Check application logs
tail -f logs/stockify.log | grep -i tenant

# Monitor H2 console for schema activity
# Connect to H2 console and run:
SHOW SCHEMAS;
```

## Best Practices

1. **Always Clear Tenant Context**: Use try-finally blocks
2. **Validate Tenant Headers**: Check for missing/invalid tenant IDs
3. **Monitor Schema Performance**: Track schema switching overhead
4. **Backup Strategy**: Consider per-tenant backup schedules
5. **Testing**: Always test tenant isolation in your scenarios

## Future Enhancements

- Tenant-specific custom fields
- Tenant usage analytics and billing
- Tenant data export/import capabilities
- Advanced tenant security policies
- Multi-region tenant distribution
