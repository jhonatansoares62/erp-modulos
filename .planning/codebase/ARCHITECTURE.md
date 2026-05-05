# Architecture

**Analysis Date:** 2026-05-05

## System Overview

```text
┌──────────────────────────────────────────────────────────────────────┐
│                    HTTP REST Endpoints (Standalones)                 │
├──────────────┬──────────────┬──────────────┬────────────────────────┤
│  api-email   │ api-storage  │ api-consultas│ (Spring Boot apps)     │
│  Port 9091   │ Port 8085    │ Port 9192    │                        │
└──────┬───────┴──────┬───────┴──────┬───────┴────────────────────────┘
       │              │              │
       └──────────────┼──────────────┘
                      │
        ┌─────────────▼──────────────┐
        │    Shared Infrastructure   │
        ├─────────────────────────── │
        │ • lib-shared (DTO, config) │
        │ • GlobalExceptionHandler   │
        │ • ApiKeyFilter             │
        │ • CORS config              │
        └──────────────────────────┘
                      │
        ┌─────────────▼──────────────────────┐
        │    Client Libraries                │
        ├────────────────────────────────── │
        │ • lib-consultas-client (Consumer)  │
        │   - HTTP client + DTOs             │
        │   - Resilience4j (Circuit Breaker) │
        │   - Auto-config for ERPs           │
        └────────────────────────────────────┘
                      │
        ┌─────────────▼──────────────┐
        │  External Services         │
        ├─────────────────────────── │
        │ • PostgreSQL (api-email)   │
        │ • PostgreSQL (api-storage) │
        │ • BrasilAPI (Consultas)    │
        │ • ViaCEP (Consultas)       │
        │ • SMTP Servers             │
        └────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| api-email | Email queue, SMTP accounts, template rendering | `api-email/src/main/java/br/com/erpkit/email/` |
| api-storage | File upload, filesystem storage, thumbnails | `api-storage/src/main/java/br/com/erpkit/storage/` |
| api-consultas | CEP/CNPJ external queries, fallback providers, caching | `api-consultas/src/main/java/br/com/erpkit/consultas/` |
| lib-shared | Cross-module DTOs, exception handling, filters | `lib-shared/src/main/java/br/com/erpkit/shared/` |
| lib-consultas-client | HTTP client library for consuming api-consultas | `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/` |

## Pattern Overview

**Overall:** Multi-Service Monorepo (Maven) with Pluggable Modules

**Key Characteristics:**
- Each `api-*` module is a standalone Spring Boot application (executable JAR)
- Library modules (`lib-*`) are imported as Maven dependencies into services
- Services share common DTOs and exception handling via `lib-shared`
- `lib-consultas-client` is reusable by external ERPs via Maven import
- Each service has its own PostgreSQL database (database-per-service pattern)
- All services include OpenAPI/Swagger UI for documentation

## Layers

**Presentation Layer (Controllers):**
- Purpose: Handle HTTP REST requests, validate inputs
- Location: `{module}/src/main/java/br/com/erpkit/{module}/controller/`
- Contains: `@RestController` classes mapping HTTP endpoints
- Depends on: Service layer
- Used by: HTTP clients, external systems

**Service Layer:**
- Purpose: Business logic, orchestration, state management
- Location: `{module}/src/main/java/br/com/erpkit/{module}/service/`
- Contains: `@Service` classes with domain operations
- Depends on: Repository layer, external APIs, `lib-shared`
- Used by: Controllers, other services

**Repository/Data Access Layer:**
- Purpose: Database operations via Spring Data JPA
- Location: `{module}/src/main/java/br/com/erpkit/{module}/repository/`
- Contains: `extends JpaRepository` interfaces with custom queries
- Depends on: Entity models, Spring Data
- Used by: Service layer

**Domain/Model Layer:**
- Purpose: Entity definitions, data mapping
- Location: `{module}/src/main/java/br/com/erpkit/{module}/model/`
- Contains: `@Entity` annotated classes for tables
- Depends on: Jakarta Persistence (JPA)
- Used by: Repositories, services

**Data Transfer Layer:**
- Purpose: API request/response contracts
- Location: `{module}/src/main/java/br/com/erpkit/{module}/dto/`
- Contains: Request DTOs (`*CreateDTO`), Response DTOs
- Depends on: Jakarta Validation
- Used by: Controllers

**Configuration Layer:**
- Purpose: Spring beans, external integrations, security
- Location: `{module}/src/main/java/br/com/erpkit/{module}/config/`
- Contains: `@Configuration` classes, SecurityConfig, custom beans
- Depends on: Spring core, external libraries
- Used by: Entire application context

**Shared Layer:**
- Purpose: Cross-cutting concerns across all modules
- Location: `lib-shared/src/main/java/br/com/erpkit/shared/`
- Contains: `GlobalExceptionHandler`, `ApiKeyFilter`, shared DTOs
- Depends on: Spring Web, Jakarta Servlet
- Used by: All `api-*` modules via Maven import

## Data Flow

### Primary Email Request Path

1. **HTTP POST** → `EmailController.criar()` (`api-email/src/main/java/br/com/erpkit/email/controller/EmailController.java:33`)
   - Receives `EmailCreateDTO` with email details (recipient, subject, body/template)
   - Validated by Jakarta Bean Validation (`@Valid`)

2. **Service Validation** → `EmailService.criar()` (`api-email/src/main/java/br/com/erpkit/email/service/EmailService.java:51`)
   - Validates email account exists via `ContaEmailService`
   - Renders Thymeleaf template if specified
   - Creates `Email` entity with status="pendente"

3. **Persistence** → `EmailRepository.save()` (`api-email/src/main/java/br/com/erpkit/email/repository/EmailRepository.java`)
   - Saves to PostgreSQL table `emails`
   - Returns `EmailResponse` to client

4. **Scheduled Processing** → `EmailService.processarFila()` (`api-email/src/main/java/br/com/erpkit/email/service/EmailService.java:137`)
   - Triggered every 30 seconds (configurable `modulo.email.intervalo-fila`)
   - Selects pending emails from DB (limit by max retries)
   - For each email: constructs dynamic SMTP sender from `ContaEmail` config
   - Sends via `JavaMailSender`, updates status to "enviado"
   - On failure: increments tentativas, updates erro_mensagem, sets status="falha" after max attempts

5. **Response** → API returns `EmailResponse` with id, status, timestamps

### Storage File Upload Flow

1. **HTTP POST** → `ArquivoController.upload()` (`api-storage/src/main/java/br/com/erpkit/storage/controller/ArquivoController.java`)
   - Receives `MultipartFile` + categoria, origem, referenciaId
   - Size validation: < max-tamanho-mb (50MB default)

2. **File Handling** → `StorageService.upload()` (`api-storage/src/main/java/br/com/erpkit/storage/service/StorageService.java:73`)
   - Generates UUID filename to prevent collisions
   - Creates date-based subdirectories (yyyy/MM)
   - Copies file to filesystem at `${modulo.storage.diretorio}/{yyyy/MM}/{uuid}.{ext}`

3. **Thumbnail Generation** → `StorageService.gerarThumbnail()` (if image)
   - Uses Thumbnailator library
   - Creates 200x200px thumbnail in same directory

4. **Database Recording** → `ArquivoRepository.save()`
   - Stores metadata: original filename, stored path, size, MIME type
   - Records categoria/origem/referenciaId for tracking

5. **Response** → Returns `ArquivoResponse` with download URLs

### CEP/CNPJ Query Flow (api-consultas)

1. **HTTP GET** → `ConsultasController.consultarCep()` (`api-consultas/src/main/java/br/com/erpkit/consultas/controller/ConsultasController.java:26`)
   - Receives CEP string, normalizes and validates

2. **Cache Check** → `@Cacheable` decorator (`api-consultas/src/main/java/br/com/erpkit/consultas/service/CepService.java:30`)
   - Caffeine in-memory cache checked by CEP key
   - Returns cached `EnderecoResponse` if hit

3. **Provider Fallback** → `CepService.consultar()` (`api-consultas/src/main/java/br/com/erpkit/consultas/service/CepService.java:31`)
   - Tries BrasilAPI provider first (via `BrasilApiProvider`)
   - On `ProviderIndisponivelException`: falls back to ViaCEP
   - Both decorated with Resilience4j circuit breaker + retry

4. **External HTTP** → Calls BrasilAPI or ViaCEP REST APIs
   - Implemented in `api-consultas/src/main/java/br/com/erpkit/consultas/service/provider/`

5. **Cache Store** → Result cached for subsequent requests

6. **Response** → Returns `EnderecoResponse` with address data

**State Management:**
- **api-email**: Email status machine (pendente → enviado/falha/cancelado)
- **api-storage**: File metadata in DB, actual files in filesystem
- **api-consultas**: No stateful entities; only read-only caching via Caffeine

## Key Abstractions

**EmailQueue:**
- Purpose: Decouple email sending from request handling
- Examples: `Email` entity + `@Scheduled` async processing
- Pattern: Database queue + scheduled task consumer
- File: `api-email/src/main/java/br/com/erpkit/email/service/EmailService.java:136`

**MultiProviderFallback:**
- Purpose: Resilient query service with automatic failover
- Examples: BrasilAPI → ViaCEP
- Pattern: Try primary, catch exception, fallback to secondary
- File: `api-consultas/src/main/java/br/com/erpkit/consultas/service/CepService.java`

**StoragePath Generation:**
- Purpose: Organize uploads by date, prevent filename collisions
- Examples: `yyyy/MM/{uuid}.ext`
- Pattern: Date-based hierarchy + UUID naming
- File: `api-storage/src/main/java/br/com/erpkit/storage/service/StorageService.java:79`

**SmtpSenderCache:**
- Purpose: Reuse SMTP connections across email sends
- Examples: `Map<Long, JavaMailSenderImpl>` keyed by account ID
- Pattern: Lazy-init cache with `computeIfAbsent`
- File: `api-email/src/main/java/br/com/erpkit/email/service/EmailService.java:189`

## Entry Points

**EmailApplication:**
- Location: `api-email/src/main/java/br/com/erpkit/email/EmailApplication.java`
- Triggers: `java -jar api-email-1.1.0-SNAPSHOT.jar`
- Responsibilities: 
  - Enables scheduling for email queue processing
  - Scans packages `br.com.erpkit.email` and `br.com.erpkit.shared`
  - Exposes OpenAPI at `http://localhost:9091/swagger-ui.html`
  - Listens on port 9091

