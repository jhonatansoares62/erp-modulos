# Codebase Concerns

**Analysis Date:** 2026-05-05

## Security Issues

### Plaintext Password Storage in Database

**Risk:** CRITICAL - Passwords for email accounts stored in plaintext in PostgreSQL database
- Files: `api-email/src/main/java/br/com/erpkit/email/model/ContaEmail.java:35`
- Problem: The `password` field in the `ContaEmail` entity is stored directly without encryption
- Impact: Database breach exposes all SMTP credentials; authentication across integrations compromised
- Current mitigation: File system permissions only
- Recommendations:
  1. Implement encryption for sensitive fields using JPA `@Convert` with `AttributeConverter`
  2. Use Spring Vault or AWS Secrets Manager for credential storage
  3. Migrate existing plaintext passwords using database migration script
  4. Add encryption to `ContaEmailService.criar()` and `ContaEmailService.atualizar()` before save

### Hardcoded Localhost Default in Production Configuration

**Risk:** MEDIUM - Client module defaults to localhost URL
- Files: `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/ConsultasProperties.java:9`
- Problem: `url = "http://localhost:9192"` is the fallback when configuration is missing
- Impact: If `app.modulos.consultas.url` is not set in production, client points to local machine
- Recommendations:
  1. Remove localhost default; require explicit configuration
  2. Add validation in `ConsultasProperties` setter to reject localhost URLs in production
  3. Document required env var clearly

### Weak API Key Validation in Tests

**Risk:** LOW - Test API key is hardcoded and visible
- Files: `lib-shared/src/test/java/br/com/erpkit/shared/security/ApiKeyFilterTest.java:18`
- Problem: Line 18 shows hardcoded test API key `"minha-chave-secreta"` used in multiple test methods
- Impact: If tests are reviewed in security audits, key pattern is revealed; no impact in production
- Recommendations:
  1. Extract test API key to `application-test.yml`
  2. Use `@Value` annotation to inject from properties

### API Key Can Be Disabled with Null or Blank Value

**Risk:** LOW - Authentication bypass possible through misconfiguration
- Files: `lib-shared/src/test/java/br/com/erpkit/shared/security/ApiKeyFilterTest.java:123-147`
- Problem: ApiKeyFilter allows all requests if API key is empty string or null (tested in lines 123-147)
- Impact: If `API_KEY` environment variable is missing or set to `""`, all endpoints are public
- Recommendations:
  1. Log warning when API key is empty in production profile
  2. Add validation in filter initialization to reject empty keys in production
  3. Consider using Spring Security with authentication provider instead of custom filter

### Hardcoded SMTP Configuration in Presets

**Risk:** INFORMATIONAL - Public SMTP server information, but needs care
- Files: `api-email/src/main/java/br/com/erpkit/email/config/PresetSmtp.java:19-58`
- Problem: Preset SMTP configurations are hardcoded as static Map
- Impact: User credentials for preset providers still require entry; presets themselves are public info
- Current mitigation: Credentials are user-provided and encrypted (once issue above is fixed)
- Recommendations: Document that this is metadata only; encryption of stored credentials is essential

## Tech Debt

### Large Service Classes with Multiple Responsibilities

**Area:** Email Processing Service
- Files: `api-email/src/main/java/br/com/erpkit/email/service/EmailService.java` (238 lines)
- Impact: High complexity, difficult to test in isolation; tight coupling of templating, SMTP, caching
- Fix approach:
  1. Extract SMTP sender creation to separate `SmtpSenderFactory` class
  2. Extract template rendering to `TemplateRenderingService`
  3. Extract scheduled task logic to `EmailQueueProcessor` class
  4. Use dependency injection to compose simpler, focused services

### Hardcoded RuntimeException in StorageService

**Area:** File System Initialization
- Files: `api-storage/src/main/java/br/com/erpkit/storage/service/StorageService.java:69`
- Problem: Line 69 throws `new RuntimeException()` instead of custom exception
- Impact: Inconsistent error handling; cannot be caught specifically by callers
- Fix approach: Replace with `ModuloException` to match codebase pattern

### Unchecked Type Casting in Statistics Queries

**Area:** Email and Storage Statistics
- Files: `api-email/src/main/java/br/com/erpkit/email/service/EmailService.java:128-134`
- Files: `api-storage/src/main/java/br/com/erpkit/storage/service/StorageService.java:161-183`
- Problem: `Object[]` results cast directly to `String` and `Long` without null checks
- Risk: ClassCastException or NullPointerException if query result structure changes
- Fix approach:
  1. Create typed DTO for query results using Spring Data JPA projections
  2. Add explicit null checks before casting
  3. Use constructor mapping: `@Query("SELECT new com.example.StatsDto(s.status, COUNT(s)) FROM ...)`

