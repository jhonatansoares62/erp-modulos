# Coding Conventions

**Analysis Date:** 2026-05-05

## Naming Patterns

**Files:**
- Entity/Model files: `Email.java`, `ContaEmail.java`, `Arquivo.java` — use singular nouns matching the database table semantically
- DTO files: `EmailCreateDTO.java`, `EmailResponse.java`, `ContaEmailResponse.java` — suffix `DTO` for input models, `Response` for output
- Service files: `EmailService.java`, `StorageService.java` — suffix `Service`
- Controller files: `EmailController.java`, `ContaEmailController.java` — suffix `Controller`
- Repository files: `EmailRepository.java`, `ContaEmailRepository.java` — suffix `Repository`
- Test files: `EmailServiceTest.java`, `EmailControllerTest.java` — class name + `Test` suffix
- Exception files: `ModuloException.java`, `ConsultasException.java` — suffix `Exception`

**Functions:**
- Verbs in Portuguese or English camelCase: `criar()`, `buscar()`, `listar()`, `reenviar()`, `cancelar()`, `renderizarTemplate()`, `invalidarCacheConta()`, `gerarThumbnail()`
- Boolean methods use `is`/`tem` prefix: `isTls()`, `isHtml()`, `isTemThumbnail()`, `temThumbnail()`
- Private helper methods use leading lowercase camelCase: `toResponse()`, `criarMailSender()`, `validarArquivo()`, `extrairExtensao()`

**Variables:**
- Instance fields: camelCase, private with explicit getters/setters: `destinatario`, `contaPadrao`, `maxTentativas`, `senderCache`
- Constants: UPPER_SNAKE_CASE: `MAX_TENTATIVAS`, `TIPOS_IMAGEM`, `DIR_FORMAT`, `API_KEY`
- Collection names: plural form or descriptive: `pendentes`, `campos`, `stats`, `variaveis`
- Database column names: snake_case in `@Column(name="...")`: `conta_id`, `erro_mensagem`, `referencia_id`, `template_variaveis`

**Types:**
- DTOs are public POJOs with Jakarta Validation annotations
- Entities are JPA `@Entity` classes with `@Id` and `@GeneratedValue`
- Responses are DTO-like objects matching REST contract
- Exception types extend `RuntimeException` and carry context (e.g., `HttpStatus` in `ModuloException`)

## Code Style

**Formatting:**
- No explicit formatting config file (no Spotless, Checkstyle, or Prettier integration)
- Java 21 as target language version (`<java.version>21</java.version>`)
- UTF-8 encoding: `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>`
- 4-space indentation (observed in source files)
- Line length: no explicit limit observed, lines typically under 120 characters
- All files follow Jakarta EE standard (jakarta.* imports instead of javax.*)

**Linting:**
- No dedicated linting configuration present
- Maven validates syntax at compile time via `maven-compiler-plugin`
- Spring Boot parent POM provides default quality settings

## Import Organization

**Order:**
1. `package` declaration
2. Blank line
3. Jakarta/Java standard library imports: `jakarta.annotation.*`, `jakarta.mail.*`, `jakarta.servlet.*`, `jakarta.persistence.*`, `jakarta.validation.*`
4. Java standard library imports: `java.io.*`, `java.util.*`, `java.time.*`, `java.nio.*`, `java.net.*`
5. Blank line
6. Third-party framework imports: `org.springframework.*`, `org.slf4j.*`, `net.coobird.*`, `com.fasterxml.*`
7. Blank line
8. Project-internal imports: `br.com.erpkit.*`

**Path Aliases:**
- No explicit path aliases configured
- All imports are absolute from package root `br.com.erpkit`
- Module structure prevents circular dependencies through Maven module separation

**Consistent patterns:**
- `import static` for test assertions: `import static org.junit.jupiter.api.Assertions.*`, `import static org.mockito.Mockito.*`, `import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*`

## Error Handling

**Patterns:**
- Use `ModuloException` (`br.com.erpkit.shared.exception.ModuloException`) for all business logic errors
  - Constructor 1: `new ModuloException(String message)` → defaults to HTTP 422 (UNPROCESSABLE_ENTITY)
  - Constructor 2: `new ModuloException(String message, HttpStatus status)` → custom status (NOT_FOUND, BAD_REQUEST, etc.)
  - Example: `new ModuloException("Email não encontrado", HttpStatus.NOT_FOUND)` in `EmailService.buscar()` (`/c/projetos/erp-modulos/api-email/src/main/java/br/com/erpkit/email/service/EmailService.java:81`)
