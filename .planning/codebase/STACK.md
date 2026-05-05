# Technology Stack

**Analysis Date:** 2026-05-05

## Languages

**Primary:**
- Java 21 - Core language for all Spring Boot modules and libraries
- YAML - Configuration management (`application.yml` files across modules)
- SQL - PostgreSQL database schema and migrations via Flyway

**Secondary:**
- Shell (Bash/CMD) - Build and deployment scripts in `scripts/` directory
- Inno Setup Script (.iss) - Windows installer configuration in `installer/erp-modulos-installer.iss`

## Runtime

**Environment:**
- Java Development Kit (JDK) 21 - Required for building and running all modules
- Spring Boot 3.5.9 (via parent POM) - Framework runtime for all API modules

**Package Manager:**
- Maven 3.x - Build and dependency management via Maven Wrapper
- Lockfile: `pom.xml` (root + per-module) - Maven dependency declarations with version pinning

Maven Wrapper scripts: `mvnw` (Unix/Linux/macOS) and `mvnw.cmd` (Windows)

## Frameworks

**Core:**
- Spring Boot 3.5.9 - Application framework and auto-configuration
  - `spring-boot-starter-web` - REST API support across all modules
  - `spring-boot-starter-validation` - Bean validation (JSR 380) for DTOs
  - `spring-boot-starter-data-jpa` - ORM support in `api-email` and `api-storage`
  - `spring-boot-starter-mail` - SMTP support in `api-email`
  - `spring-boot-starter-thymeleaf` - HTML template engine for email templates in `api-email`
  - `spring-boot-starter-cache` - Caching abstraction in `api-consultas`
  - `spring-boot-autoconfigure` - Lightweight auto-config for library modules

**Documentation & API:**
- SpringDoc OpenAPI 2.8.15 - OpenAPI 3.0 specification generation and Swagger UI
  - `springdoc-openapi-starter-webmvc-ui` - UI endpoint at `/swagger-ui.html`
  - API docs endpoint: `/v3/api-docs` across all modules

**Resilience & Fault Tolerance:**
- Resilience4j 2.2.0 - Circuit breaker and retry patterns
  - `resilience4j-circuitbreaker` - Circuit breaker implementation (10-call sliding window, 50% failure threshold, 60s open state)
  - `resilience4j-retry` - Exponential backoff retry logic (3 max attempts, 1s initial interval, 2.0x multiplier)
  - Used in `lib-consultas-client` for external API calls

**Caching:**
- Caffeine - In-memory cache implementation in `api-consultas` for CEP/CNPJ results
  - Configured via `spring-boot-starter-cache`

## Key Dependencies

**Critical:**

- `lib-shared` (internal) - Shared exceptions, DTOs, and filters used across all modules
  - Location: `lib-shared/` - published to local Maven repository
- `lib-consultas-client` (internal) - HTTP client library for `api-consultas` module with circuit breaker/retry
  - Location: `lib-consultas-client/`
  - Auto-configurable Spring Boot starter (includes `spring-boot-configuration-processor`)

**Database:**

- PostgreSQL JDBC Driver - Runtime dependency for production database access
  - Scope: `runtime` in `api-email` and `api-storage`
- Flyway 9.22.x (via Spring Boot BOM) - Database migration management
  - Modules: `api-email`, `api-storage`
  - Both use PostgreSQL-specific dialect
  - Config: `baseline-on-migrate: true` for greenfield deployments

**Testing:**

- H2 Database 2.x (via Spring Boot BOM) - In-memory SQL database for tests
  - Scope: `test` in `api-email` and `api-storage`
- Spring Boot Test Starter - JUnit 5, Mockito, AssertJ
  - Scope: `test` in all modules

**Infrastructure:**

- Thumbnailator 0.4.20 - Image thumbnail generation in `api-storage`
  - Used for generating 200x200px thumbnails from uploaded images

**Logging:**

- SLF4J API (via Spring Boot parent) - Logging facade
- Logback (transitive via Spring Boot) - Default logging implementation

**HTTP Client:**

- Spring RestTemplate (via `spring-web`) - HTTP client for external API calls
  - Used in `lib-consultas-client` and `api-consultas` providers (BrasilAPI, ViaCEP)
  - Timeout: 5000ms (configurable via `modulo.timeout-externo-ms`)

**Servlet API:**

- Jakarta Servlet API - Provided scope in `lib-shared` (compatibility with servlet containers)

## Configuration

**Environment:**

- `application.yml` per module - Main Spring Boot configuration file
- `application-test.yml` - Test-specific overrides in `api-email` and `api-storage`
- Environment variables:
  - `API_KEY` - Authentication token for module-to-module calls (default: empty)
  - `STORAGE_DIR` - File storage root directory (default: `./uploads` in `api-storage`)
  - `STORAGE_BASE_URL` - Base URL for file serving (default: `http://localhost:8085`)

**Database Configuration:**
- PostgreSQL connection strings in `application.yml` per module
- Default credentials in config: `erp_calhas` / `erp_calhas_dev`
- Connection pooling: HikariCP (default Spring Boot provider)
- DDL mode: `validate` (schema must exist, no auto-creation)

**Build:**

- `.pom.xml` files - Maven configuration with dependency management
  - Root POM: `pom.xml` - Parent config and module aggregation
  - Module POMs: per-module dependency definitions
  - Maven Wrapper configuration: `.mvn/wrapper/maven-wrapper.properties`

**Ports:**

- `api-email`: 9091
- `api-storage`: 8085
- `api-consultas`: 9192

## Platform Requirements

**Development:**

- Java 21 JDK (or compatible OpenJDK)
- Maven 3.9+ (provided via Maven Wrapper)
- PostgreSQL 12+ (for local database)
- Git (for version control)
- Bash or Windows CMD (for build/deploy scripts)

**Production:**

- Java 21 runtime (OpenJDK or Oracle JDK)
- PostgreSQL 12+ (standalone or managed service)
- Linux, macOS, or Windows server environment
- Network access to external APIs:
  - BrasilAPI (`https://brasilapi.com.br/api`) for CEP/CNPJ lookups
  - ViaCEP (`https://viacep.com.br`) as fallback for CEP lookups
- SMTP server (for email sending via `api-email`)

---

*Stack analysis: 2026-05-05*