### Missing File Type Validation for Upload

**Area:** File Storage Security
- Files: `api-storage/src/main/java/br/com/erpkit/storage/service/StorageService.java:73-109`
- Problem: No whitelist for allowed file types; only validates size
- Risk: User can upload executable files (.exe, .jar, .sh) if mime-type header is forged
- Fix approach:
  1. Add `@NotEmpty Set<String> allowedMimeTypes` to StorageService
  2. Validate against this whitelist: `if (!TIPOS_PERMITIDOS.contains(file.getContentType()))`
  3. Add configuration property `modulo.storage.allowed-types: image/*,application/pdf`

### Path Traversal Risk in Archive Names

**Area:** File Storage Path Handling
- Files: `api-storage/src/main/java/br/com/erpkit/storage/service/StorageService.java:220-226`
- Problem: `nomeArmazenado` comes from database but could contain path traversal characters if input was not validated at upload
- Risk: If earlier validation is bypassed, attacker could read files outside upload directory
- Current protection: UUID generated at upload prevents this currently
- Fix approach:
  1. Add explicit path validation: `if (nomeArmazenado.contains("..") || nomeArmazenado.startsWith("/"))`
  2. Document that UUID storage path is mandatory security control
  3. Add assertion check in carregarRecurso() method

### Email CC Field Splits on Comma Without Validation

**Area:** Email Composition
- Files: `api-email/src/main/java/br/com/erpkit/email/service/EmailService.java:181-183`
- Problem: `.setCc(email.getCc().split(","))` assumes valid email format after split
- Risk: Invalid email addresses will cause MessagingException at send time
- Fix approach:
  1. Add email validation in `EmailService.enviar()` before split
  2. Create `EmailValidator` utility class with regex for email format
  3. Return meaningful error message if CC contains invalid addresses

## Test Coverage Gaps

### Low Test Coverage in api-consultas Module

**What's not tested:** Service layer, provider integrations, error handling
- Files: `api-consultas/src/main/java/br/com/erpkit/consultas/service/` (11 main files vs 1 test file)
- Risk: BrasilAPI and ViaCEP provider failures not covered; timeout/retry logic untested
- Priority: HIGH - External integrations are critical
- Fix approach:
  1. Add `BrasilApiProviderTest.java` with mocked RestTemplate
  2. Add `ProviderIndisponivelExceptionTest.java`
  3. Add `CepServiceTest.java` testing retry logic and circuit breaker
  4. Use `@RestClientTest` for RestTemplate mocking

### Low Test Coverage in lib-consultas-client Module

**What's not tested:** CircuitBreaker configuration, Retry logic, sanitization function
- Files: `lib-consultas-client/src/main/java/` (8 main files vs 1 test file)
- Risk: Resilience4j configuration could fail silently; input sanitization might be bypassed
- Priority: HIGH - This is a critical library dependency
- Fix approach:
  1. Add `ConsultasClientImplTest.java` with circuit breaker state tests
  2. Add tests for `sanitize()` method with various inputs (special chars, null, empty)
  3. Add integration test with mock server for full flow

### Low Test Coverage in api-storage Module

**What's not tested:** Thumbnail generation, file download, statistics aggregation
- Files: `api-storage/src/main/java/br/com/erpkit/storage/service/StorageService.java` (269 lines, 3 tests)
- Risk: Image processing library failures not covered; null handling in statistics untested
- Priority: MEDIUM
- Fix approach:
  1. Add `ThumbnailGenerationTest` mocking Thumbnails library
  2. Add test for concurrent uploads to same file path
  3. Add test for `carregarRecurso()` with missing file

### Insufficient Validation Testing in api-email

**What's not tested:** Invalid SMTP configuration handling, template rendering errors, email address validation
- Files: No test for `ContaEmailService` validation logic
- Risk: Invalid configurations stored to database; template errors at send time instead of creation time
- Priority: MEDIUM
- Fix approach:
  1. Add `ContaEmailServiceValidationTest` for host/port/credentials
  2. Test template variables with null/undefined variables
  3. Test special characters in remetente field

## Missing Critical Features

### No Password Encryption for Email Accounts

**What's missing:** Encryption of stored SMTP passwords
- Blocks: Cannot safely deploy to production with sensitive credentials
- Priority: CRITICAL - Security requirement

### No Input Validation on PathVariable Parameters

