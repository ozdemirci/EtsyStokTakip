# EtsyStokTakip Project Guidelines

## Project Overview

EtsyStokTakip is a Spring Boot application designed for tracking and managing inventory for Etsy sellers. The application provides functionality for user management, product management, and stock tracking.

### Key Features

- User authentication and role-based authorization (ADMIN, DEPO, USER roles)
- Product management (add, edit, delete, list)
- Stock level tracking
- Integration with Etsy product IDs

## Project Structure

The project follows a standard Spring Boot application structure:

```
EtsyStokTakip/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/yourcompany/etsystoktakip/
│   │   │       ├── config/           # Configuration classes
│   │   │       ├── controller/       # MVC controllers
│   │   │       ├── exception/        # Exception handling
│   │   │       ├── model/            # Entity classes
│   │   │       ├── repository/       # Data access layer
│   │   │       ├── service/          # Business logic
│   │   │       └── EtsyStokTakipApplication.java  # Main class
│   │   └── resources/
│   │       ├── templates/            # Thymeleaf templates
│   │       ├── application.properties # Application configuration
│   │                                     
│   └── test/                         # Test classes
├── Dockerfile                        # Docker configuration
├── docker-compose.yml                # Docker Compose configuration
└── pom.xml                           # Maven dependencies
```

## Technology Stack

- **Java 17**: Core programming language
- **Spring Boot 3.2.5**: Application framework
- **Spring Security**: Authentication and authorization
- **Spring Data JPA**: Data access layer
- **Thymeleaf**: Server-side templating
- **H2 Database**: In-memory database for development
- **PostgreSQL**: Production database
- **Maven**: Build tool
- **Docker**: Containerization

## Setup Instructions

### Prerequisites

- Java Development Kit (JDK) 17
- Maven 3.6+
- Docker and Docker Compose (for containerized deployment)
- Git

### Local Development Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/ozdemirci/EtsyStokTakip.git
   cd EtsyStokTakip
   ```

2. Build the application:
   ```bash
   mvn clean package
   ```

3. Run the application:
   ```bash
   mvn spring-boot:run
   ```

4. Access the application:
   - Open a web browser and navigate to `http://localhost:8080`
   - Default admin credentials: username `admin`, password `admin123`

## Database Configuration

The application supports two database configurations:

1. **H2 In-Memory Database** (default for development):
   - Console available at `http://localhost:8080/h2-console`
   - JDBC URL: `jdbc:h2:mem:etsystoktakip`
   - Username: `sa`
   - Password: `password`

2. **PostgreSQL** (for production):
   - Configure in `application.properties` or through environment variables
   - Default configuration in Docker Compose

## Running Tests

Execute the following command to run tests:

```bash
mvn test
```

For specific test classes:

```bash
mvn test -Dtest=TestClassName
```

## Deployment

### Docker Deployment

1. Build the application:
   ```bash
   mvn clean package
   ```

2. Build and start the Docker containers:
   ```bash
   docker-compose up -d
   ```

3. Access the application at `http://localhost:8080`

4. Stop the containers:
   ```bash
   docker-compose down
   ```

### Production Deployment Considerations

- Update database credentials in production
- Configure proper logging
- Set up HTTPS
- Implement backup strategies for the database
- Consider using environment-specific configuration files

## Code Style Guidelines

### Java Code Style

- Follow standard Java naming conventions:
  - Classes: PascalCase (e.g., `ProductController`)
  - Methods and variables: camelCase (e.g., `getAllProducts()`)
  - Constants: UPPER_SNAKE_CASE (e.g., `MAX_RETRY_COUNT`)
- Use meaningful names for classes, methods, and variables
- Add comments for complex logic
- Keep methods short and focused on a single responsibility
- Use proper exception handling

### Spring Best Practices

- Use constructor injection instead of field injection when possible
- Follow the Spring MVC pattern (Controller, Service, Repository)
- Use Spring Security for authentication and authorization
- - Use Spring DTO for data transfer objects
- Validate input data using Bean Validation annotations
- Use proper HTTP methods in REST controllers (GET, POST, PUT, DELETE)

### Database Guidelines

- Use JPA annotations for entity mapping
- Define proper relationships between entities
- Use appropriate data types for columns
- Add validation constraints to entity fields
- Use meaningful names for tables and columns

### Testing Guidelines

- Write unit tests for services and repositories
- Write integration tests for controllers
- Use MockMvc for testing controllers
- Use H2 in-memory database for testing
- Aim for high test coverage

## Troubleshooting

### Common Issues

1. **Database connection issues**:
   - Check database credentials
   - Ensure database server is running
   - Verify connection URL

2. **Application startup failures**:
   - Check logs for detailed error messages
   - Verify port 8080 is not in use by another application

3. **Authentication issues**:
   - Verify user credentials
   - Check role assignments
   - Review security configuration

## Contributing

1. Create a feature branch from `main`
2. Implement your changes
3. Write tests for your changes
4. Ensure all tests pass
5. Submit a pull request

## Contact

For questions or support, contact the project maintainers at:
- Email: ozdemircihakan@gmail.com
