# Phase 1: Fundacao HMAC + Webhook - Context

**Gathered:** 2026-05-05
**Status:** Ready for planning
**Mode:** `--auto` (user delegated all gray-area decisions to Claude after seeing the menu)

<domain>
## Phase Boundary

O modulo `api-whatsapp` arranca como Spring Boot service standalone (porta 9193, scan `br.com.erpkit`), valida no boot que todas as 5 propriedades sensiveis estao presentes, expoe `GET /webhook/whatsapp` que ecoa `hub.challenge` em plain text, e expoe `POST /webhook/whatsapp` que valida HMAC-SHA256 do header `X-Hub-Signature-256` contra os bytes brutos do body e retorna 200 (sucesso) ou 401 (assinatura invalida).

Nao ha persistencia de mensagens, nao ha parser de payload Meta, nao ha callback para o ERP, nao ha envio outbound. Phase 1 entrega exclusivamente a **fundacao de seguranca** + **infra base do modulo** + **migrations Flyway V1-V4 aplicadas**.

**Em escopo:**
- Estrutura Maven nova `api-whatsapp/` registrada no `pom.xml` raiz
- `WhatsAppApplication.java` com `@SpringBootApplication(scanBasePackages = "br.com.erpkit")`
- `WhatsAppProperties` (`@ConfigurationProperties("app.modulos.whatsapp")` + `@Validated` + `@NotBlank`) com 5 campos obrigatorios + `callbackTimeout` opcional
- `application.yml` com placeholders `${WHATSAPP_*}` (nada hardcoded)
- `CachedBodyHttpServletRequest` (HttpServletRequestWrapper customizado, lê body eager no construtor)
- `HmacValidator` (service unit-testavel, computa HMAC-SHA256 sobre `byte[]`, compara com `MessageDigest.isEqual`)
- `HmacSignatureFilter` (`OncePerRequestFilter` com `@Order(Ordered.HIGHEST_PRECEDENCE)`, aplicado apenas em `/webhook/*`, embrulha request em `CachedBodyHttpServletRequest`, delega ao `HmacValidator`)
- `WebhookController` com 2 endpoints: `GET` ecoa challenge em `text/plain` apos comparar `verifyToken` com `MessageDigest.isEqual`; `POST` retorna 200 imediatamente (HMAC ja validado pelo Filter, parsing/persistencia ficam para Phase 2)
- `SecurityConfig` registra `HmacSignatureFilter` (ordem 0, so `/webhook/*`) + `ApiKeyFilter` (ordem 1, todos os paths exceto `/webhook/*` que entra no `additionalPublicPaths`)
- Modificacao em `lib-shared/ApiKeyFilter.java`: aceitar `Set<String> additionalPublicPaths` no construtor (default vazio)
- Migrations Flyway `V1__criar_tabela_clientes_zap.sql`, `V2__criar_tabela_mensagens_log.sql`, `V3__criar_tabela_media_cache.sql`, `V4__criar_tabela_estado_conversa.sql` no schema `whatsapp`
- `application-test.yml` com H2 em modo PostgreSQL compat para `mvnw verify` verde

**Fora de escopo (Phase 2-6):**
- Parser de `WebhookPayloadDTO` / extracao de `wamid`, `telefone`, `tipo`
- Entities JPA (`ClienteZap`, `MensagemLog`, `MediaCache`) — criadas em Phase 2
- `IdempotencyService`, `ClienteZapService`, `MensagemService` async
- `ErpCallbackClient`, `MessageRouter`
- `WhatsAppCloudClient`, `MediaCacheService`, `WindowEnforcementService`
- `WhatsAppController` (endpoints internos `enviar-*`)
- `lib-whatsapp-client/`
- README/RUNBOOK/SpringDoc OpenAPI exhaustivo (apenas o minimo pra build verde)

</domain>

<decisions>
## Implementation Decisions

### D-01: HMAC validation via Servlet Filter at HIGHEST_PRECEDENCE (delegating to HmacValidator service)