- Unchecked exception pattern (extends `RuntimeException`) — no try/catch for ModuloException
- Repository operations throw ModuloException via `orElseThrow()` with lambda
- Validation errors automatically converted to HTTP 400 via `GlobalExceptionHandler.handleValidation()` (`/c/projetos/erp-modulos/lib-shared/src/main/java/br/com/erpkit/shared/exception/GlobalExceptionHandler.java:26`)
- Generic exceptions caught and converted to HTTP 500 via `GlobalExceptionHandler.handleGeneric()` (`/c/projetos/erp-modulos/lib-shared/src/main/java/br/com/erpkit/shared/exception/GlobalExceptionHandler.java:37`)

## Logging

**Framework:** SLF4J (via Spring Boot Starter)

**Patterns:**
- Logger field: `private static final Logger log = LoggerFactory.getLogger(ClassName.class)` (see `EmailService.java:35`, `StorageService.java:38`)
- Log levels used:
  - `log.info()` for successful operations: "Email enfileirado", "Email enviado", "Storage inicializado"
  - `log.warn()` for retries: "Tentativa {}/{} falhou para email id={}"
  - `log.error()` for failures: "Email falhou definitivamente", "Não foi possível criar diretório"
- Message format: descriptive string with placeholders `{}` followed by parameters
- Example: `log.info("Email enfileirado: id={}, para={}, conta={}", email.getId(), email.getDestinatario(), conta.getNome())` (`EmailService.java:75`)

## Comments

**When to Comment:**
- Minimal inline comments; self-documenting method names preferred
- Block comments above complex logic or non-obvious algorithms (e.g., PNG binary header in `StorageServiceTest.java:201`)
- Business rule explanations in comments (e.g., "Simular que o email falhou" in tests)
- No JavaDoc on public service methods (conventions rely on clear naming instead)

**JSDoc/TSDoc:**
- Not used in this Java codebase
- Method parameter and return types documented via IDE hints through explicit declarations

## Function Design

**Size:**
- Methods typically 10–50 lines in services
- Controllers are thin wrappers (5–15 lines) delegating to services
- Single responsibility: `criar()` creates and returns DTO, `buscar()` fetches by ID, `listar()` returns paginated results

**Parameters:**
- Prefer dependency injection via constructor: `public EmailService(EmailRepository emailRepository, ContaEmailService contaEmailService, TemplateEngine templateEngine)` (`EmailService.java:45`)
- DTO pattern for multiple parameters: pass single `EmailCreateDTO` instead of individual fields
- Validation via `@Valid` annotation on controller parameters: `public ResponseEntity<EmailResponse> criar(@Valid @RequestBody EmailCreateDTO dto)`
- Optional config via `@Value`: `@Value("${modulo.email.max-tentativas:3}") private int maxTentativas`

**Return Values:**
- Service methods return domain models or DTOs: `EmailResponse`, `Page<EmailResponse>`, `Map<String, Long>`
- Controllers wrap responses in `ResponseEntity`: `ResponseEntity.status(HttpStatus.CREATED).body(response)`, `ResponseEntity.ok(...)`
- `Page<T>` from Spring Data for paginated results
- Maps for statistics: `Map<String, Long> stats` in `estatisticas()`

## Module Design

**Exports:**
- Each module (api-email, api-storage, etc.) is standalone Spring Boot application
- Shared code in `lib-shared` exported as library JAR (not executable)
- Internal packages: `br.com.erpkit.{modulename}.{layer}` — e.g., `br.com.erpkit.email.service`, `br.com.erpkit.email.controller`, `br.com.erpkit.email.repository`
- Public API via REST controllers only; services not exposed outside module

**Barrel Files:**
- No explicit barrel/index files
- Each component (DTO, Service, Repository, Entity) is independent file

## Language & Identifiers

**Portuguese convention:**
- All identifiers, comments, and user-facing messages in Portuguese
- Example identifiers: `destinatario`, `remetente`, `assunto`, `corpo`, `conta`, `arquivo`, `criado_em`, `atualizado_em`
- Example messages: "Email não encontrado", "Email já foi enviado", "Destinatário é obrigatório"
- This is a business domain convention for Brazilian ERP context

## Data Transfer & Validation

**DTO Pattern:**
- Input DTO: `EmailCreateDTO` with Jakarta Validation annotations (`@NotBlank`, `@Email`)
- Response DTO: `EmailResponse` with same fields as domain model for JSON serialization
- Mapping: Manual via `toResponse(Email entity)` method in service layer (see `EmailService.java:219`)
- No MapStruct or ModelMapper — explicit mapping is preferred for clarity

**Validation Annotations:**
- `@NotBlank(message="...")` for required text fields
- `@Email(message="...")` for email format validation
- `@Valid` on controller parameters to trigger validation
- Custom validators: `DocumentoValidator` for CEP/CNPJ normalization and validation

## Transaction Management

**Pattern:**
- Service methods are implicitly transactional via Spring Data repositories
- No explicit `@Transactional` annotations observed (relies on default Spring Boot behavior)
- Each repository save/delete is atomic

---

*Convention analysis: 2026-05-05*
