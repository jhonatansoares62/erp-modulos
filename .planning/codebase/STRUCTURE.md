# Codebase Structure

**Analysis Date:** 2026-05-05

## Directory Layout

```
erp-modulos/
├── .mvn/                          # Maven wrapper configuration
├── .planning/                      # GSD planning documents
│   └── codebase/                   # This directory
├── api-email/                      # Email service (Spring Boot JAR)
│   ├── src/main/java/br/com/erpkit/email/
│   │   ├── EmailApplication.java
│   │   ├── controller/             # REST endpoints
│   │   ├── service/                # Business logic
│   │   ├── model/                  # JPA entities
│   │   ├── repository/             # Data access
│   │   ├── dto/                    # Request/response objects
│   │   └── config/                 # Spring configuration
│   ├── src/main/resources/
│   │   ├── application.yml         # Service config (port 9091)
│   │   ├── db/migration/           # Flyway migrations
│   │   ├── templates/              # Thymeleaf email templates
│   │   └── logback-spring.xml
│   ├── src/test/
│   │   ├── java/                   # Unit & integration tests
│   │   └── resources/application-test.yml
│   └── pom.xml
├── api-storage/                    # File storage service (Spring Boot JAR)
│   ├── src/main/java/br/com/erpkit/storage/
│   │   ├── StorageApplication.java
│   │   ├── controller/
│   │   ├── service/
│   │   ├── model/
│   │   ├── repository/
│   │   ├── dto/
│   │   └── config/
│   ├── src/main/resources/
│   │   ├── application.yml         # Service config (port 8085)
│   │   ├── db/migration/
│   │   └── logback-spring.xml
│   ├── uploads/                    # Generated storage directory
│   │   └── {yyyy}/{mm}/            # Date-organized files
│   └── pom.xml
├── api-consultas/                  # Query service (Spring Boot JAR)
│   ├── src/main/java/br/com/erpkit/consultas/
│   │   ├── ConsultasApplication.java
│   │   ├── controller/
│   │   ├── service/                # CEP/CNPJ query logic
│   │   ├── service/provider/       # External API clients
│   │   ├── validation/
│   │   ├── config/                 # Cache config
│   │   └── controller/
│   ├── src/main/resources/
│   │   ├── application.yml         # Service config (port 9192)
│   │   └── logback-spring.xml
│   └── pom.xml
├── lib-shared/                     # Shared library (Maven JAR)
│   ├── src/main/java/br/com/erpkit/shared/
│   │   ├── config/                 # CORS, common beans
│   │   ├── dto/                    # ErrorResponse, HealthResponse
│   │   ├── exception/              # GlobalExceptionHandler, ModuloException
│   │   └── security/               # ApiKeyFilter
│   ├── src/test/
│   │   └── java/                   # Shared tests
│   └── pom.xml
├── lib-consultas-client/           # Client library (Maven JAR)
│   ├── src/main/java/br/com/erpkit/consultas/client/
│   │   ├── ConsultasClient.java    # Interface
│   │   ├── ConsultasClientImpl.java # Implementation
│   │   ├── ConsultasClientAutoConfiguration.java  # Spring Boot auto-config
│   │   ├── ConsultasProperties.java # Config class
│   │   ├── dto/                    # EnderecoResponse, FornecedorResponse
│   │   └── exception/              # ConsultasException, ConsultasIndisponivelException
│   └── pom.xml
├── scripts/                        # Deployment automation
│   ├── build.sh / build.cmd        # Maven clean build all modules
│   ├── deploy.sh / deploy.cmd      # Start all services
│   └── install.sh / install.cmd    # Setup PostgreSQL databases
├── installer/                      # Inno Setup installer (Windows)
├── logs/                           # Runtime logs directory (generated)
├── pom.xml                         # Root parent POM
├── mvnw & mvnw.cmd                 # Maven wrapper scripts
└── .gitignore
```

## Directory Purposes

**`api-email/`:**
- Purpose: Standalone email service with SMTP queue processing
- Contains: Email entity, ContaEmail (SMTP accounts), queue scheduling, Thymeleaf templates
- Key files: `src/main/java/br/com/erpkit/email/service/EmailService.java`, `src/main/resources/db/migration/`

**`api-storage/`:**
- Purpose: File upload and retrieval service with thumbnail generation
- Contains: Arquivo entity, multipart handling, filesystem operations, image thumbnails
- Key files: `src/main/java/br/com/erpkit/storage/service/StorageService.java`

