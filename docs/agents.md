The project is a Spring Boot application named Stockify, designed as a multi‑tenant inventory management system. The README describes the goal clearly:

Stockify is a comprehensive multi-tenant inventory management system that enables complete data isolation between different companies/tenants using schema-based multi-tenancy architecture.

Tenants are isolated by schema, with examples such as stockify, acme_corp, and global_trade. The README outlines how the system works:

1. **Schema Creation**: Each tenant gets its own schema with identical table structures  
2. **Super Admin Tenant**: `stockify` schema hosts the super admin user for cross-tenant management  
3. **Connection Provider**: `SchemaMultiTenantConnectionProvider` manages schema switching  
4. **Tenant Context**: `TenantContext` maintains current tenant information per request  
5. **Header-Based Routing**: `X-TenantId` header determines which tenant's data to access

Project structure
The main packages under src/main/java/dev/oasis/stockify are:

config/ – Spring configuration including multi‑tenant setup (Hibernate configuration, security, Flyway migration).

controller/ – MVC and REST controllers (e.g., ProductController, TenantManagementController).

service/ – Business logic for users, products, tenants, notifications, etc.

repository/ – Spring Data JPA repositories.

model/ – Entity classes (AppUser, Product, StockNotification).

dto/ and mapper/ – Data transfer objects and mapping helpers.

The entry point is Stockify.java.

Multi‑tenancy
The multi-tenant setup uses a thread‑local context and schema switching. Key components include:

TenantContext – Holds the current tenant for the request.

SchemaMultiTenantConnectionProvider – Sets the connection’s schema before queries are executed.

CurrentTenantIdentifierResolverImpl – Supplies Hibernate with the current tenant ID.

TenantHeaderFilter and TenantSecurityFilter – Extract tenant identifiers from requests and validate them.

Flyway migrations run for each schema via MultiTenantFlywayConfig. The config iterates over all tenant schemas and creates schema-specific flyway history tables.

Sample data
A DataLoader component (active in the dev profile) creates schemas and inserts sample users and products for several tenants.

Controllers and services
ProductController exposes CRUD and import/export endpoints for products, delegating to ProductService.

TenantManagementController allows a super admin to create, activate, or deactivate tenants and provides REST endpoints for tenant data.

MultiTenantDemoController offers endpoints demonstrating schema isolation and listing schemas.

Services implement the business rules (e.g., ProductService, TenantManagementService, StockNotificationService). The service layer uses repositories (e.g., ProductRepository) for persistence.

Database schema
src/main/resources/db/migration/V1__init_schema.sql defines the tables app_user, product, stock_notification, tenant_config, etc. Each tenant has the same structure in its own schema.

Documentation
The docs/ directory includes several guides:

multi-tenancy-guide.md – explains the tenant architecture and usage examples.

tasks.md – lists planned improvements, such as layered architecture enforcement, global exception handling, caching, and i18n.

Next steps for a new contributor
Understand request flow: See how TenantHeaderFilter and TenantSecurityFilter establish tenant context before controllers run.

Study services and repositories: Each service encapsulates business rules and data access via JPA repositories.

Review database migrations: Flyway scripts define the schema used for each tenant.

Check docs/tasks.md for outstanding improvements and priorities.

Run in dev mode following the README instructions to explore the sample tenants and API endpoints.

This should provide a starting point for extending or debugging the multi‑tenant features. The documentation folder offers deeper guidance on migration and future enhancements.