**What's missing:** Validation of CEP/CNPJ format in ConsultasController
- Files: `api-consultas/src/main/java/br/com/erpkit/consultas/controller/ConsultasController.java:26,31`
- Blocks: Invalid input passed to external APIs; poor error messages to client
- Priority: MEDIUM
- Fix approach:
  1. Add `@Pattern(regexp = "\\d{5}\\d{3}")` to CEP path variable
  2. Add `@Pattern(regexp = "\\d{14}")` to CNPJ path variable
  3. Add `@ControllerAdvice` handler for `ConstraintViolationException`

### No Audit Logging for Sensitive Operations

**What's missing:** Logging of email account creation/password changes, file downloads by user
- Impact: Cannot trace who accessed/modified sensitive data
- Priority: MEDIUM - Important for compliance
- Fix approach:
  1. Add audit logging in `ContaEmailService.criar()` and `.atualizar()`
  2. Add download logging in `StorageService.download()`
  3. Create `AuditLog` entity with user, action, timestamp, resource ID

### No Rate Limiting on File Upload

**What's missing:** Protection against storage DoS attacks
- Impact: Attacker can fill disk with large uploads
- Priority: MEDIUM
- Fix approach:
  1. Add `@RateLimiter` from Resilience4j on `ArquivoController.upload()`
  2. Implement per-user upload quota tracking
  3. Add configuration for max total storage size

## Performance Bottlenecks

### Email Sender Cache Not Thread-Safe

**Issue:** ConcurrentHashMap used, but creation logic in lambda may have race condition
- Files: `api-email/src/main/java/br/com/erpkit/email/service/EmailService.java:40`
- Problem: `computeIfAbsent()` creates sender but if creation takes time, multiple instances may be created
- Impact: Multiple SMTP connections to same account; resource leak
- Fix approach:
  1. Pre-create senders at application startup based on active accounts
  2. Use `@Cacheable` Spring annotation with cache manager instead of manual map
  3. Add eviction policy for accounts that are soft-deleted

### N+1 Query Risk in Statistics

**Issue:** `procesarFila()` method queries each pending email, then loads account for each
- Files: `api-email/src/main/java/br/com/erpkit/email/service/EmailService.java:137-167`
- Problem: For 100 pending emails, performs 101+ queries (1 for pendents, 100 for accounts)
- Impact: High database load during queue processing; potential timeouts
- Fix approach:
  1. Add `LEFT JOIN FETCH` in `buscarPendentesParaEnvio()` query
  2. Load `ContaEmail` eagerly with email: `@Query("SELECT e FROM Email e LEFT JOIN FETCH e.conta WHERE ...")`

### Statistics Query Not Indexed

**Issue:** GROUP BY queries without indexes on queried columns
- Files: `api-storage/src/main/java/br/com/erpkit/storage/repository/ArquivoRepository.java:24`
- Files: `api-email/src/main/java/br/com/erpkit/email/repository/EmailRepository.java:22`
- Problem: Full table scans to aggregate statistics
- Impact: Slow response as data grows; high CPU
- Fix approach:
  1. Add database index: `CREATE INDEX idx_arquivo_ativo_categoria ON arquivo(ativo, categoria)`
  2. Add index: `CREATE INDEX idx_email_status ON email(status)`
  3. Cache statistics results with `@CachePut` for 5 minutes

## Fragile Areas

### BrasilAPI Provider Without Fallback

**Area:** External API Integration
- Files: `api-consultas/src/main/java/br/com/erpkit/consultas/service/provider/BrasilApiProvider.java`
- Why fragile: Single hardcoded BASE_URL; no fallback if BrasilAPI becomes unavailable
- Risk: ProviderIndisponivelException breaks application flow if external API is down
- Safe modification:
  1. Test locally with ViaCEP fallback enabled
  2. Add health check endpoint that calls BrasilAPI to verify availability
  3. Document fallback strategy clearly
- Test coverage: No test coverage for timeout scenarios

### Template Engine Configuration Without Prefix Validation

**Area:** Email Template Resolution
- Files: `api-email/src/main/resources/application.yml:26-27`
- Why fragile: Relative path `classpath:/templates/` could fail if jar packaging changes
- Risk: Template not found errors at runtime, not at startup
- Safe modification:
  1. Add integration test that renders each template at startup
  2. Use `@PostConstruct` in `EmailService` to validate all referenced templates exist
- Test coverage: No test for missing template file

### File System Permissions Assumed to Exist

**Area:** Storage Directory Initialization
- Files: `api-storage/src/main/java/br/com/erpkit/storage/service/StorageService.java:63-71`
- Why fragile: `init()` only creates directory, doesn't check write permissions
- Risk: If directory is read-only (permissions issue), upload fails only when first file is uploaded
- Safe modification:
  1. Add test write: `Files.write(path, "test".getBytes()); Files.delete(path);` in `init()`
  2. Log permission errors clearly so devops can debug