**Decisao:** Filter pattern, nao service-no-controller. Estrutura em duas camadas:

- `HmacSignatureFilter extends OncePerRequestFilter` — `@Order(Ordered.HIGHEST_PRECEDENCE)`, aplicado via `FilterRegistrationBean.addUrlPatterns("/webhook/*")`. Embrulha `HttpServletRequest` em `CachedBodyHttpServletRequest` no `doFilterInternal`, le `cachedBody` e header `X-Hub-Signature-256`, delega ao `HmacValidator`. Se invalido: `response.setStatus(401)` + escreve `ErrorResponse` JSON + return (sem `chain.doFilter`).
- `HmacValidator` (`@Service`, sem dependencia de servlet) — metodo `boolean isValid(byte[] rawBody, String signatureHeader, String appSecret)`. Pure function unit-testavel. Implementa: `Mac.getInstance("HmacSHA256")`, `init` com `SecretKeySpec(appSecret.getBytes(UTF_8), "HmacSHA256")`, computa `byte[] expected = mac.doFinal(rawBody)`, decodifica `signatureHeader` (strip `"sha256="`, hex decode), compara com `MessageDigest.isEqual(expected, received)`. Retorna `false` se header ausente, formato invalido, ou comparacao falha. Nunca lanca excecao por causa de input malformado — sempre retorna `false`.

**Por que Filter e nao service-no-controller:** PITFALLS C-02 documenta explicitamente que o filter deve estar em `HIGHEST_PRECEDENCE` para garantir que nenhum outro filtro ou processamento Spring MVC toque no body antes da validacao. Service-no-controller (sugerido por ARCHITECTURE.md) tambem e seguro tecnicamente com `CachedBodyHttpServletRequest`, mas coloca o ponto de defesa apos o Spring MVC ter feito request mapping, content negotiation, etc. Filter e o gate mais cedo possivel — payload hostil nunca chega no MVC pipeline. ARCHITECTURE.md sera atualizado pelo planner para refletir esta decisao (o service `HmacValidator` continua existindo, so muda quem o invoca).

**Conflito reconhecido:** ARCHITECTURE.md mostra `hmacValidator.validarOuRejeitar(signature, corpo)` no Controller. Esta CONTEXT.md substitui aquela orientacao para a Phase 1. Planner deve seguir CONTEXT.md.

### D-02: Modificar lib-shared/ApiKeyFilter para aceitar `additionalPublicPaths` configuraveis

**Decisao:** Mudanca minima e cirurgica em `lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java`:

```java
private static final Set<String> DEFAULT_PUBLIC_PATHS =
    Set.of("/health", "/api/info", "/swagger-ui", "/v3/api-docs");

private final String apiKey;
private final Set<String> publicPaths;

public ApiKeyFilter(String apiKey) {
    this(apiKey, Set.of());
}

public ApiKeyFilter(String apiKey, Set<String> additionalPublicPaths) {
    this.apiKey = apiKey;
    this.publicPaths = new HashSet<>(DEFAULT_PUBLIC_PATHS);
    this.publicPaths.addAll(additionalPublicPaths);
}
```

`api-whatsapp/SecurityConfig` instancia `new ApiKeyFilter(apiKey, Set.of("/webhook"))` — webhook fica publico (validado por HMAC, nao API key).

**Por que modificar lib-shared e nao criar filter local:** ApiKeyFilter e o ponto canonico de policy de paths publicos do monorepo. Hardcodar em cada modulo o que e publico vs privado se afasta do padrao. A mudanca e backward-compatible (construtor de 1 arg preservado), zero impacto em api-email/api-storage/api-consultas. Outros modulos futuros (webhooks de payment provider, OAuth callbacks) reusam o mesmo mecanismo.

**Risco:** Toca codigo compartilhado. Mitigado por ter teste em `lib-shared/src/test/java/` cobrindo o construtor antigo + novo (planner adiciona em Phase 1).

