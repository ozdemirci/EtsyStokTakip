# STOCKIFY - Multi-Tenant Inventory Management System

Stockify is a comprehensive multi-tenant inventory management system that enables complete data isolation between different companies/tenants using schema-based multi-tenancy architecture.

## 🏗️ Multi-Tenant Architecture

### Schema-Based Multi-Tenancy
STOCKIFY implements **schema-based multi-tenancy** where each tenant (company) gets its own database schema with complete data isolation:

- **Tenant Isolation**: Each tenant has a dedicated schema (e.g., `ACME_CORP`, `GLOBAL_TRADE`)
- **Data Security**: Complete data separation between tenants
- **Scalability**: Easy to add new tenants dynamically
- **Flexibility**: Each tenant can have custom configurations

### Current Tenant Examples
- `stockify` → Stockify Platform (Super Admin Tenant)
- `acme_corp` → ACME Corporation
- `global_trade` → Global Trade Solutions  
- `artisan_crafts` → Artisan Crafts Co.
- `tech_solutions` → Tech Solutions Inc.

### How It Works
1. **Schema Creation**: Each tenant gets its own schema with identical table structures
2. **Super Admin Tenant**: `stockify` schema hosts the super admin user for cross-tenant management
3. **Connection Provider**: `SchemaMultiTenantConnectionProvider` manages schema switching
4. **Tenant Context**: `TenantContext` maintains current tenant information per request
5. **Header-Based Routing**: `X-TenantId` header determines which tenant's data to access

## 🚀 Features

### Core Functionality
- **Multi-Tenant Product Management**
  - Create, read, update, and delete products per tenant
  - SKU-based unique identification within tenant scope
  - Category-based organization
  - Stock level tracking with tenant-specific thresholds
  - Etsy integration support per tenant

- **Tenant-Isolated User Management**
  - Role-based access control per tenant
  - Tenant-specific admin users
  - Super admin for cross-tenant management

- **Advanced Stock Monitoring**
  - Tenant-specific stock level notifications
  - Configurable low stock thresholds per tenant
  - Email notification system

- **Tenant Management**
  - Dynamic tenant creation/activation/deactivation
  - Tenant-specific configurations
  - Tenant dashboard and analytics

### Multi-Tenant Demo Features
- **Tenant Data Comparison**: Compare data isolation between tenants
- **Schema Inspection**: View all tenant schemas and their structures
- **Cross-Tenant Security**: Validate that tenants cannot access each other's data

## 🛠️ Development

### Prerequisites
- Java 17 or later
- Maven 3.6+
- H2 Database (development) 
- PostgreSQL (production)

### Quick Start

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd STOCKIFY
   ```

2. **Run the application**
   ```bash
   # Development mode (H2 with sample tenants)
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

3. **Access the application**
   - Main App: http://localhost:8080
   - H2 Console: http://localhost:8080/h2-console
   - Health Check: http://localhost:8080/actuator/health

### Multi-Tenant Testing

Use the provided `multi-tenant-test.http` file to test:

```http
# List all schemas
GET http://localhost:8080/api/demo/schemas

# Get tenant-specific data
GET http://localhost:8080/api/demo/tenant/acme_corp/data
X-TenantId: acme_corp

# Compare tenant isolation
GET http://localhost:8080/api/demo/compare/acme_corp/vs/global_trade
```

### Database Schema Verification

Connect to H2 Console and verify schema isolation:
- URL: `jdbc:h2:mem:stockifydb`
- Username: `sa` 
- Password: (empty)

You should see separate schemas:
- `STOCKIFY` (super admin tenant)
- `ACME_CORP` (tenant 1)
- `GLOBAL_TRADE` (tenant 2)
- `ARTISAN_CRAFTS` (tenant 3)
- `TECH_SOLUTIONS` (tenant 4)

### Super Admin Access

The super admin user is created in the `stockify` tenant:
- **Tenant**: `stockify`
- **Username**: `superadmin`
- **Password**: `superadmin123`
- **Role**: `SUPER_ADMIN`

Super admin can manage all tenants and access cross-tenant functionality.

### Database Migration

The application uses Flyway for database migrations. Migration scripts are located in `src/main/resources/db/migration/`.

To create a new migration:
1. Create a new SQL file in the migration directory
2. Name it following the pattern: `V{number}__{description}.sql`
3. Write your migration SQL
4. Run the application - migrations will be applied automatically

### Testing

Run tests with:
```bash
mvn test
```

## Import/Export Format

Products can be imported/exported using CSV files with the following columns:
- Name
- Description
- SKU
- Price
- Quantity
- Category

Example:
```csv
Name,Description,SKU,Price,Quantity,Category
Sample Product,Sample product description,SKU001,99.99,100,Electronics
```
