# Testing Patterns

**Analysis Date:** 2026-05-05

## Test Framework

**Runner:**
- JUnit 5 (Jupiter) via Spring Boot Starter Test
- Test discovery: `*Test.java` pattern
- Execution: Maven Surefire plugin (default from `spring-boot-starter-test`)

**Assertion Library:**
- JUnit Jupiter Assertions: `org.junit.jupiter.api.Assertions.*`
  - `assertEquals()`, `assertTrue()`, `assertFalse()`, `assertNotNull()`, `assertThrows()`
- AssertJ: `org.assertj.core.api.Assertions.assertThat()` (seen in `DocumentoValidatorTest.java`)
- Mockito assertions: `org.mockito.Mockito.when()`, `ArgumentMatchers.any()`

**Run Commands:**
```bash
mvn test                              # Run all tests across modules
mvn test -Dtest=EmailServiceTest      # Run specific test class
mvn test -DfailIfNoTests=false        # Run tests, don't fail if none found
mvn clean verify                      # Full build including integration tests
```

**Coverage:**
- No JaCoCo or coverage plugin configured in pom.xml
- No coverage reports generated automatically

## Test File Organization

**Location:**
- Co-located pattern: tests in `src/test/java` mirroring `src/main/java` structure
- Example: `api-email/src/main/java/br/com/erpkit/email/service/EmailService.java` → `api-email/src/test/java/br/com/erpkit/email/service/EmailServiceTest.java`
- Test resources in `src/test/resources/`

**Naming:**
- Class naming: `{SourceClass}Test.java` — e.g., `EmailServiceTest`, `EmailControllerTest`, `GlobalExceptionHandlerTest`, `DocumentoValidatorTest`
- Test method naming: descriptive Portuguese with `deve` + action pattern
  - Examples: `deveCriarEmailComStatusPendente()`, `deveRetornar201AoCriarEmail()`, `deveLancarExcecaoAoBuscarEmailInexistente()`
  - Prefix `deve` (should) makes intent clear in test reports

**Structure:**
```
api-email/src/test/java/
├── br/com/erpkit/email/
│   ├── config/
│   │   └── PresetSmtpTest.java
│   ├── controller/
│   │   ├── EmailControllerTest.java
│   │   ├── ContaEmailControllerTest.java
│   │   └── ModuloControllerTest.java
│   └── service/
│       ├── EmailServiceTest.java
│       └── ContaEmailServiceTest.java

lib-shared/src/test/java/
├── br/com/erpkit/shared/
│   ├── exception/
│   │   └── GlobalExceptionHandlerTest.java
│   └── security/
│       └── ApiKeyFilterTest.java
```

## Test Structure

**Suite Organization:**
```java
@SpringBootTest
@ActiveProfiles("test")
class EmailServiceTest {

    @Autowired
    private EmailService emailService;

    @Autowired
    private EmailRepository emailRepository;

    @BeforeEach
    void setUp() {
        emailRepository.deleteAll();  // Clean database before each test
        // Create test fixtures (e.g., contaPadrao)
    }

    @Test
    @DisplayName("Deve criar email e enfileirar com status pendente")
    void deveCriarEmailComStatusPendente() {
        // Given
        EmailCreateDTO dto = criarEmailDto();

        // When
        EmailResponse response = emailService.criar(dto);

        // Then
        assertNotNull(response.getId(), "ID do email nao deve ser nulo");
        assertEquals("pendente", response.getStatus(), "Status deve ser pendente");
    }
    
    private EmailCreateDTO criarEmailDto() {
        // Helper method to create test data
        EmailCreateDTO dto = new EmailCreateDTO();
        dto.setDestinatario("dest@example.com");
        dto.setAssunto("Assunto teste");
        dto.setCorpo("Corpo do email de teste");
        return dto;
    }
}
```

**Patterns:**
- Setup: `@BeforeEach void setUp()` clears database/state
- Teardown: `@AfterEach void tearDown()` (seen in `StorageServiceTest.java:50`) cleans up resources and temp files
- Test method structure: Given-When-Then pattern via comments
- Helper methods: Private factory methods like `criarEmailDto()` create test fixtures

## Mocking

**Framework:**
- Mockito 4.x (via `spring-boot-starter-test`)
- Spring Test's `@MockitoBean` for component testing: `@org.springframework.test.context.bean.override.mockito.MockitoBean`

**Patterns:**
```java
@WebMvcTest(EmailController.class)
class EmailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailService emailService;

    @Test
    void deveRetornar201AoCriarEmail() throws Exception {
        EmailResponse response = criarEmailResponse(1L, "pendente");
        
        // Mock the service
        when(emailService.criar(any())).thenReturn(response);

        // Use MockMvc to test controller
        mockMvc.perform(post("/api/emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "test-key-123")
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    private EmailResponse criarEmailResponse(Long id, String status) {
        EmailResponse response = new EmailResponse();
        response.setId(id);
        response.setStatus(status);
        return response;
    }
}
```

**What to Mock:**
- External services in unit tests (e.g., `EmailService` when testing `EmailController`)
- HTTP calls, file systems, external APIs
- Mock via `@MockitoBean` in Spring context tests

**What NOT to Mock:**
- Repositories in integration tests (use real H2 database)
- Spring framework components (use `@SpringBootTest` with real context)
- Domain models and DTOs
- See `EmailServiceTest.java:24` — uses `@SpringBootTest` to test service with real repository

## Fixtures and Factories