### D-03: Fail-fast via `@Validated` + Jakarta Bean Validation com mensagens em portugues

**Decisao:** `WhatsAppProperties` usa Spring Boot's standard pattern:

```java
@ConfigurationProperties("app.modulos.whatsapp")
@Validated
public class WhatsAppProperties {
    @NotBlank(message = "WHATSAPP_PHONE_NUMBER_ID nao definida")
    private String phoneNumberId;

    @NotBlank(message = "WHATSAPP_ACCESS_TOKEN nao definida")
    private String accessToken;

    @NotBlank(message = "WHATSAPP_APP_SECRET nao definida")
    private String appSecret;

    @NotBlank(message = "WHATSAPP_VERIFY_TOKEN nao definida")
    private String verifyToken;

    @NotBlank(message = "WHATSAPP_ERP_CALLBACK_URL nao definida")
    private String erpCallbackUrl;

    private Duration callbackTimeout = Duration.ofSeconds(5);

    // toString() override que mascara accessToken/appSecret/verifyToken
    @Override
    public String toString() {
        return "WhatsAppProperties{phoneNumberId=" + phoneNumberId
             + ", accessToken=[REDACTED], appSecret=[REDACTED], verifyToken=[REDACTED]"
             + ", erpCallbackUrl=" + erpCallbackUrl
             + ", callbackTimeout=" + callbackTimeout + "}";
    }
}
```

`@EnableConfigurationProperties(WhatsAppProperties.class)` no `WhatsAppApplication` (ou no `SecurityConfig`). Boot falha com `BindValidationException` listando o campo + mensagem em portugues — operador entende imediatamente qual env var faltou.

**Por que Bean Validation e nao @PostConstruct manual:** Padrao Spring Boot, dependencia `spring-boot-starter-validation` ja esta no monorepo (api-email/api-storage usam pra DTOs). Falha graciosa via mecanismo conhecido. `@PostConstruct` com `IllegalStateException` produz stack trace mais ruidoso e exige codigo manual repetitivo. `ApplicationContextInitializer` e overkill — checa env vars antes do contexto subir, ganho marginal vs Bean Validation.

**Mensagens em portugues:** Alinhado com convencao do monorepo (`CONVENTIONS.md` confirma identificadores e mensagens user-facing em PT-BR). Operador da ERPKit le log de boot e sabe qual env var ajustar no `service-config-whatsapp.xml` do WinSW.

### D-04: POST /webhook stub retorna 200 vazio apos HMAC valido — sem parsing, sem log de body

**Decisao:** Phase 1's POST endpoint e:

```java
@PostMapping(value = "/webhook/whatsapp")
public ResponseEntity<Void> receberWebhook(HttpServletRequest request) {
    // HmacSignatureFilter ja validou. Se chegou aqui, a assinatura e valida.
    // Phase 2 substitui este stub por: parse → idempotency → return 200 → @Async dispatch.
    return ResponseEntity.ok().build();
}
```

Nada de:
- Parser de envelope (`message.text`, `interactive.button_reply`, `statuses.status`) — Phase 2 (WEB-07)
- Log do body — anti-pattern documentado em PITFALLS (vaza phone numbers, message content, PII)
- Log de wamid — exige parser, e Phase 2
- `@Async` boundary — exige `MensagemService.processarAsync()` e MensagemLogRepository, ambos Phase 2/3

**Por que stub minimo e nao "parse minimo agora pra testar parser cedo":** Phase boundaries devem ser tight. Sucesso da Phase 1 ja exige integration test com payload portugues `"Ola, gostaria de um orcamento"` (criterio 3 do ROADMAP) — testa HMAC sobre body real do Meta sem precisar parsear o JSON. Adicionar parsing prematuro cria implementacao meio-pronta que Phase 2 desfaz ou expande, violando "Don't add features beyond what the task requires." (CLAUDE.md global guidance).

