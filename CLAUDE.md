## Project

**ERP Modulos**

Monorepo Spring Boot de **modulos reaproveitaveis** que o ERPKit consome em cada ERP de cliente (MUDAS, CALHAS, futuros). Cada modulo expoe uma capacidade transversal (envio de email, storage de arquivos, consultas externas, integracao WhatsApp, etc.) como um par `api-<dominio>` (servico Spring Boot) + `lib-<dominio>-client` (starter Spring Boot com auto-config + SPI), instalado on-premise junto do ERP do cliente.

A milestone ativa adiciona o **Modulo WhatsApp** (`api-whatsapp` + `lib-whatsapp-client`) seguindo o padrao ja estabelecido por `api-consultas` + `lib-consultas-client`. WhatsApp Cloud API com modelo **reativo puro de custo zero**: cliente sempre inicia, ERP responde dentro da janela 24h, nunca usa template pago.

**Core Value:** **Modulos reaproveitaveis entre ERPs ERPKit, sem custo operacional recorrente com terceiros.** Pro Modulo WhatsApp especificamente, isso significa **custo zero de Meta garantido por design** — nao via disciplina, via arquitetura.

### Constraints

- **Custo**: ZERO de Meta. Garantido por (a) sem API de template, (b) trava hard de janela 24h, (c) reativo puro (sem listeners de eventos do ERP) — Custo recorrente com terceiros e o maior risco operacional do produto
- **Tech stack**: Spring Boot 3.5.9 + Java 21 + Maven — alinhado com o monorepo; novo modulo nao deve introduzir framework alternativo
- **Padrao arquitetural**: `api-<dominio>` + `lib-<dominio>-client` com auto-config condicional + Resilience4j — alinhamento com api-consultas/lib-consultas-client e Cleanliness do monorepo
- **Persistencia**: PostgreSQL local 5433 do cliente, schema `whatsapp` isolado, Flyway no boot — D1 (dados ficam no PC do cliente, nao em servidor ERPKit)
- **Deployment**: On-premise por cliente, Windows Service via WinSW, dependente de `postgresql-x64-15-erpmudas` — alinhamento com pacote MUDAS atual
- **Idempotencia**: `wamid` UNIQUE em `mensagens_log` — Meta reenvia entregas se webhook nao responde 200 em <5s
- **Janela 24h**: implicita arquiteturalmente (D3 reativo) e travada explicitamente (D5 hard-block) — gera custo se quebrada
- **HMAC**: validacao de `X-Hub-Signature-256` obrigatoria em todo POST do Meta — webhook publico precisa rejeitar trafico nao assinado
- **Escopo cross-repo**: Engate em ERP-MUDAS e installer ficam **fora** — este projeto nao toca codigo em `C:\projetos\ERP-MUDAS\`

## Technology Stack

## Languages
- Java 21 - Core language for all Spring Boot modules and libraries
- YAML - Configuration management (`application.yml` files across modules)
- SQL - PostgreSQL database schema and migrations via Flyway
- Shell (Bash/CMD) - Build and deployment scripts in `scripts/` directory
- Inno Setup Script (.iss) - Windows installer configuration in `installer/erp-modulos-installer.iss`
## Runtime
- Java Development Kit (JDK) 21 - Required for building and running all modules
- Spring Boot 3.5.9 (via parent POM) - Framework runtime for all API modules
- Maven 3.x - Build and dependency management via Maven Wrapper
- Lockfile: `pom.xml` (root + per-module) - Maven dependency declarations with version pinning
## Frameworks
- Spring Boot 3.5.9 - Application framework and auto-configuration
- SpringDoc OpenAPI 2.8.15 - OpenAPI 3.0 specification generation and Swagger UI
- Resilience4j 2.2.0 - Circuit breaker and retry patterns
- Caffeine - In-memory cache implementation in `api-consultas` for CEP/CNPJ results
## Key Dependencies
- `lib-shared` (internal) - Shared exceptions, DTOs, and filters used across all modules
- `lib-consultas-client` (internal) - HTTP client library for `api-consultas` module with circuit breaker/retry
- PostgreSQL JDBC Driver - Runtime dependency for production database access
- Flyway 9.22.x (via Spring Boot BOM) - Database migration management
- H2 Database 2.x (via Spring Boot BOM) - In-memory SQL database for tests
- Spring Boot Test Starter - JUnit 5, Mockito, AssertJ
- Thumbnailator 0.4.20 - Image thumbnail generation in `api-storage`
- SLF4J API (via Spring Boot parent) - Logging facade
- Logback (transitive via Spring Boot) - Default logging implementation
- Spring RestTemplate (via `spring-web`) - HTTP client for external API calls
- Jakarta Servlet API - Provided scope in `lib-shared` (compatibility with servlet containers)
## Configuration
- `application.yml` per module - Main Spring Boot configuration file
- `application-test.yml` - Test-specific overrides in `api-email` and `api-storage`
- Environment variables:
- PostgreSQL connection strings in `application.yml` per module
- Default credentials in config: `erp_calhas` / `erp_calhas_dev`
- Connection pooling: HikariCP (default Spring Boot provider)
- DDL mode: `validate` (schema must exist, no auto-creation)
- `.pom.xml` files - Maven configuration with dependency management
- `api-email`: 9091
- `api-storage`: 8085
- `api-consultas`: 9192
## Platform Requirements
- Java 21 JDK (or compatible OpenJDK)
- Maven 3.9+ (provided via Maven Wrapper)
- PostgreSQL 12+ (for local database)
- Git (for version control)
- Bash or Windows CMD (for build/deploy scripts)
- Java 21 runtime (OpenJDK or Oracle JDK)
- PostgreSQL 12+ (standalone or managed service)
- Linux, macOS, or Windows server environment
- Network access to external APIs:
- SMTP server (for email sending via `api-email`)

## Conventions

## Naming Patterns
- Entity/Model files: `Email.java`, `ContaEmail.java`, `Arquivo.java` — use singular nouns matching the database table semantically
- DTO files: `EmailCreateDTO.java`, `EmailResponse.java`, `ContaEmailResponse.java` — suffix `DTO` for input models, `Response` for output
- Service files: `EmailService.java`, `StorageService.java` — suffix `Service`
- Controller files: `EmailController.java`, `ContaEmailController.java` — suffix `Controller`
- Repository files: `EmailRepository.java`, `ContaEmailRepository.java` — suffix `Repository`
- Test files: `EmailServiceTest.java`, `EmailControllerTest.java` — class name + `Test` suffix
- Exception files: `ModuloException.java`, `ConsultasException.java` — suffix `Exception`
- Verbs in Portuguese or English camelCase: `criar()`, `buscar()`, `listar()`, `reenviar()`, `cancelar()`, `renderizarTemplate()`, `invalidarCacheConta()`, `gerarThumbnail()`
- Boolean methods use `is`/`tem` prefix: `isTls()`, `isHtml()`, `isTemThumbnail()`, `temThumbnail()`
- Private helper methods use leading lowercase camelCase: `toResponse()`, `criarMailSender()`, `validarArquivo()`, `extrairExtensao()`
- Instance fields: camelCase, private with explicit getters/setters: `destinatario`, `contaPadrao`, `maxTentativas`, `senderCache`
- Constants: UPPER_SNAKE_CASE: `MAX_TENTATIVAS`, `TIPOS_IMAGEM`, `DIR_FORMAT`, `API_KEY`
- Collection names: plural form or descriptive: `pendentes`, `campos`, `stats`, `variaveis`
- Database column names: snake_case in `@Column(name="...")`: `conta_id`, `erro_mensagem`, `referencia_id`, `template_variaveis`
- DTOs are public POJOs with Jakarta Validation annotations
- Entities are JPA `@Entity` classes with `@Id` and `@GeneratedValue`
- Responses are DTO-like objects matching REST contract
- Exception types extend `RuntimeException` and carry context (e.g., `HttpStatus` in `ModuloException`)
## Code Style
- No explicit formatting config file (no Spotless, Checkstyle, or Prettier integration)
- Java 21 as target language version (`<java.version>21</java.version>`)
- UTF-8 encoding: `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>`
- 4-space indentation (observed in source files)
- Line length: no explicit limit observed, lines typically under 120 characters
- All files follow Jakarta EE standard (jakarta.* imports instead of javax.*)
- No dedicated linting configuration present
- Maven validates syntax at compile time via `maven-compiler-plugin`
- Spring Boot parent POM provides default quality settings
## Import Organization
- No explicit path aliases configured
- All imports are absolute from package root `br.com.erpkit`
- Module structure prevents circular dependencies through Maven module separation
- `import static` for test assertions: `import static org.junit.jupiter.api.Assertions.*`, `import static org.mockito.Mockito.*`, `import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*`
## Error Handling
- Use `ModuloException` (`br.com.erpkit.shared.exception.ModuloException`) for all business logic errors
- Unchecked exception pattern (extends `RuntimeException`) — no try/catch for ModuloException
- Repository operations throw ModuloException via `orElseThrow()` with lambda
- Validation errors automatically converted to HTTP 400 via `GlobalExceptionHandler.handleValidation()` (`/c/projetos/erp-modulos/lib-shared/src/main/java/br/com/erpkit/shared/exception/GlobalExceptionHandler.java:26`)
- Generic exceptions caught and converted to HTTP 500 via `GlobalExceptionHandler.handleGeneric()` (`/c/projetos/erp-modulos/lib-shared/src/main/java/br/com/erpkit/shared/exception/GlobalExceptionHandler.java:37`)
## Logging
- Logger field: `private static final Logger log = LoggerFactory.getLogger(ClassName.class)` (see `EmailService.java:35`, `StorageService.java:38`)
- Log levels used:
- Message format: descriptive string with placeholders `{}` followed by parameters
- Example: `log.info("Email enfileirado: id={}, para={}, conta={}", email.getId(), email.getDestinatario(), conta.getNome())` (`EmailService.java:75`)
## Comments
- Minimal inline comments; self-documenting method names preferred
- Block comments above complex logic or non-obvious algorithms (e.g., PNG binary header in `StorageServiceTest.java:201`)
- Business rule explanations in comments (e.g., "Simular que o email falhou" in tests)
- No JavaDoc on public service methods (conventions rely on clear naming instead)
- Not used in this Java codebase
- Method parameter and return types documented via IDE hints through explicit declarations
## Function Design
- Methods typically 10–50 lines in services
- Controllers are thin wrappers (5–15 lines) delegating to services
- Single responsibility: `criar()` creates and returns DTO, `buscar()` fetches by ID, `listar()` returns paginated results
- Prefer dependency injection via constructor: `public EmailService(EmailRepository emailRepository, ContaEmailService contaEmailService, TemplateEngine templateEngine)` (`EmailService.java:45`)
- DTO pattern for multiple parameters: pass single `EmailCreateDTO` instead of individual fields
- Validation via `@Valid` annotation on controller parameters: `public ResponseEntity<EmailResponse> criar(@Valid @RequestBody EmailCreateDTO dto)`
- Optional config via `@Value`: `@Value("${modulo.email.max-tentativas:3}") private int maxTentativas`
- Service methods return domain models or DTOs: `EmailResponse`, `Page<EmailResponse>`, `Map<String, Long>`
- Controllers wrap responses in `ResponseEntity`: `ResponseEntity.status(HttpStatus.CREATED).body(response)`, `ResponseEntity.ok(...)`
- `Page<T>` from Spring Data for paginated results
- Maps for statistics: `Map<String, Long> stats` in `estatisticas()`
## Module Design
- Each module (api-email, api-storage, etc.) is standalone Spring Boot application
- Shared code in `lib-shared` exported as library JAR (not executable)
- Internal packages: `br.com.erpkit.{modulename}.{layer}` — e.g., `br.com.erpkit.email.service`, `br.com.erpkit.email.controller`, `br.com.erpkit.email.repository`
- Public API via REST controllers only; services not exposed outside module
- No explicit barrel/index files
- Each component (DTO, Service, Repository, Entity) is independent file
## Language & Identifiers
- All identifiers, comments, and user-facing messages in Portuguese
- Example identifiers: `destinatario`, `remetente`, `assunto`, `corpo`, `conta`, `arquivo`, `criado_em`, `atualizado_em`
- Example messages: "Email não encontrado", "Email já foi enviado", "Destinatário é obrigatório"
- This is a business domain convention for Brazilian ERP context
## Data Transfer & Validation
- Input DTO: `EmailCreateDTO` with Jakarta Validation annotations (`@NotBlank`, `@Email`)
- Response DTO: `EmailResponse` with same fields as domain model for JSON serialization
- Mapping: Manual via `toResponse(Email entity)` method in service layer (see `EmailService.java:219`)
- No MapStruct or ModelMapper — explicit mapping is preferred for clarity
- `@NotBlank(message="...")` for required text fields
- `@Email(message="...")` for email format validation
- `@Valid` on controller parameters to trigger validation
- Custom validators: `DocumentoValidator` for CEP/CNPJ normalization and validation
## Transaction Management
- Service methods are implicitly transactional via Spring Data repositories
- No explicit `@Transactional` annotations observed (relies on default Spring Boot behavior)
- Each repository save/delete is atomic

## Architecture

## System Overview
```text
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
- Each `api-*` module is a standalone Spring Boot application (executable JAR)
- Library modules (`lib-*`) are imported as Maven dependencies into services
- Services share common DTOs and exception handling via `lib-shared`
- `lib-consultas-client` is reusable by external ERPs via Maven import
- Each service has its own PostgreSQL database (database-per-service pattern)
- All services include OpenAPI/Swagger UI for documentation
## Layers
- Purpose: Handle HTTP REST requests, validate inputs
- Location: `{module}/src/main/java/br/com/erpkit/{module}/controller/`
- Contains: `@RestController` classes mapping HTTP endpoints
- Depends on: Service layer
- Used by: HTTP clients, external systems
- Purpose: Business logic, orchestration, state management
- Location: `{module}/src/main/java/br/com/erpkit/{module}/service/`
- Contains: `@Service` classes with domain operations
- Depends on: Repository layer, external APIs, `lib-shared`
- Used by: Controllers, other services
- Purpose: Database operations via Spring Data JPA
- Location: `{module}/src/main/java/br/com/erpkit/{module}/repository/`
- Contains: `extends JpaRepository` interfaces with custom queries
- Depends on: Entity models, Spring Data
- Used by: Service layer
- Purpose: Entity definitions, data mapping
- Location: `{module}/src/main/java/br/com/erpkit/{module}/model/`
- Contains: `@Entity` annotated classes for tables
- Depends on: Jakarta Persistence (JPA)
- Used by: Repositories, services
- Purpose: API request/response contracts
- Location: `{module}/src/main/java/br/com/erpkit/{module}/dto/`
- Contains: Request DTOs (`*CreateDTO`), Response DTOs
- Depends on: Jakarta Validation
- Used by: Controllers
- Purpose: Spring beans, external integrations, security
- Location: `{module}/src/main/java/br/com/erpkit/{module}/config/`
- Contains: `@Configuration` classes, SecurityConfig, custom beans
- Depends on: Spring core, external libraries
- Used by: Entire application context
- Purpose: Cross-cutting concerns across all modules
- Location: `lib-shared/src/main/java/br/com/erpkit/shared/`
- Contains: `GlobalExceptionHandler`, `ApiKeyFilter`, shared DTOs
- Depends on: Spring Web, Jakarta Servlet
- Used by: All `api-*` modules via Maven import
## Data Flow
### Primary Email Request Path
### Storage File Upload Flow
### CEP/CNPJ Query Flow (api-consultas)
- **api-email**: Email status machine (pendente → enviado/falha/cancelado)
- **api-storage**: File metadata in DB, actual files in filesystem
- **api-consultas**: No stateful entities; only read-only caching via Caffeine
## Key Abstractions
- Purpose: Decouple email sending from request handling
- Examples: `Email` entity + `@Scheduled` async processing
- Pattern: Database queue + scheduled task consumer
- File: `api-email/src/main/java/br/com/erpkit/email/service/EmailService.java:136`
- Purpose: Resilient query service with automatic failover
- Examples: BrasilAPI → ViaCEP
- Pattern: Try primary, catch exception, fallback to secondary
- File: `api-consultas/src/main/java/br/com/erpkit/consultas/service/CepService.java`
- Purpose: Organize uploads by date, prevent filename collisions
- Examples: `yyyy/MM/{uuid}.ext`
- Pattern: Date-based hierarchy + UUID naming
- File: `api-storage/src/main/java/br/com/erpkit/storage/service/StorageService.java:79`
- Purpose: Reuse SMTP connections across email sends
- Examples: `Map<Long, JavaMailSenderImpl>` keyed by account ID
- Pattern: Lazy-init cache with `computeIfAbsent`
- File: `api-email/src/main/java/br/com/erpkit/email/service/EmailService.java:189`
## Entry Points
- Location: `api-email/src/main/java/br/com/erpkit/email/EmailApplication.java`
- Triggers: `java -jar api-email-1.1.0-SNAPSHOT.jar`
- Responsibilities: 
- Location: `api-storage/src/main/java/br/com/erpkit/storage/StorageApplication.java`
- Triggers: `java -jar api-storage-1.1.0-SNAPSHOT.jar`
- Responsibilities:
- Location: `api-consultas/src/main/java/br/com/erpkit/consultas/ConsultasApplication.java`
- Triggers: `java -jar api-consultas-1.1.0-SNAPSHOT.jar`
- Responsibilities:
## Architectural Constraints
- **Threading:** Single-threaded event loop per service; scheduled tasks use Spring's TaskScheduler (thread pool)
- **Global state:** Email SMTP sender cache is thread-safe `ConcurrentHashMap` (`api-email/src/main/java/br/com/erpkit/email/service/EmailService.java:40`)
- **Circular imports:** None detected; clear dependency hierarchy: controllers → services → repositories + external APIs
- **Database isolation:** Each service owns its PostgreSQL database; no cross-service queries
- **External API dependency:** api-consultas depends on BrasilAPI and ViaCEP; fallback mechanism protects against single provider failure
- **ClassPath scanning:** Each application scans only its own packages (`scanBasePackages` in `@SpringBootApplication`)
## Anti-Patterns
### Over-eager Email Retry
### No Transaction Boundaries in processarFila()
### No Circuit Breaker on SMTP Sender Creation
## Error Handling
- `ModuloException`: Custom exception with `HttpStatus`; mapped by `GlobalExceptionHandler` to `ErrorResponse` JSON
- `MethodArgumentNotValidException`: Bean validation failures return 400 with field-level error details
- Generic `Exception`: Fallback handler returns 500 with error message
- External API errors: Wrapped in `ProviderIndisponivelException` (api-consultas), re-thrown as `ModuloException`
- `lib-shared/src/main/java/br/com/erpkit/shared/exception/GlobalExceptionHandler.java`
- `lib-shared/src/main/java/br/com/erpkit/shared/exception/ModuloException.java`
## Cross-Cutting Concerns
- Framework: SLF4J + Logback (Spring default)
- Pattern: Each service logs at `INFO` level for domain operations (email sent, file uploaded); `WARN` on retries; `ERROR` on failures
- Example: `api-email/src/main/java/br/com/erpkit/email/service/EmailService.java:75` logs successful send
- Framework: Jakarta Bean Validation (javax.validation)
- Pattern: DTOs annotated with `@NotNull`, `@Email`, `@Size`; controller method parameters use `@Valid` to trigger validation
- Example: `EmailController.criar()` validates `@Valid @RequestBody EmailCreateDTO`
- Framework: Custom API key filter
- Pattern: All endpoints except public paths (`/health`, `/api/info`, `/swagger-ui`, `/v3/api-docs`) require `X-API-Key` header
- Implementation: `lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java`
- Configuration: API key sourced from environment variable `${API_KEY}`
