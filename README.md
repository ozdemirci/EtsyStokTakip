# 🏢 STOCKIFY - Multi-Tenant Inventory Management System

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.1-brightgreen.svg)
![Java](https://img.shields.io/badge/Java-17-orange.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+-blue.svg)
![Maven](https://img.shields.io/badge/Maven-3.6+-red.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

## 📋 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Architecture](#-architecture)
- [Prerequisites](#-prerequisites)
- [Installation](#-installation)
- [Configuration](#-configuration)
- [Usage](#-usage)
- [API Documentation](#-api-documentation)
- [Multi-Tenancy](#-multi-tenancy)
- [Security](#-security)
- [Development](#-development)
- [Deployment](#-deployment)
- [Contributing](#-contributing)
- [License](#-license)

## 🎯 Overview

**Stockify** is a comprehensive, enterprise-grade inventory management system built with Spring Boot 3.2 and Java 17. It features a sophisticated multi-tenant architecture using PostgreSQL schema-based isolation, providing complete data separation for different organizations while maintaining optimal performance.

### Key Highlights

- **Multi-Tenant Architecture**: Schema-based isolation with PostgreSQL
- **Role-Based Access Control**: SUPER_ADMIN, ADMIN, USER roles with fine-grained permissions
- **Advanced Analytics**: Stock movement predictions and trend analysis
- **Real-time Notifications**: Low stock alerts and system notifications
- **Bulk Operations**: CSV import/export capabilities
- **Responsive UI**: Modern Bootstrap-based interface with Thymeleaf templates
- **RESTful APIs**: Comprehensive REST endpoints for integration

## 🚀 Features

### 📦 Inventory Management
- **Product Management**: Full CRUD operations with categories, SKUs, and descriptions
- **Stock Tracking**: Real-time stock levels with movement history
- **Stock Movements**: IN, OUT, ADJUSTMENT, DAMAGED, EXPIRED movement types
- **Low Stock Alerts**: Configurable threshold-based notifications
- **Bulk Operations**: CSV import/export for products and stock movements

### 📊 Analytics & Reporting
- **Stock Analysis**: Advanced analytics with filtering by product, time, and movement type
- **Trend Analysis**: Historical data visualization and trend identification
- **Predictive Analytics**: Future stock depletion estimation
- **Movement Statistics**: Comprehensive reporting on stock movements
- **Dashboard Metrics**: Real-time KPIs and performance indicators

### 🏢 Multi-Tenancy
- **Schema Isolation**: Complete data separation using PostgreSQL schemas
- **Tenant Management**: Dynamic tenant creation and management
- **Cross-Tenant Operations**: Super Admin access across all tenants
- **Tenant Configuration**: Customizable settings per tenant
- **Subscription Management**: Plan-based access control

### 👥 User Management
- **Role-Based Access**: SUPER_ADMIN, ADMIN, USER roles
- **User Statistics**: Comprehensive user analytics and reporting
- **Authentication**: Secure login with tenant-aware routing
- **Authorization**: Fine-grained permissions and access control
- **Profile Management**: User profile and preferences

### 🔔 Notifications
- **Low Stock Alerts**: Automated notifications for inventory levels
- **Email Integration**: SMTP support for email notifications
- **Real-time Updates**: In-app notification system
- **Notification History**: Comprehensive notification tracking

## 🏗️ Architecture

### Technology Stack
- **Backend**: Spring Boot 3.2.1, Spring Security, Spring Data JPA
- **Database**: PostgreSQL 15+ with schema-based multi-tenancy
- **Frontend**: Thymeleaf, Bootstrap 5, JavaScript
- **Build Tool**: Maven 3.6+
- **Java Version**: 17 (LTS)

### Multi-Tenant Architecture
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Public Schema │    │  Tenant Schema  │    │  Tenant Schema  │
│                 │    │      (com)      │    │   (rezonans)    │
│  - Super Admin  │    │  - Users        │    │  - Users        │
│  - Global Config│    │  - Products     │    │  - Products     │
│  - Contact Msgs │    │  - Stock Moves  │    │  - Stock Moves  │
│                 │    │  - Categories   │    │  - Categories   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### Key Components
- **Tenant Context Resolution**: Automatic tenant detection from HTTP headers
- **Connection Provider**: PostgreSQL schema switching
- **Security Filters**: Tenant-aware authentication and authorization
- **Data Loader**: Automatic sample data generation
- **Flyway Migrations**: Schema versioning and management

## 📋 Prerequisites

Before running Stockify, ensure you have the following installed:

- **Java 17** or later (LTS recommended)
- **Maven 3.6+** for build management
- **PostgreSQL 15+** for database
- **Git** for version control
- **PowerShell** (Windows) or **Bash** (Linux/macOS)

### System Requirements
- **RAM**: Minimum 2GB, Recommended 4GB+
- **Storage**: 1GB free space
- **Network**: Internet connection for dependencies

## 🛠️ Installation

### 1. Clone the Repository
```powershell
git clone https://github.com/your-username/stockify.git
cd stockify
```

### 2. Database Setup
```sql
-- Create PostgreSQL database
CREATE DATABASE stockify;

-- Create user (optional)
CREATE USER stockify_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE stockify TO stockify_user;
```

### 3. Configure Application
Edit `src/main/resources/application.properties`:
```properties
# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/stockify
spring.datasource.username=postgres
spring.datasource.password=your_password

# Multi-tenant schemas
spring.flyway.schemas=public,com,rezonans
```

### 4. Build and Run
```powershell
# Clean and compile
mvn clean compile

# Run the application
mvn spring-boot:run
```

### 5. Access the Application
- **Main Application**: http://localhost:8080
- **Health Check**: http://localhost:8080/actuator/health
- **Admin Panel**: http://localhost:8080/admin/dashboard

## ⚙️ Configuration

### Environment Variables
```bash
# Database
POSTGRES_URL=jdbc:postgresql://localhost:5432/stockify
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_password

# Application
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

# Email (Optional)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
```

### Tenant Configuration
```properties
# Default tenants
spring.flyway.schemas=public,com,rezonans

# Flyway settings
spring.flyway.baseline-on-migrate=true
spring.flyway.create-schemas=true
```

## 📖 Usage

### Default Credentials
After first run, the following users are created:

**Super Admin**
- Username: `superadmin`
- Password: `superadmin123`
- Access: All tenants

**Tenant Admins**
- Username: `admin` (for each tenant)
- Password: `admin123`
- Access: Respective tenant only

**Regular Users**
- Username: `user1`, `user2` (for each tenant)
- Password: `user123`
- Access: Respective tenant only

### Tenant Access
Access different tenants by adding the tenant header:
```
X-TenantId: com
X-TenantId: rezonans
```

Or use the tenant-aware URLs:
- http://localhost:8080/admin/dashboard (auto-detects tenant)
- http://localhost:8080/user/dashboard

## 🔧 API Documentation

### Authentication Endpoints
```
POST /login          - User login
POST /logout         - User logout
POST /register       - User registration
```

### Admin Endpoints
```
GET  /admin/dashboard           - Admin dashboard
GET  /admin/products           - Product management
POST /admin/products           - Create product
GET  /admin/users              - User management
GET  /admin/stock-movements    - Stock movement management
GET  /admin/notifications      - Notification management
```

### API Endpoints
```
GET  /admin/products/api       - Get products (JSON)
POST /admin/stock-movements/create     - Create stock movement
GET  /admin/stock-movements/analysis   - Stock analysis
POST /admin/stock-movements/validate   - Validate movement
```

### Stock Movement Analysis
```
GET /admin/stock-movements/analysis?productId=1&days=30&movementType=IN
```

Response:
```json
{
  "totalMovements": 45,
  "totalQuantity": 1250,
  "averageDaily": 41.7,
  "prediction": {
    "daysUntilDepletion": 15,
    "recommendedReorder": 500
  },
  "trends": [
    {
      "date": "2024-01-01",
      "quantity": 100,
      "type": "IN"
    }
  ]
}
```

## 🏢 Multi-Tenancy

### Tenant Architecture
Stockify uses **schema-based multi-tenancy** where each tenant gets its own PostgreSQL schema:

- **public**: Super admin and global configurations
- **com**: First tenant (company)
- **rezonans**: Second tenant (rezonans)

### Tenant Resolution
1. **HTTP Header**: `X-TenantId: com`
2. **URL Path**: `/tenant/com/admin/dashboard`
3. **User Context**: Automatic detection from authenticated user

### Adding New Tenants
```sql
-- Add to Flyway configuration
spring.flyway.schemas=public,com,rezonans,newtenant

-- Or use Super Admin UI
POST /superadmin/tenants/create
{
  "tenantId": "newtenant",
  "companyName": "New Company",
  "adminEmail": "admin@newcompany.com"
}
```

## 🔐 Security

### Authentication
- **Spring Security** with form-based authentication
- **Password encoding** with BCrypt
- **Session management** with CSRF protection
- **Tenant-aware** user resolution

### Authorization
- **Role-based access control** (RBAC)
- **Method-level security** with `@PreAuthorize`
- **Tenant isolation** enforcement
- **Cross-tenant** access for Super Admin

### Security Headers
```java
// Tenant context injection
@ModelAttribute
public void setupTenantContext(HttpServletRequest request) {
    tenantResolutionUtil.setupTenantContext(request);
}
```

## 💻 Development

### Project Structure
```
src/
├── main/
│   ├── java/dev/oasis/stockify/
│   │   ├── config/           # Configuration classes
│   │   ├── controller/       # REST and MVC controllers
│   │   ├── dto/             # Data Transfer Objects
│   │   ├── model/           # JPA entities
│   │   ├── repository/      # Data repositories
│   │   ├── service/         # Business logic
│   │   └── util/            # Utility classes
│   └── resources/
│       ├── application.properties
│       ├── db/migration/    # Flyway migrations
│       ├── templates/       # Thymeleaf templates
│       └── static/          # CSS, JS, images
└── test/                    # Unit and integration tests
```

### Building
```powershell
# Clean build
mvn clean compile

# Run tests
mvn test

# Package
mvn package

# Run with profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Database Migrations
Flyway automatically handles schema migrations:
- `V1__init_complete_schema.sql` - Initial schema
- `V2__create_public_contact_messages.sql` - Contact messages

### Code Style
- **Lombok** for reducing boilerplate
- **Spring annotations** for dependency injection
- **Java 17 features** where applicable
- **Clean architecture** principles

## 🚀 Deployment

### Production Deployment
```powershell
# Build production JAR
mvn clean package -Dspring.profiles.active=prod

# Run with production profile
java -jar target/Stockify-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### Docker Deployment
```dockerfile
# Dockerfile
FROM openjdk:17-jdk-slim
COPY target/Stockify-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```yaml
# docker-compose.yml
version: '3.8'
services:
  stockify:
    build: .
    ports:
      - "8080:8080"
    environment:
      - POSTGRES_URL=jdbc:postgresql://db:5432/stockify
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=postgres
    depends_on:
      - db
  
  db:
    image: postgres:15
    environment:
      - POSTGRES_DB=stockify
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=postgres
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

### Environment Configuration
```properties
# Production settings
spring.profiles.active=prod
spring.jpa.hibernate.ddl-auto=validate
spring.thymeleaf.cache=true
logging.level.root=WARN
logging.level.dev.oasis.stockify=INFO
```

## 📈 Monitoring

### Health Checks
- **Actuator endpoints**: `/actuator/health`
- **Database connectivity**: Automatic health checks
- **Custom metrics**: Stock levels, user activity

### Logging
- **Structured logging** with tenant context
- **Log rotation** with size and time-based rotation
- **Different log levels** for different components

### Metrics
- **Prometheus metrics** via Micrometer
- **Custom metrics** for business KPIs
- **Performance monitoring** for database queries

## 🧪 Testing

### Running Tests
```powershell
# Unit tests
mvn test

# Integration tests
mvn verify

# Test specific class
mvn test -Dtest=UserServiceTest

# Test with coverage
mvn test jacoco:report
```

### Test Structure
- **Unit tests**: Service layer testing
- **Integration tests**: Controller and repository testing
- **Security tests**: Authentication and authorization testing
- **Multi-tenant tests**: Tenant isolation testing

## 📊 Performance

### Database Optimization
- **Connection pooling** with HikariCP
- **Query optimization** with JPA criteria
- **Index strategies** for frequently queried fields
- **Schema-based isolation** for performance

### Caching
- **Spring Cache** for frequently accessed data
- **Template caching** in production
- **Static resource caching** with proper headers

## 🔧 Troubleshooting

### Common Issues

**Database Connection Issues**
```bash
# Check PostgreSQL service
sudo systemctl status postgresql

# Verify database exists
psql -U postgres -l | grep stockify

# Test connection
psql -U postgres -d stockify -c "SELECT 1;"
```

**Tenant Resolution Issues**
```bash
# Check tenant header
curl -H "X-TenantId: com" http://localhost:8080/admin/dashboard

# Verify schemas exist
psql -U postgres -d stockify -c "\dn"
```

**Migration Issues**
```bash
# Clean and rebuild
mvn clean compile

# Manual migration
mvn flyway:migrate -Dflyway.schemas=public,com,rezonans
```

## 📚 Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [PostgreSQL Multi-tenancy](https://www.postgresql.org/docs/current/ddl-schemas.html)
- [Flyway Documentation](https://flywaydb.org/documentation/)
- [Thymeleaf Documentation](https://www.thymeleaf.org/documentation.html)

## 🤝 Contributing

We welcome contributions! Please follow these steps:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit your changes: `git commit -m 'Add amazing feature'`
4. Push to the branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

### Development Guidelines
- Follow Java coding conventions
- Write unit tests for new features
- Update documentation for API changes
- Ensure multi-tenant compatibility

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👨‍💻 Authors

- **Development Team** - *Initial work* - [Oasis Development](https://github.com/oasis-dev)

## 🙏 Acknowledgments

- Spring Boot team for the excellent framework
- PostgreSQL community for robust database engine
- Bootstrap team for responsive UI components
- All contributors who help improve this project

---

**⚠️ Important Notes:**
- Change default passwords in production
- Configure proper SSL/TLS for production deployment
- Set up proper backup strategies for PostgreSQL
- Monitor application logs for security issues
- Keep dependencies updated for security patches

**📞 Support:**
For technical support or questions, please create an issue in the GitHub repository or contact the development team.