**Por que NAO logar o body:** PITFALLS "Security Mistakes" tabela: "Log full webhook body in PROD → Customer phone numbers, message content, PII exposed in log files. Prevention: Log only wamid + message type + timestamp; never log message body." Como Phase 1 nao tem parser, nao temos nem wamid pra logar — entao o log de POST fica em `log.debug("webhook recebido, hmac valido")` sem nenhum dado do body.

### D-05: Logging strategy alinhada com cross-cutting security pitfalls

Decisoes preventivas pulled das PITFALLS para Phase 1 (nao sao gray areas, mas precisam estar explicitas pro planner nao "esquecer"):

- **C-09:** Spring `CommonsRequestLoggingFilter` desligado. Log level `org.springframework.web` = `INFO`, nao `DEBUG`. Outbound HTTP client (chega em Phase 4) tera interceptor que mascara `Authorization: Bearer`. Phase 1 ja deixa o `application.yml` com `logging.level.org.springframework.web: INFO`.
- **C-11:** Query string nao e logada. `application.yml` com `server.tomcat.accesslog.enabled: false` (default ja e false em Spring Boot, mas explicitar elimina ambiguidade).
- **C-09 actuator:** `management.endpoint.env.keys-to-sanitize` adicionado com regex que cobre `accessToken`, `appSecret`, `verifyToken`. Padrao Spring Boot ja sanitiza chaves contendo `password`, `secret`, `key`, `token` — verifyToken e cobertura, accessToken/appSecret tambem por conterem `token`/`secret`. Mesmo assim, listar explicitamente e defesa em profundidade.

### D-06: Migrations V1-V4 escritas em SQL ANSI portavel (PostgreSQL + H2 PostgreSQL-mode)

**Decisao:** Cada `V*.sql` usa apenas sintaxe portavel:
- `BIGSERIAL` (PostgreSQL) → para H2 funcionar, usar `BIGINT GENERATED ALWAYS AS IDENTITY` (SQL standard, suportado por ambos a partir do Postgres 10 / H2 2.x)
- `TIMESTAMP DEFAULT NOW()` → ambos suportam
- Schema `whatsapp` criado pela migration: `CREATE SCHEMA IF NOT EXISTS whatsapp;` na V1, antes do `CREATE TABLE`. Em producao o instalador Inno Setup tambem cria, mas a migration e auto-suficiente para CI/test.
- `application-test.yml` configura H2 com `MODE=PostgreSQL` e `INIT=CREATE SCHEMA IF NOT EXISTS whatsapp;` no JDBC URL — Flyway aplica V1-V4 limpas no boot do test.

Esquema das tabelas vem 1:1 do REQUIREMENTS.md PER-02 a PER-04 + V4 estado_conversa minima (so `ultima_mensagem_em` por enquanto, conforme D6 do PROJECT.md).

### Claude's Discretion

User delegou todas as 4 areas para mim apos ver o menu. Decisoes acima sao defaults recomendados — todas reversiveis em phases futuras se a implementacao mostrar atrito (Filter vs service no controller especialmente — Phase 3 quando `@Async` boundary entrar pode revisitar).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Pitfalls de seguranca (criticos para Phase 1)
- `.planning/research/PITFALLS.md` §C-02 — HMAC body consumed before filter (define que filter eager-read e obrigatorio)
- `.planning/research/PITFALLS.md` §C-03 — HMAC timing attack (define `MessageDigest.isEqual`)
- `.planning/research/PITFALLS.md` §C-04 — Unicode charset (define raw bytes never via String)
- `.planning/research/PITFALLS.md` §C-09 — Bearer token in logs (escopo Phase 4 mas ja influencia application.yml de Phase 1)
- `.planning/research/PITFALLS.md` §C-10 — hub.challenge plain text (define `produces=TEXT_PLAIN_VALUE`)
- `.planning/research/PITFALLS.md` §C-11 — verifyToken in query logs (define `accesslog.enabled: false`)

