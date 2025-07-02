# 🤖 AGENTS.md - Development Guidelines for AI Assistants

This document provides comprehensive guidelines for AI agents working on the Stockify project. It covers architecture, conventions, best practices, and common operations.

## 📋 Table of Contents

- [Project Overview](#-project-overview)
- [Architecture Guidelines](#-architecture-guidelines)
- [Development Standards](#-development-standards)
- [Multi-Tenancy Rules](#-multi-tenancy-rules)
- [Database Guidelines](#-database-guidelines)
- [Code Conventions](#-code-conventions)
- [Security Guidelines](#-security-guidelines)
- [Testing Guidelines](#-testing-guidelines)
- [Common Operations](#-common-operations)
- [Troubleshooting Guide](#-troubleshooting-guide)

## 🎯 Project Overview

**Stockify** is a multi-tenant inventory management system built with:
- **Java 17** (LTS)
- **Spring Boot 3.2.1**
- **PostgreSQL 15+** with schema-based multi-tenancy
- **Maven** for build management
- **Thymeleaf** for server-side templating
- **Bootstrap 5** for responsive UI

### Key Architecture Principles
1. **Schema-based multi-tenancy** - Each tenant has its own PostgreSQL schema
2. **Role-based access control** - SUPER_ADMIN, ADMIN, USER roles
3. **Tenant-aware operations** - All operations must respect tenant boundaries
4. **RESTful API design** - Clean, consistent endpoint structure
5. **Security-first approach** - All endpoints properly secured

## 🏗️ Architecture Guidelines

### Multi-Tenant Architecture
```
Application Layer (Spring Boot)
├── Tenant Resolution (HTTP Headers)
├── Security Layer (Spring Security)
├── Service Layer (Business Logic)
├── Repository Layer (JPA/Hibernate)
└── Database Layer (PostgreSQL Schemas)
    ├── public (Super Admin)
    ├── com (Tenant 1)
    └── rezonans (Tenant 2)
```

### Component Interaction
1. **Request** → Tenant Header Filter → Tenant Resolution
2. **Authentication** → Security Filter → Role Validation
3. **Service** → Tenant Context → Database Schema Selection
4. **Response** → Data Filtering → Tenant-specific Response

## 📏 Development Standards

### File and Directory Structure
```
src/main/java/dev/oasis/stockify/
├── config/           # Configuration classes
│   ├── security/     # Security configurations
│   └── tenant/       # Multi-tenancy configurations
├── controller/       # MVC and REST controllers
├── dto/             # Data Transfer Objects
├── exception/       # Custom exceptions
├── model/           # JPA entities
├── repository/      # Data access repositories
├── service/         # Business logic services
└── util/            # Utility classes
```

### Naming Conventions
- **Classes**: PascalCase (e.g., `StockMovementController`)
- **Methods**: camelCase (e.g., `getAllStockMovements`)
- **Variables**: camelCase (e.g., `currentTenantId`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `DEFAULT_PAGE_SIZE`)
- **Database tables**: snake_case (e.g., `stock_movement`)
- **Database columns**: snake_case (e.g., `created_at`)

### Package Organization
- **Controllers**: Group by functional area (Admin, User, API)
- **Services**: One service per entity, plus utility services
- **Repositories**: Mirror the service structure
- **DTOs**: Organize by operation (Create, Update, Response)

## 🏢 Multi-Tenancy Rules

### Critical Multi-Tenancy Guidelines

1. **Always Use Tenant Context**
   ```java
   @ModelAttribute
   public void setupTenantContext(HttpServletRequest request) {
       tenantResolutionUtil.setupTenantContext(request);
   }
   ```

2. **Tenant Resolution Order**
   - HTTP Header: `X-TenantId`
   - User's primary tenant
   - URL path parameter
   - Default to 'public' for SUPER_ADMIN

3. **Schema Naming**
   - Use lowercase for schema names
   - No special characters except underscores
   - Keep names short and descriptive

4. **Tenant Isolation**
   - Never expose cross-tenant data without explicit SUPER_ADMIN check
   - Always validate tenant access in service layer
   - Use tenant-aware queries

### Tenant-Aware Code Examples

**Controller Level:**
```java
@Controller
@RequestMapping("/admin/products")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class ProductController {
    
    @ModelAttribute
    public void setupTenantContext(HttpServletRequest request) {
        tenantResolutionUtil.setupTenantContext(request);
    }
    
    @GetMapping
    public String listProducts(HttpServletRequest request, 
                              Authentication authentication, 
                              Model model) {
        String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
        // ... tenant-aware logic
    }
}
```

**Service Level:**
```java
@Service
@Transactional
public class ProductService {
    
    public List<Product> getAllProducts() {
        // Hibernate automatically uses current tenant context
        return productRepository.findAll();
    }
}
```

## 🗄️ Database Guidelines

### Schema Management
- **public**: Super admin users, global configurations, contact messages
- **Tenant schemas**: All tenant-specific data (users, products, stock movements)
- **Migration strategy**: Flyway with schema-specific migrations

### Entity Design Rules
1. **Use Lombok** for reducing boilerplate code
2. **JPA annotations** for entity mapping
3. **Audit fields** (createdAt, updatedAt) for tracking
4. **Proper indexing** for performance
5. **Foreign key constraints** for data integrity

### Repository Patterns
```java
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Simple queries - Hibernate handles tenant context
    List<Product> findByCategory(String category);
    
    // Complex queries - Use @Query when needed
    @Query("SELECT p FROM Product p WHERE p.stockQuantity < :threshold")
    List<Product> findLowStockProducts(@Param("threshold") Integer threshold);
    
    // Native queries - Include schema context if needed
    @Query(value = "SELECT * FROM product WHERE sku = ?1", nativeQuery = true)
    Optional<Product> findBySku(String sku);
}
```

### Migration Best Practices
1. **Version control** all migrations
2. **Test migrations** on sample data
3. **Backup strategy** before major changes
4. **Schema consistency** across all tenants

## 💻 Code Conventions

### Spring Boot Best Practices

1. **Constructor Injection** (use `@RequiredArgsConstructor`)
   ```java
   @Service
   @RequiredArgsConstructor
   public class ProductService {
       private final ProductRepository productRepository;
       private final TenantUtil tenantUtil;
   }
   ```

2. **Configuration Properties**
   ```java
   @ConfigurationProperties(prefix = "stockify")
   @Data
   public class StockifyProperties {
       private String defaultTenant = "public";
       private Integer lowStockThreshold = 5;
   }
   ```

3. **Error Handling**
   ```java
   @ControllerAdvice
   public class GlobalExceptionHandler {
       
       @ExceptionHandler(TenantNotFoundException.class)
       public ResponseEntity<ErrorResponse> handleTenantNotFound(TenantNotFoundException ex) {
           return ResponseEntity.status(HttpStatus.NOT_FOUND)
               .body(new ErrorResponse("Tenant not found", ex.getMessage()));
       }
   }
   ```

### Lombok Usage
- **@Data** for simple POJOs
- **@Entity** + **@Table** for JPA entities
- **@Builder** for complex object creation
- **@RequiredArgsConstructor** for dependency injection
- **@Slf4j** for logging

### Logging Standards
```java
@Slf4j
public class ProductService {
    
    public Product createProduct(ProductCreateDTO dto) {
        log.info("Creating product: {} for tenant: {}", dto.getName(), getCurrentTenant());
        
        try {
            // Business logic
            Product product = productRepository.save(newProduct);
            log.info("✅ Product created successfully: {}", product.getId());
            return product;
        } catch (Exception e) {
            log.error("❌ Failed to create product: {}", e.getMessage(), e);
            throw new ProductCreationException("Failed to create product", e);
        }
    }
}
```

## 🔐 Security Guidelines

### Authentication & Authorization
1. **Method-level security** with `@PreAuthorize`
2. **Tenant validation** in all service methods
3. **Input validation** with Jakarta Validation
4. **CSRF protection** for state-changing operations

### Security Annotations
```java
// Controller level
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")

// Method level
@PreAuthorize("hasRole('SUPER_ADMIN') or (hasRole('ADMIN') and @tenantUtil.canAccessTenant(#tenantId))")

// Conditional access
@PreAuthorize("@productService.canUserAccessProduct(authentication.name, #productId)")
```

### Input Validation
```java
@PostMapping("/products")
public ResponseEntity<?> createProduct(@Valid @RequestBody ProductCreateDTO dto,
                                     BindingResult result) {
    if (result.hasErrors()) {
        return ResponseEntity.badRequest()
            .body(ValidationErrorDTO.fromBindingResult(result));
    }
    // ... process valid input
}
```

## 🧪 Testing Guidelines

### Test Structure
```
src/test/java/dev/oasis/stockify/
├── controller/       # Integration tests
├── service/         # Unit tests
├── repository/      # Repository tests
└── integration/     # End-to-end tests
```

### Testing Patterns
```java
@SpringBootTest
@TestMethodOrder(OrderAnnotation.class)
class ProductServiceTest {
    
    @MockBean
    private ProductRepository productRepository;
    
    @Autowired
    private ProductService productService;
    
    @Test
    @Order(1)
    void shouldCreateProduct() {
        // Given
        ProductCreateDTO dto = ProductCreateDTO.builder()
            .name("Test Product")
            .sku("TEST-001")
            .build();
        
        // When
        Product result = productService.createProduct(dto);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Product");
    }
}
```

### Multi-Tenant Testing
```java
@Test
void shouldIsolateTenantData() {
    // Setup tenant context
    TenantContext.setCurrentTenant("com");
    List<Product> comProducts = productService.getAllProducts();
    
    TenantContext.setCurrentTenant("rezonans");
    List<Product> rezonansProducts = productService.getAllProducts();
    
    // Verify isolation
    assertThat(comProducts).isNotEqualTo(rezonansProducts);
}
```

## ⚙️ Common Operations

### Building and Running
```powershell
# Clean build
mvn clean compile

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Package for deployment
mvn clean package -DskipTests

# Run tests
mvn test

# Run specific test class
mvn test -Dtest=ProductServiceTest
```

### Database Operations
```powershell
# Connect to database
psql -U postgres -d stockify

# List schemas
\dn

# Switch to tenant schema
SET search_path TO com;

# Check tenant tables
\dt

# Run migrations manually
mvn flyway:migrate -Dflyway.schemas=public,com,rezonans
```

### Adding New Features

1. **Create Entity**
   ```java
   @Entity
   @Table(name = "new_entity")
   @Data
   @Builder
   @NoArgsConstructor
   @AllArgsConstructor
   public class NewEntity {
       @Id
       @GeneratedValue(strategy = GenerationType.IDENTITY)
       private Long id;
       
       // Add fields with proper validation
   }
   ```

2. **Create Repository**
   ```java
   @Repository
   public interface NewEntityRepository extends JpaRepository<NewEntity, Long> {
       // Add custom query methods
   }
   ```

3. **Create Service**
   ```java
   @Service
   @RequiredArgsConstructor
   @Transactional
   public class NewEntityService {
       private final NewEntityRepository repository;
       // Implement business logic
   }
   ```

4. **Create Controller**
   ```java
   @Controller
   @RequestMapping("/admin/new-entities")
   @PreAuthorize("hasRole('ADMIN')")
   @RequiredArgsConstructor
   public class NewEntityController {
       // Implement endpoints
   }
   ```

### Adding New Tenant
```java
// Via Super Admin interface
POST /superadmin/tenants/create
{
    "tenantId": "newtenant",
    "companyName": "New Company",
    "adminEmail": "admin@newcompany.com",
    "adminUsername": "admin",
    "adminPassword": "secure_password"
}

// Or add to application.properties
spring.flyway.schemas=public,com,rezonans,newtenant
```

## 🔧 Troubleshooting Guide

### Common Issues and Solutions

**1. Tenant Resolution Failures**
```java
// Problem: No tenant found in context
// Solution: Check tenant header and resolution logic
@ModelAttribute
public void setupTenantContext(HttpServletRequest request) {
    String tenantId = request.getHeader("X-TenantId");
    if (tenantId != null) {
        TenantContext.setCurrentTenant(tenantId);
    }
}
```

**2. Database Connection Issues**
```properties
# Problem: Cannot connect to database
# Solution: Verify connection properties
spring.datasource.url=jdbc:postgresql://localhost:5432/stockify
spring.datasource.username=postgres
spring.datasource.password=correct_password

# Test connection
spring.datasource.hikari.connection-test-query=SELECT 1
```

**3. Migration Failures**
```powershell
# Problem: Flyway migration errors
# Solution: Clean and repair
mvn flyway:clean
mvn flyway:repair
mvn flyway:migrate
```

**4. Security Access Denied**
```java
// Problem: Access denied errors
// Solution: Check role assignments and method security
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public String adminPage() {
    // Ensure user has correct role
}
```

### Performance Issues

**1. Slow Queries**
- Add proper database indexes
- Use query optimization
- Enable SQL logging for analysis

**2. Memory Issues**
- Check connection pool settings
- Monitor tenant context cleanup
- Profile application with JProfiler

### Debugging Tips

1. **Enable Debug Logging**
   ```properties
   logging.level.dev.oasis.stockify=DEBUG
   logging.level.org.hibernate.SQL=DEBUG
   ```

2. **Monitor Tenant Context**
   ```java
   log.debug("Current tenant: {}", TenantContext.getCurrentTenant());
   ```

3. **Validate Security Context**
   ```java
   Authentication auth = SecurityContextHolder.getContext().getAuthentication();
   log.debug("Current user: {}, roles: {}", auth.getName(), auth.getAuthorities());
   ```

## 📚 Best Practices Summary

### DO's ✅
- Use PowerShell for Windows development
- Always set up tenant context in controllers
- Use Lombok to reduce boilerplate code
- Write tenant-aware integration tests
- Use proper logging with tenant context
- Validate input with Jakarta Validation
- Use constructor injection with `@RequiredArgsConstructor`
- Follow RESTful API conventions
- Use semantic commit messages (English, verb-first)
- Keep tenant names lowercase
- Use `mvn clean compile spring-boot:run` to start application

### DON'Ts ❌
- Don't use `curl` command (use PowerShell equivalents)
- Don't expose cross-tenant data without proper authorization
- Don't hardcode tenant names in business logic
- Don't skip input validation
- Don't use field injection (`@Autowired` on fields)
- Don't commit sensitive configuration data
- Don't use uppercase in schema names
- Don't skip tenant context setup
- Don't use `create-drop` in production (`ddl-auto=validate`)

### Database Best Practices
- Database name: `stockify` (lowercase)
- Schema names: lowercase only
- Migration files: Version-controlled and tested
- Connection pooling: Properly configured
- Indexes: Added for frequently queried columns

### Code Style
- Java 17 features where appropriate
- Spring Boot 3.2+ annotations and patterns
- Clean architecture principles
- Comprehensive error handling
- Proper exception types and messages

## 🆘 Emergency Procedures

### System Down
1. Check application logs: `logs/stockify.log`
2. Verify database connectivity
3. Check tenant schema integrity
4. Restart application with clean profile

### Data Corruption
1. Stop application immediately
2. Backup database
3. Identify affected tenant schema
4. Restore from last known good backup
5. Replay transactions if possible

### Security Breach
1. Change all default passwords immediately
2. Audit user access logs
3. Review tenant isolation
4. Update security configurations
5. Notify affected tenants

---

**📝 Note for AI Agents:**
This document should be referenced for all development activities on the Stockify project. When in doubt, prioritize tenant isolation, security, and data integrity. Always test multi-tenant scenarios and validate proper access controls.

**🔄 Updates:**
This document should be updated whenever architectural changes are made or new patterns are established. Keep it synchronized with the actual codebase.