**`api-consultas/`:**
- Purpose: External data query service (CEP via ViaCEP/BrasilAPI, CNPJ via BrasilAPI)
- Contains: CepService, CnpjService, provider implementations, cache config
- Key files: `src/main/java/br/com/erpkit/consultas/service/`, `src/main/java/br/com/erpkit/consultas/service/provider/`

**`lib-shared/`:**
- Purpose: Shared cross-module infrastructure
- Contains: Global exception handler, API key filter, common DTOs, CORS config
- Key files: `src/main/java/br/com/erpkit/shared/exception/GlobalExceptionHandler.java`, `src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java`

**`lib-consultas-client/`:**
- Purpose: Reusable Maven library for consuming api-consultas
- Contains: HTTP client wrapper, DTOs for address/supplier responses, auto-configuration
- Key files: `src/main/java/br/com/erpkit/consultas/client/ConsultasClientAutoConfiguration.java`
- Consumed by: External ERP applications (import as dependency)

**`scripts/`:**
- Purpose: Build and deployment automation
- Contains: Build scripts (Maven), database initialization, service startup
- Usage: `./scripts/build.sh` builds all modules; `./scripts/deploy.sh` starts services

## Key File Locations

**Entry Points:**
- `api-email/src/main/java/br/com/erpkit/email/EmailApplication.java`: Main class for email service
- `api-storage/src/main/java/br/com/erpkit/storage/StorageApplication.java`: Main class for storage service
- `api-consultas/src/main/java/br/com/erpkit/consultas/ConsultasApplication.java`: Main class for query service

**Configuration:**
- `api-email/src/main/resources/application.yml`: Port 9091, PostgreSQL, SMTP defaults, scheduled task interval (30s)
- `api-storage/src/main/resources/application.yml`: Port 8085, PostgreSQL, upload directory, thumbnail dimensions
- `api-consultas/src/main/resources/application.yml`: Port 9192, Caffeine cache config
- `pom.xml` (root): Dependency management, Spring Boot 3.5.9, Java 21, library versions

**Core Logic:**
- `api-email/src/main/java/br/com/erpkit/email/service/EmailService.java`: Queue processing, SMTP dispatch, template rendering
- `api-storage/src/main/java/br/com/erpkit/storage/service/StorageService.java`: File handling, thumbnail generation
- `api-consultas/src/main/java/br/com/erpkit/consultas/service/CepService.java`: CEP queries with provider fallback and caching
- `api-consultas/src/main/java/br/com/erpkit/consultas/service/CnpjService.java`: CNPJ queries with caching

**Testing:**
- `api-email/src/test/java/`: Unit tests for EmailService, ContaEmailService
- `api-storage/src/test/java/`: Unit tests for StorageService
- `api-consultas/src/test/java/`: Unit tests for CepService, CnpjService, provider integration
- `lib-shared/src/test/java/`: Tests for exception handling, filters

**Database:**
- `api-email/src/main/resources/db/migration/V1__criar_tabela_emails.sql`: Email queue table
- `api-email/src/main/resources/db/migration/V2__criar_tabela_contas_email.sql`: SMTP account credentials table
- `api-storage/src/main/resources/db/migration/V1__criar_tabela_arquivos.sql`: File metadata table

## Naming Conventions

**Files:**
- Java classes: PascalCase (e.g., `EmailService.java`, `ContaEmailController.java`)
- Test files: `{ClassName}Test.java` or `{ClassName}Tests.java` (e.g., `EmailServiceTest.java`)
- Configuration files: `application.yml`, `application-{profile}.yml` (e.g., `application-test.yml`)
- Migration files: `V{number}__{description}.sql` (e.g., `V1__criar_tabela_emails.sql`)

**Directories:**
- Package directories: lowercase with hyphens for multi-word modules (e.g., `api-email/`, `lib-shared/`)
- Source packages: lowercase segments separated by dots (e.g., `br.com.erpkit.email.service`)
- Resource subdirectories: lowercase (e.g., `db/migration/`, `templates/`)

**Classes:**
- Service classes: `{Domain}Service` (e.g., `EmailService`, `StorageService`)
- Controller classes: `{Domain}Controller` (e.g., `EmailController`, `ArquivoController`)
- Entity classes: Domain noun (e.g., `Email`, `ContaEmail`, `Arquivo`)
- DTO Request classes: `{Domain}CreateDTO` or `{Domain}UpdateDTO` (e.g., `EmailCreateDTO`, `ContaEmailCreateDTO`)
- DTO Response classes: `{Domain}Response` (e.g., `EmailResponse`, `ArquivoResponse`)
- Repository interfaces: `{Domain}Repository` extends `JpaRepository` (e.g., `EmailRepository`)
- Exception classes: `{Domain}Exception` (e.g., `ModuloException`, `ProviderIndisponivelException`)