### Arquitetura e contratos
- `.planning/research/ARCHITECTURE.md` — visao geral; **NOTA:** sessao "Component Responsibilities" mostra `HmacValidator` como service-no-controller; CONTEXT.md D-01 sobrescreve com Filter+service. Planner deve seguir CONTEXT.md.
- `.planning/research/ARCHITECTURE.md` §"Recommended Project Structure" — layout completo de `api-whatsapp/src/main/java/`
- `.planning/PROJECT.md` §"Active" — requirements WHATS-01 a WHATS-04 + CFG-01 a CFG-04 mapeados para Phase 1
- `.planning/REQUIREMENTS.md` §"Webhook" §"Persistencia" §"Configuracao" — WEB-01..04, PER-01, CFG-01..04 (locked para Phase 1)
- `.planning/ROADMAP.md` §"Phase 1" — 5 success criteria literais (replicados em VERIFICATION na Phase 6)

### Padroes do codebase a espelhar
- `lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java` — padrao `OncePerRequestFilter`; **vai ser modificado** para aceitar `additionalPublicPaths` (D-02)
- `api-consultas/src/main/java/br/com/erpkit/consultas/config/SecurityConfig.java` — padrao `FilterRegistrationBean` com `addUrlPatterns` e `setOrder`
- `api-consultas/src/main/java/br/com/erpkit/consultas/ConsultasApplication.java` — padrao `@SpringBootApplication(scanBasePackages = "br.com.erpkit")`
- `api-consultas/src/main/resources/application.yml` — estrutura de `server.port` + `spring.application.name` + `springdoc` + `logging.level` a espelhar
- `api-email/src/main/resources/application.yml` — padrao `spring.datasource` + `spring.jpa` + `spring.flyway` para PostgreSQL
- `api-email/src/main/resources/db/migration/V1__criar_tabela_emails.sql` — padrao de migration Flyway (DDL + indices)
- `lib-shared/src/main/java/br/com/erpkit/shared/exception/GlobalExceptionHandler.java` — padrao para tratamento de validation exceptions

### Convencoes
- `.planning/codebase/CONVENTIONS.md` — identificadores em PT-BR, ausencia de `@Transactional` explicito, packages por camada
- `.planning/codebase/STRUCTURE.md` — onde adicionar codigo novo, naming patterns
- `CLAUDE.md` (raiz) §"Project" §"Constraints" — custo zero, on-premise, alinhamento com api-consultas

### Documento de origem
- `PLANO-WHATSAPP.md` — fonte arquitetural; D1-D3 (hospedagem on-premise, piloto MUDAS, modelo reativo) influenciam decisoes downstream

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `lib-shared/ApiKeyFilter.java` — sera estendido (nao reescrito) com `additionalPublicPaths`. Construtor de 1 arg preservado para backward-compat com api-email/api-storage/api-consultas que ja consomem.
- `lib-shared/exception/GlobalExceptionHandler.java` — captura `MethodArgumentNotValidException` → 400 e `Exception` → 500. Cobre validacao de futuros DTOs (Phase 2+) automaticamente. Phase 1 nao precisa de novos handlers.
- `lib-shared/exception/ModuloException.java` — base para `JanelaFechadaException` (Phase 4) e qualquer excecao custom. Phase 1 nao usa.
- `lib-shared/dto/ErrorResponse.java` — JSON de resposta de erro padronizado. `HmacSignatureFilter` o usa para escrever 401.
- Spring Boot validation starter — ja transitiva via `lib-shared` ou api-email's pom; api-whatsapp/pom.xml so precisa declarar `lib-shared`.

