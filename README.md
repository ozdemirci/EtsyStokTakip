# STOCKIFY

Stockify is an inventory management system that helps you track your products and manage stock levels.

## Features

- Product Management
  - Create, read, update, and delete products
  - Each product has:
    - Title (required)
    - Description (up to 1000 characters)
    - SKU (unique identifier, required)
    - Category
    - Price
    - Stock Level
    - Low Stock Threshold
    - Etsy Product ID (optional)
- Stock Level Monitoring
  - Automatic notifications when stock falls below threshold
  - Email notifications
- User Management
  - Role-based access control
  - User authentication and authorization
- Import/Export
  - CSV import/export support
  - Bulk product management

## Development

### Prerequisites

- Java 17 or later
- Maven
- PostgreSQL (for production)
- H2 (for development)

### Setup

1. Clone the repository
2. Configure application properties
   - Development: `src/main/resources/application-dev.properties`
   - Production: `src/main/resources/application-prod.properties`
3. Run the application:
   ```bash
   # Development
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   
   # Production
   mvn spring-boot:run -Dspring-boot.run.profiles=prod
   ```

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