- Test coverage: `StorageServiceTest` doesn't cover permission failures

## Deployment & Configuration Issues

### Hardcoded Localhost Base URL in Storage

**Issue:** Default production URL is localhost
- Files: `api-storage/src/main/resources/application.yml:35`
- Problem: `base-url: ${STORAGE_BASE_URL:http://localhost:8085}` — if env var missing, client gets localhost URLs
- Impact: File download links in responses point to local machine; API unusable from other services
- Fix approach:
  1. Remove localhost default; require explicit configuration
  2. Add validation: reject localhost URLs in non-dev profiles
  3. Use Spring profiles to inject correct URL by environment

### Database Credentials in YAML Files

**Issue:** Database username/password in plaintext in application.yml
- Files: `api-email/src/main/resources/application.yml:9-10`
- Files: `api-storage/src/main/resources/application.yml:9-10`
- Content: `username: erp_calhas` / `password: erp_calhas_dev`
- Problem: Credentials visible in version control and config files
- Fix approach:
  1. Use environment variables: `username: ${DB_USERNAME}`
  2. Add `.gitignore` rule: `application-local.yml`
  3. Document required env vars in `README.md`
  4. Use HashiCorp Vault or AWS Secrets Manager for credentials

### Flyway Auto-Migration Enabled

**Issue:** Automatic database schema migration enabled
- Files: `api-email/src/main/resources/application.yml:19-20`
- Problem: `baseline-on-migrate: true` allows auto-creation of schema; risky in production
- Fix approach:
  1. Disable auto-migration in production: use `spring.profiles.active=prod`
  2. Create `application-prod.yml` with `flyway.enabled: false`
  3. Document manual migration process in deployment guide

## Dependencies at Risk

### Spring Boot 3.5.9 Version Management

**Risk:** Version is stable but monitor for security updates
- Current: Spring Boot 3.5.9 (parent POM)
- Impact: Inherited by all modules; updates require rebuild of all
- Monitoring: Use `mvn versions:display-dependency-updates`

### Resilience4j Circuit Breaker Configuration

**Risk:** Hard-coded configuration values
- Files: `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/ConsultasClientImpl.java:44-58`
- Problem: CircuitBreaker `slidingWindowSize=10`, `failureRateThreshold=50%` are hardcoded
- Impact: Cannot tune for different environments without code change
- Fix approach:
  1. Move configuration to `ConsultasProperties`
  2. Allow Spring to inject from properties file
  3. Document these settings in deployment guide

### Thymeleaf Template Engine Risk

**Risk:** Untrusted template content could cause SSTI attacks
- Area: Email template rendering
- Impact: If user-provided template strings are processed, could execute SpEL
- Current mitigation: Templates loaded from classpath resources only (safe)
- Recommendations:
  1. Never process user input as template string
  2. Only use `templateName` parameter, not template content
  3. Document in security section of README

## Known Bugs

### Null Reference in Email CC Splitting

**Bug description:** NullPointerException risk when CC field is null
- Symptoms: Email send fails if CC is null (not just empty)
- Files: `api-email/src/main/java/br/com/erpkit/email/service/EmailService.java:181-183`
- Trigger: Create email with `cc: null` instead of empty string
- Current code: `if (email.getCc() != null && !email.getCc().isBlank())` — protects this
- Status: FIXED (already has null check)

### Thumbnail Generation Swallows Errors

**Bug description:** Failed thumbnail generation doesn't block upload
- Symptoms: Image uploaded successfully but no thumbnail created; user doesn't know
- Files: `api-storage/src/main/java/br/com/erpkit/storage/service/StorageService.java:207-217`
- Trigger: Upload image when Thumbnails library has permission issues
- Current: Line 216 logs warning only; `arquivo.setTemThumbnail(true)` is set regardless
- Recommendation: 
  1. Either fail upload if thumbnail fails (strict mode)
  2. Or set `temThumbnail=false` when exception caught
  3. Or retry thumbnail generation asynchronously

### Arquivo Entity Uses Mutable isActive State

**Bug description:** Soft delete relies on boolean flag; no audit trail
- Symptoms: Cannot recover deleted files; cannot see deletion history
- Files: `api-storage/src/main/java/br/com/erpkit/storage/model/Arquivo.java` (has `ativo` boolean)
- Trigger: Call `softDelete()`; file appears gone permanently
- Recommendation:
  1. Add `deletedAt: LocalDateTime` field instead of boolean
  2. Add `deletedBy: String` to track who deleted
  3. Allow restore for undeleted files within grace period

---

*Concerns audit: 2026-05-05*