**StorageApplication:**
- Location: `api-storage/src/main/java/br/com/erpkit/storage/StorageApplication.java`
- Triggers: `java -jar api-storage-1.1.0-SNAPSHOT.jar`
- Responsibilities:
  - Initializes filesystem storage directory
  - Listens on port 8085
  - Exposes OpenAPI at `http://localhost:8085/swagger-ui.html`

**ConsultasApplication:**
- Location: `api-consultas/src/main/java/br/com/erpkit/consultas/ConsultasApplication.java`
- Triggers: `java -jar api-consultas-1.1.0-SNAPSHOT.jar`
- Responsibilities:
  - Enables Caffeine caching for CEP/CNPJ responses
  - Listens on port 9192
  - Scans all packages under `br.com.erpkit`
  - Exposes OpenAPI at `http://localhost:9192/swagger-ui.html`

## Architectural Constraints

- **Threading:** Single-threaded event loop per service; scheduled tasks use Spring's TaskScheduler (thread pool)
- **Global state:** Email SMTP sender cache is thread-safe `ConcurrentHashMap` (`api-email/src/main/java/br/com/erpkit/email/service/EmailService.java:40`)
- **Circular imports:** None detected; clear dependency hierarchy: controllers → services → repositories + external APIs
- **Database isolation:** Each service owns its PostgreSQL database; no cross-service queries
- **External API dependency:** api-consultas depends on BrasilAPI and ViaCEP; fallback mechanism protects against single provider failure
- **ClassPath scanning:** Each application scans only its own packages (`scanBasePackages` in `@SpringBootApplication`)