**Variables/Methods:**
- camelCase for all Java identifiers (e.g., `criarMailSender()`, `emailRepository`)
- Boolean fields/getters: `is{Property}` prefix (e.g., `isHtml()`, `temThumbnail`)
- Constant fields: UPPER_SNAKE_CASE (e.g., `MAX_TENTATIVAS`, `TIPOS_IMAGEM`)

## Where to Add New Code

**New API Endpoint (Feature):**
1. Create DTO classes in `{module}/src/main/java/br/com/erpkit/{module}/dto/`
   - Request: `{Feature}CreateDTO.java`
   - Response: `{Feature}Response.java`
2. Add controller method to `{module}/src/main/java/br/com/erpkit/{module}/controller/{Feature}Controller.java`
   - Map HTTP method: `@GetMapping`, `@PostMapping`, etc.
   - Delegate to service layer
3. Implement service logic in `{module}/src/main/java/br/com/erpkit/{module}/service/{Feature}Service.java`
   - Handle validation, business rules, state changes
   - Use repository for data access
4. Extend repository if needed: `{module}/src/main/java/br/com/erpkit/{module}/repository/{Entity}Repository.java`
   - Add custom `@Query` methods if JpaRepository defaults insufficient
5. Write tests: `{module}/src/test/java/br/com/erpkit/{module}/{Feature}Test.java`

**New Library Module:**
1. Create directory at repo root: `lib-{name}/`
2. Copy `lib-shared/pom.xml` as template, change `<artifactId>` to `lib-{name}`
3. Create package: `lib-{name}/src/main/java/br/com/erpkit/{name}/`
4. Register in root `pom.xml` `<modules>` section
5. If reusable library: Create `*AutoConfiguration.java` class for Spring Boot auto-config (see `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/ConsultasClientAutoConfiguration.java`)

**New Shared DTO/Exception:**
1. Add to `lib-shared/src/main/java/br/com/erpkit/shared/dto/` or `.../exception/`
2. All `api-*` modules will pick it up after Maven rebuild

**Utilities/Validators:**
- Shared across services: `lib-shared/src/main/java/br/com/erpkit/shared/` (e.g., validators)
- Service-specific: `{module}/src/main/java/br/com/erpkit/{module}/validation/` (e.g., `DocumentoValidator.java` in api-consultas)

**Configuration Changes:**
- Per-service config: `{module}/src/main/resources/application.yml`
- Test profile: `{module}/src/test/resources/application-test.yml` (e.g., H2 in-memory DB)
- Environment variables: Sourced via `${VAR_NAME:default}` syntax in YAML

**Database Schema Changes:**
- Create migration file: `{module}/src/main/resources/db/migration/V{N}__{description}.sql`
- Naming: Increment version from previous migration
- Flyway auto-runs on application startup
- Never edit previous migrations; create new ones for modifications

## Special Directories

**`api-storage/uploads/`:**
- Purpose: Generated storage directory for uploaded files
- Generated: Yes (auto-created by `StorageService.init()` on startup)
- Committed: No (`.gitignore` excludes `/uploads/`)
- Organization: `{yyyy}/{mm}/{uuid}.{ext}` for files, `{yyyy}/{mm}/{uuid}-thumb.{ext}` for thumbnails

**`logs/`:**
- Purpose: Runtime application logs
- Generated: Yes (created by Logback at runtime)
- Committed: No (`.gitignore` excludes `/logs/`)

**`.mvn/`:**
- Purpose: Maven wrapper configuration (allows building without system Maven)
- Generated: No (checked into version control)
- Committed: Yes
- Usage: `./mvnw clean install` (Unix) or `mvnw.cmd clean install` (Windows)

**`target/`:**
- Purpose: Maven build artifacts, compiled classes, JARs
- Generated: Yes (`mvn clean install` creates these)
- Committed: No (`.gitignore` excludes `/target/`)

**`.planning/codebase/`:**
- Purpose: GSD codebase analysis documents (ARCHITECTURE.md, STRUCTURE.md, etc.)
- Generated: Yes (created by gsd-codebase-mapper)
- Committed: Yes (checked into version control for team reference)

---

*Structure analysis: 2026-05-05*