### Established Patterns
- **Module bootstrap:** `@SpringBootApplication(scanBasePackages = "br.com.erpkit")` — necessario porque `lib-shared` esta em outro pacote root mas mesma raiz.
- **Filter registration:** `FilterRegistrationBean` em `@Configuration` SecurityConfig — `setOrder(N)` define ordem, `addUrlPatterns("/...")` restringe scope.
- **YAML config:** `${ENV_VAR:default}` para env vars com fallback; sem fallback = obrigatorio (boot falha se ausente E se Bean Validation tambem rejeita).
- **Flyway:** `spring.flyway.enabled: true` + `baseline-on-migrate: true` no application.yml; migrations em `src/main/resources/db/migration/V{n}__{snake_case}.sql`.
- **Exception → HTTP:** `ModuloException(msg, HttpStatus)` lancada pelo service vira HTTP correspondente via `GlobalExceptionHandler`.
- **DDL mode:** `spring.jpa.hibernate.ddl-auto: validate` — Hibernate so verifica que entities batem com schema; nunca cria/altera. Esquema vem 100% das migrations Flyway.

### Integration Points
- **Root pom.xml** (`pom.xml`): adicionar `<module>api-whatsapp</module>` na lista `<modules>`. lib-whatsapp-client entra no pom em Phase 5.
- **Cross-module:** Modificacao em `lib-shared/ApiKeyFilter.java` requer rebuild de api-email/api-storage/api-consultas (nenhum codigo deles muda — so re-resolve a transitive). `mvnw verify` no reator captura.
- **DB local:** Schema `whatsapp` criado pela migration V1 (`CREATE SCHEMA IF NOT EXISTS whatsapp`). Em producao via instalador Inno Setup; em test via JDBC URL com `INIT=CREATE SCHEMA IF NOT EXISTS whatsapp`.
- **Env vars no instalador:** `service-config-whatsapp.xml` (WinSW) recebe as 5 env vars. Phase 1 nao toca instalador (Out of Scope cross-repo) mas o `application.yml` documenta os placeholders pra ERPKit popular.

</code_context>

<specifics>
## Specific Ideas

- **Mensagens de erro do Bean Validation em portugues** — operador da ERPKit le log de boot e identifica imediatamente qual env var faltou (alinhamento com convencao PT-BR do monorepo).
- **`MessageDigest.isEqual` aplicado tambem ao `verifyToken`** no GET handshake — risco menor que HMAC, mas consistencia importa e custo e zero.
- **Filter so em `/webhook/*`** (nao `/*` global) — endpoints internos do ERP (Phase 4) nao precisam HMAC, so API key.
- **D-06 portabilidade SQL:** preferir `BIGINT GENERATED ALWAYS AS IDENTITY` sobre `BIGSERIAL` para H2 + Postgres ambos rodarem as mesmas migrations. Caso a portabilidade quebre, fallback aceitavel e usar Testcontainers em Phase 6 (mas e pesado pra Phase 1).
- **Stub de POST sem log do body** — eliminacao de surface de PII de cara, antes mesmo de Phase 2 ter parser.

</specifics>

<deferred>
## Deferred Ideas

- **Parser de WebhookPayloadDTO** (`message.text`, `interactive.button_reply`, `interactive.list_reply`, `message.document`, `statuses.status`) — Phase 2 (WEB-07).
- **Idempotency por wamid** — Phase 2 (WEB-05/WEB-06).
- **Async dispatch (`@Async`, `@TransactionalEventListener(AFTER_COMMIT)`)** — Phase 3 (ROU-01 a ROU-05).
- **Health check que valida WABA subscription via Graph API** (PITFALLS C-12) — Phase 4 ou Phase 6 (`GET /api/whatsapp/status`, WHATS-17).
- **Mascarar `Authorization: Bearer` em logs do RestClient** (PITFALLS C-09) — Phase 4 quando `WhatsAppCloudClient` for criado.
- **Testes de carga / verificar P95 < 1s do POST sob delay do ERP** (PITFALLS C-05) — Phase 6 com WireMock.
- **`accesslog.enabled` reforce em CI** — opcional Phase 6.
- **Testcontainers para PostgreSQL real em CI** — adiavel se H2 PostgreSQL-mode rodar bem; revisitar Phase 6 se descobrir gap de portabilidade.

</deferred>

---

*Phase: 1-Fundacao HMAC + Webhook*
*Context gathered: 2026-05-05*