## Anti-Patterns

### Over-eager Email Retry

**What happens:** Email service retries up to `max-tentativas` (default 3) regardless of error type. Network timeouts, SMTP auth failures, and permanent failures all increment the same counter.

**Why it's wrong:** Temporary SMTP server downtime consumes retry budget that should be reserved for permanent failures. After 3 attempts, email is marked "falha" and abandoned even if SMTP recovers.

**Do this instead:** Differentiate error types in `EmailService.processarFila()` (`api-email/src/main/java/br/com/erpkit/email/service/EmailService.java:146`). Use exponential backoff for transient errors (5xx) vs immediate failure for permanent errors (4xx auth).

### No Transaction Boundaries in processarFila()

**What happens:** Email queue processor loads pending emails, processes them in a loop, and updates status individually. If the scheduled task crashes mid-loop, partially processed emails are left inconsistent.

**Why it's wrong:** No `@Transactional` wrapping means updates are committed immediately without rollback on exception. Subsequent scheduler runs may retry emails already sent.

**Do this instead:** Wrap the loop in `@Transactional(isolation = Isolation.SERIALIZABLE)` or use optimistic locking with version fields. Add a "processing" status state to prevent duplicate sends.

### No Circuit Breaker on SMTP Sender Creation

**What happens:** `SmtpSenderImpl` is created on every email send attempt. If SMTP host is unreachable, connection timeouts accumulate without backoff.

