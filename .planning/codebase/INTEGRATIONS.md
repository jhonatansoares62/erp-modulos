# External Integrations

**Analysis Date:** 2026-05-05

## APIs & External Services

**CEP (Address) Lookup:**
- BrasilAPI - Primary provider for CEP address resolution via REST API
  - Endpoint: `https://brasilapi.com.br/api/cep/v2/{cep}`
  - SDK/Client: RestTemplate (Spring Web)
  - Implementation: `api-consultas/src/main/java/br/com/erpkit/consultas/service/provider/BrasilApiProvider.java`
  - Timeout: 5000ms (configurable)
  - Circuit breaker: Enabled (50% failure threshold, 60s open state, 10-call window)
  - Retry: 3 attempts with exponential backoff (1s initial, 2.0x multiplier)

- ViaCEP - Fallback provider for CEP lookups
  - Endpoint: `https://viacep.com.br/ws/{cep}/json`
  - SDK/Client: RestTemplate (Spring Web)
  - Implementation: `api-consultas/src/main/java/br/com/erpkit/consultas/service/provider/ViaCepProvider.java`
  - Timeout: 5000ms (configurable)
  - Fallback strategy: Used when BrasilAPI is unavailable

**CNPJ (Company) Lookup:**
- BrasilAPI - Primary provider for CNPJ company data
  - Endpoint: `https://brasilapi.com.br/api/cnpj/v1/{cnpj}`
  - SDK/Client: RestTemplate (Spring Web)
  - Implementation: `api-consultas/src/main/java/br/com/erpkit/consultas/service/provider/BrasilApiProvider.java`
  - Timeout: 5000ms (configurable)
  - Returns: Legal name, business name, registration date, legal nature, CNAE code, email, phone, address
  - Circuit breaker: Enabled (same config as CEP)

## Data Storage

**Databases:**

- PostgreSQL 12+
  - Connection (api-email): `jdbc:postgresql://localhost:5432/db_api_email`
    - Username: `erp_calhas`
    - Client: Hibernate ORM via Spring Data JPA
    - Dialect: `org.hibernate.dialect.PostgreSQLDialect`
    - DDL Strategy: `validate` (migrations managed separately)

  - Connection (api-storage): `jdbc:postgresql://localhost:5432/db_api_storage`
    - Username: `erp_calhas`
    - Client: Hibernate ORM via Spring Data JPA
    - Dialect: `org.hibernate.dialect.PostgreSQLDialect`
    - DDL Strategy: `validate` (migrations managed separately)

**Migrations:**
- Flyway 9.22.x - Database versioning and schema management
  - Modules: `api-email`, `api-storage`
  - Config: `baseline-on-migrate: true` (creates baseline for existing databases)
  - Location: Likely in `src/main/resources/db/migration/` per module (following Flyway conventions)

**File Storage:**

- Local Filesystem - Primary storage
  - Directory: `${STORAGE_DIR:./uploads}` (environment-configurable)
  - Module: `api-storage`
  - Max file size: 50MB (per `max-file-size` in `application.yml`)
  - Base URL for serving files: `${STORAGE_BASE_URL:http://localhost:8085}`

- Thumbnails:
  - Generated on upload for images
  - Size: 200x200px
  - Library: Thumbnailator 0.4.20
  - Implementation: `api-storage/src/main/java/br/com/erpkit/storage/service/StorageService.java`

**Caching:**

- Caffeine - In-memory cache for lookup results
  - Module: `api-consultas`
  - Purpose: Cache CEP/CNPJ results to reduce external API calls
  - Type: `caffeine` (configured in `spring.cache.type`)

## Authentication & Identity

**Auth Provider:**
- Custom API Key authentication
  - Implementation: Filter-based (likely in `lib-shared`)
  - Header: `X-API-Key` (used in `lib-consultas-client`)
  - Configuration: Via `API_KEY` environment variable (default: empty)
  - Applied to: All modules that need inter-service authentication

**SMTP/Email Authentication:**
- Dynamic SMTP account management
  - Module: `api-email`
  - Stored in PostgreSQL (table: likely `conta_email`)
  - Supports multiple email accounts with one marked as default
  - Credentials stored: username, password (per SMTP provider)
  - Pre-configured providers: `api-email/src/main/java/br/com/erpkit/email/config/PresetSmtp.java`

## Monitoring & Observability

**Error Tracking:**
- Not detected - No explicit error tracking service (Sentry, Rollbar, etc.)

**Logs:**

- SLF4J + Logback
  - Default output: Console (stdout)
  - Module-specific config: `logging.level.br.com.erpkit.consultas: INFO` in `api-consultas`
  - Captured logs: REST calls, circuit breaker state changes, provider failures

**Health Check:**
- Spring Boot Actuator endpoint (implicit)
  - Endpoint: `/health` (standard Spring Boot)
  - Used by: `lib-consultas-client` to check module availability
  - Response format: `{"status": "UP"}`

## CI/CD & Deployment

**Hosting:**
- Not detected - No cloud platform integration specified
- Target: On-premises or virtual machines (Linux/macOS/Windows)
- Deployment via JAR distribution (Spring Boot fat JAR)

**CI Pipeline:**
- Not detected - No GitHub Actions, GitLab CI, Jenkins, or other CI service configured
- Manual build via provided scripts: `scripts/build.sh`, `scripts/build.cmd`

**Build & Packaging:**
- Maven with Spring Boot Maven Plugin
  - Command: `mvnw clean package`
  - Output: Executable JARs in `target/` per module
  - JAR naming: `{module}-{version}.jar`

**Deployment Scripts:**
- `scripts/deploy.sh` / `scripts/deploy.cmd` - Deployment automation (likely JAR placement, service restart)
- `scripts/install.sh` / `scripts/install.cmd` - Installation automation

**Windows Installer:**
- Inno Setup configuration in `installer/erp-modulos-installer.iss`
  - Purpose: Package modules into a Windows installer
  - Generates: `.exe` installer executable

## Environment Configuration

**Required env vars:**

For modules:
- `API_KEY` - API Key for module-to-module authentication (default: empty, optional)
- `STORAGE_DIR` - Root directory for file uploads in `api-storage` (default: `./uploads`)
- `STORAGE_BASE_URL` - Base URL for serving files (default: `http://localhost:8085`)

For database:
- Database credentials are hardcoded in `application.yml` for development
  - Production deployments should override via Spring profiles or environment variables
  - Username: `erp_calhas` (development)
  - Password: `erp_calhas_dev` (development)

**Secrets location:**
- Embedded in `application.yml` (development only)
- Best practice: Override in production via environment variables or Spring Cloud Config
- No `.env` file support detected (Spring Boot typically uses environment variables or `application-{profile}.yml`)

## Webhooks & Callbacks

**Incoming:**
- None detected - Modules are consumer-only (no webhook receivers)

**Outgoing:**
- None detected - Modules call external APIs but don't expose callback/webhook subscriptions

---

*Integration audit: 2026-05-05*