**Test Data:**
```java
private EmailCreateDTO criarEmailDto() {
    EmailCreateDTO dto = new EmailCreateDTO();
    dto.setDestinatario("dest@example.com");
    dto.setAssunto("Assunto teste");
    dto.setCorpo("Corpo do email de teste");
    return dto;
}
```

**Location:**
- Private helper methods in test class itself (no separate factory files)
- Simple data builders for common test objects
- Example: `StorageServiceTest.criarPngMinimo()` (`StorageServiceTest.java:201`) — creates minimal valid PNG bytes for image tests

## Coverage

**Requirements:**
- No coverage requirements enforced
- No JaCoCo or similar tools configured in pom.xml

**Test Coverage Status:**
- Recent commit (542c156) added tests for lib-shared, api-email, api-storage
- 12 test classes across all modules (as of analysis date)
- Notable coverage:
  - Services: full CRUD + edge cases (e.g., duplicate states, validation failures)
  - Controllers: HTTP status codes + request validation + error handling
  - Exceptions: custom exception handling and global error handler
  - Validation: CEP/CNPJ validators with edge cases

## Test Types

**Unit Tests:**
- Scope: Single class in isolation
- Example: `DocumentoValidatorTest.java` — tests static utility methods
  - `normalizaCepRemovendoMascara()`, `cepValido()`, `cnpjValido()`
  - No mocks; pure logic testing
- Example: `GlobalExceptionHandlerTest.java` — tests exception translation to HTTP responses
  - Creates exceptions directly, calls handler methods, asserts response structure
  - No Spring context; unit test of handler logic

**Integration Tests:**
- Scope: Service + Repository + Database
- Annotation: `@SpringBootTest` + `@ActiveProfiles("test")`
- Database: H2 in-memory (see `application-test.yml`)
- Example: `EmailServiceTest.java` — tests full flow from DTO → Service → Repository → Database
  - Tests state mutations: status transitions from "pendente" → "enviado" → "falha"
  - Uses real `EmailRepository`, `ContaEmailService`, `TemplateEngine`
  - Example: `deveCriarEmailComStatusPendente()` creates email, asserts persisted state

**Controller Tests:**
- Annotation: `@WebMvcTest({Controller}.class)` for Spring MVC slice testing
- Uses `MockMvc` to simulate HTTP requests without starting full server
- Mocks downstream services
- Example: `EmailControllerTest.java:38` — tests POST endpoint returns 201
  - Mocks `EmailService.criar()` to return test response
  - Asserts HTTP status, JSON response structure, field values

**E2E Tests:**
- Not used in this codebase
- No Selenium or REST Assured integration tests

## Common Patterns

**Async Testing:**
```java
// Not explicitly used in codebase
// @Scheduled methods tested via integration tests with real timing or mocked schedulers
```

**Error Testing:**
```java
@Test
@DisplayName("Deve lancar excecao ao buscar email inexistente")
void deveLancarExcecaoAoBuscarEmailInexistente() {
    ModuloException ex = assertThrows(ModuloException.class,
            () -> emailService.buscar(999L),
            "Deve lancar ModuloException para email inexistente");
    assertTrue(ex.getMessage().contains("encontrado"), "Mensagem deve indicar nao encontrado");
}
```
- Use `assertThrows()` to assert exception type
- Verify exception message content with `assertTrue()` + `contains()`
- Test both: exception is thrown AND message is correct

**Validation Testing:**
```java
@Test
@DisplayName("Deve tratar MethodArgumentNotValidException com erros de campo")
void deveTratarValidationException() throws NoSuchMethodException {
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "dto");
    bindingResult.addError(new FieldError("dto", "nome", "Nome e obrigatorio"));
    
    MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);
    ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

    assertEquals(400, response.getStatusCode().value(), "Status deve ser 400");
    assertNotNull(response.getBody().getCampos(), "Campos nao devem ser nulos");
    assertEquals("Nome e obrigatorio", response.getBody().getCampos().get("nome"));
}
```

**HTTP Testing with MockMvc:**
```java
@Test
@DisplayName("POST /api/emails deve retornar 201 ao criar email")
void deveRetornar201AoCriarEmail() throws Exception {
    when(emailService.criar(any())).thenReturn(response);

    String json = """
            {
                "destinatario": "dest@example.com",
                "assunto": "Teste",
                "corpo": "Corpo do email"
            }
            """;

    mockMvc.perform(post("/api/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-API-Key", "test-key-123")
                    .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1));
}
```

**Test Configuration:**
- `application-test.yml` in each module's `src/test/resources/`
- H2 in-memory database configuration
- Flyway disabled for tests (`enabled: false`)
- Mock SMTP host/port (localhost:25)
- Test-specific properties: `modulo.api-key: test-key-123`, `modulo.email.max-tentativas: 3`

## Test Execution & Isolation

**Database Isolation:**
- `@BeforeEach` calls `deleteAll()` to clear database between tests
- Each test is isolated; no state leakage between tests
- Example: `EmailServiceTest.setUp()` deletes all emails and accounts before each test

**File System Isolation:**
- `@AfterEach` cleanup in `StorageServiceTest.tearDown()` removes temp uploaded files
- Tests use configurable `modulo.storage.diretorio` pointing to test directory
- Prevents test artifacts polluting production upload directory

**API Key Testing:**
- Tests use header `X-API-Key: test-key-123` (from `application-test.yml`)
- Public endpoints (`/health`, `/api/info`, `/swagger-ui`) tested without key
- Protected endpoints tested with valid and invalid keys (see `ApiKeyFilterTest.java`)

---

*Testing analysis: 2026-05-05*