**Why it's wrong:** Transient SMTP server outages cause a cascade of failed attempts that saturate the email queue backlog.

**Do this instead:** Wrap `JavaMailSender` creation in Resilience4j circuit breaker. Mark SMTP account as "degraded" after N consecutive failures. Skip queue processing for degraded accounts.

## Error Handling

**Strategy:** Centralized exception mapping via `@RestControllerAdvice`

**Patterns:**
- `ModuloException`: Custom exception with `HttpStatus`; mapped by `GlobalExceptionHandler` to `ErrorResponse` JSON
- `MethodArgumentNotValidException`: Bean validation failures return 400 with field-level error details
- Generic `Exception`: Fallback handler returns 500 with error message
- External API errors: Wrapped in `ProviderIndisponivelException` (api-consultas), re-thrown as `ModuloException`

**Files:**
- `lib-shared/src/main/java/br/com/erpkit/shared/exception/GlobalExceptionHandler.java`
- `lib-shared/src/main/java/br/com/erpkit/shared/exception/ModuloException.java`

## Cross-Cutting Concerns

**Logging:** 
- Framework: SLF4J + Logback (Spring default)
- Pattern: Each service logs at `INFO` level for domain operations (email sent, file uploaded); `WARN` on retries; `ERROR` on failures
- Example: `api-email/src/main/java/br/com/erpkit/email/service/EmailService.java:75` logs successful send

**Validation:** 
- Framework: Jakarta Bean Validation (javax.validation)
- Pattern: DTOs annotated with `@NotNull`, `@Email`, `@Size`; controller method parameters use `@Valid` to trigger validation
- Example: `EmailController.criar()` validates `@Valid @RequestBody EmailCreateDTO`

**Authentication:** 
- Framework: Custom API key filter
- Pattern: All endpoints except public paths (`/health`, `/api/info`, `/swagger-ui`, `/v3/api-docs`) require `X-API-Key` header
- Implementation: `lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java`
- Configuration: API key sourced from environment variable `${API_KEY}`

---

*Architecture analysis: 2026-05-05*
