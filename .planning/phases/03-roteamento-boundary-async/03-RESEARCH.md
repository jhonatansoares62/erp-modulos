# Phase 3: Roteamento + Boundary Async - Research

**Researched:** 2026-05-05
**Domain:** Spring Boot 3.5.9 ack-first async + Resilience4j + WhatsApp Cloud API media download
**Confidence:** HIGH (Phase 1+2 entregues + 183 tests verdes; padrao Resilience4j ja existe em lib-consultas-client; documentacao Meta v22 + Spring 6 verificadas)

## Summary

Phase 3 refatora `MensagemService.processarWebhook` da Phase 2 (sincrono) em pattern **ack-first / process-later** usando `ApplicationEventPublisher` + `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async("whatsappTaskExecutor")`. O caminho sincrono fica reduzido a parse + idempotency + persistencia + return 200; o resto (media download → cliente identification → atualizar timestamp → comando extraction → callback ERP) roda em thread pool dedicado, fora da request HTTP do Meta.

A estrategia tem 3 pilares verificados:

1. **`@TransactionalEventListener(AFTER_COMMIT)`** garante que o event so dispare apos commit do INSERT em `mensagens_log`. Se commit falhar, listener nunca roda → ERP nao recebe falso positivo. Spring honra essa semantica via `TransactionSynchronization` callback. **Importante: sem transacao ativa no publisher, o listener e silenciosamente descartado** — exige que o caller (`MensagemService.processarWebhook`) tenha transacao Spring Data JPA, garantida implicitamente por `repository.save()` em `IdempotencyService` e `ClienteZapService`.

2. **Resilience4j 2.2.0 com starter `resilience4j-spring-boot3`** usando annotations `@CircuitBreaker(name="erp-callback")` + `@Retry(name="erp-callback")` espelhando `lib-consultas-client/ConsultasClientImpl.java`. Diferenca: la o pattern e **programatico** (decorateSupplier); aqui sera **declarativo** (annotations + `spring-boot-starter-aop`) — ambos suportados, annotations mais limpas para casos sem composicao de varios decoradores. Exige `spring-boot-starter-aop` adicionado ao classpath.

3. **Media download como PRIMEIRA acao do listener** — URL Meta expira em 5min (PITFALLS C-08); sob carga, queue async pode atrasar. Sequenciar media → cliente → callback minimiza window de expiry. 2-step Graph API: GET `/v22.0/{media_id}` → GET URL temporaria. Bearer header em ambas chamadas, **nunca** query param (PITFALLS C-14).

**Primary recommendation:** Implementar em 6 waves sequenciais (1→2→3→4→5→6), cada wave commitavel independente. Test profile sobrescreve `whatsappTaskExecutor` com `SyncTaskExecutor` — 13 tests E2E da Phase 2 mantem assertions sincronas sem `Awaitility` flake.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| HTTP boundary inbound (webhook) | API/Backend (api-whatsapp:9193) | — | Spring MVC controller; apenas Meta consome |
| HMAC validation | API/Backend (filter chain) | — | Filter `HIGHEST_PRECEDENCE` ja em Phase 1 |
| Persistencia idempotente | Database (PostgreSQL schema whatsapp) | — | UNIQUE wamid e o gate atomico — Phase 2 entregue |
| Async task scheduling | API/Backend (ThreadPoolTaskExecutor) | — | Pool dedicado Spring; nao usa MQ/broker (D1 on-premise) |
| Media download (ingress) | API/Backend (MetaMediaClient) | External (graph.facebook.com) | Sincrono dentro do listener async — pre-callback |
| ERP callback (egress) | API/Backend (ErpCallbackClient) | External (localhost:8090 ERP) | Resilience4j circuit breaker; sem retry na fallback |
| Comando extraction | API/Backend (ComandoExtractor) | — | Logica pura sem I/O; reuso por Phase 5 lib |
| Test isolation | API/Backend (test profile) | — | SyncTaskExecutor override em `application-test.yml` ou `@TestConfiguration` |

**Por que tudo no api-whatsapp:** Phase 3 nao toca lib-shared, lib-consultas-client, nem ERP-MUDAS. Boundary cross-process e somente HTTP egress (ERP callback) + HTTP egress (Meta media). lib-whatsapp-client (Phase 5) sera consumidor do callback, nao Phase 3.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01: Pattern ack-first via `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`**
- `MensagemService.processarWebhook(byte[])` reescrito para fast-path sincrono apenas: parse → idempotency → persist → publishEvent → return.
- `MensagemAsyncListener.aoMensagemPersistida(...)` anotado `@Async("whatsappTaskExecutor") @TransactionalEventListener(phase = AFTER_COMMIT)` ouvindo `MensagemPersistidaEvent`. Sequencia: media download → identificar → atualizar timestamp → comando extraction → ErpCallbackClient. Captura excecao em cada step + log estruturado, nao propaga.
- Trade-off ack-first: falhas pos-ack sao perdidas silenciosamente (so log). Mitigacao: log.error estruturado com wamid+comando para correlacao operacional. Phase 6 pode adicionar metric counters Micrometer.

**D-02: Configuracao `@EnableAsync` + ThreadPoolTaskExecutor dedicado**
- `WhatsAppApplication.java` ganha `@EnableAsync`.
- Novo `config/AsyncConfig.java` com `@Bean(name = "whatsappTaskExecutor")` — ThreadPoolTaskExecutor: corePool=2, maxPool=10, queueCapacity=100, threadNamePrefix=`whatsapp-async-`, RejectedExecutionHandler=`CallerRunsPolicy`.
- Listener anota `@Async("whatsappTaskExecutor")` para garantir uso do pool dedicado (nao default).

**D-03: Resilience4j circuit breaker + retry no `ErpCallbackClient`**
- `pom.xml` da api-whatsapp: adicionar `io.github.resilience4j:resilience4j-spring-boot3` (versao gerenciada pelo parent ja em 2.2.0).
- `application.yml` com bloco `resilience4j.circuitbreaker.instances.erp-callback` (slidingWindowSize=10, failureRateThreshold=50, waitDurationInOpenState=60s, permittedNumberOfCallsInHalfOpenState=3) + `resilience4j.retry.instances.erp-callback` (maxAttempts=3, waitDuration=1s, enableExponentialBackoff=true, exponentialBackoffMultiplier=2.0, retryExceptions=HttpServerErrorException/SocketTimeoutException/IOException).
- `ErpCallbackClient` anotado `@CircuitBreaker(name="erp-callback", fallbackMethod="fallbackDespachar")` + `@Retry(name="erp-callback")`.
- Retentativa apenas para 5xx/timeout/IOException — NAO 4xx categoricos.

**D-04: Media download como PRIMEIRA acao do listener (5 min URL expiry)**
- `MetaMediaClient` faz 2-step download: GET `graph.facebook.com/v22.0/{media_id}` → GET URL temporaria → bytes.
- Bearer accessToken em header em ambas as chamadas. **NUNCA** query param.
- 404 graceful via `Optional.empty()` + log.warn.
- Sem Resilience4j — primeira acao precisa ser rapida, queue delay e o risco principal nao indisponibilidade.

**D-05: Comando extraction simples baseado em tipo**
- `ComandoExtractor.extrair(String tipo, String conteudo)`:
  - `text` → primeira palavra do conteudo (lowercase, locale ROOT)
  - `interactive_button`/`interactive_list` → id (parser Phase 2 formata "id|title")
  - `document`/`image`/`audio`/`video` → o proprio tipo
  - default → null (skip dispatch)

**D-06: ComandoCallbackDTO com mediaBase64 (nao filesystem)**
- `record ComandoCallbackDTO(String telefone, String comando, String payload, Long idCliente, String mediaBase64, String mediaMimeType, String mediaFilename)`. mediaBase64 nullable.
- Por que base64 e nao filesystem: filesystem path expoe acoplamento entre processos. Base64 e self-contained. Trade-off: PDFs grandes (~10MB) viram ~13MB base64 — aceitavel para callback localhost.

**D-07: Refatorar MensagemService — remover sync de cliente identification + atualizar timestamp**
- Phase 2 fazia tudo sincrono dentro de processarWebhook. Phase 3 muda para fast-path: parse → idempotency → persist → publishEvent.
- Listener async faz: media download → identificar → atualizar timestamp → ERP callback.
- Tests existentes da Phase 2 (`MensagemServiceTest`, `WebhookPersistenciaIntegrationTest`) refatorados:
  - `MensagemServiceTest` muda para verificar `publishEvent` (nao chamadas a clienteZapService).
  - `WebhookPersistenciaIntegrationTest` usa SyncTaskExecutor override em test profile.

**D-08: Sem retry no callback ERP fallback (ROU-03)**
- Quando Resilience4j esgota retries (3 tentativas) e circuit abre OU 4xx categorico chega, fallback method NAO retenta — apenas loga error estruturado. ERP pode ter executado parcialmente; retry pode duplicar side effects.
- Trade-off: mensagem do cliente nao recebe resposta. Cliente pode reenviar mensagem. Phase 6 RUNBOOK documenta como diagnosticar.

### Claude's Discretion

Em CONTEXT.md, todas as decisoes sao locked (D-01..D-08 detalhadas). Areas de discricionariedade do agente sao limitadas ao DETALHE DE IMPLEMENTACAO:

- Estrutura interna de `MensagemAsyncListener` (helpers privados, ordem de log, granularidade de try/catch por step).
- Nomes de helpers privados em `MetaMediaClient` (`buscarUrl`, `baixarBytes`, etc.).
- Test naming convention para os novos integration tests.
- Schema de logs estruturados (que campos por log line).
- Decisao entre `@SpringBootTest` full vs `@WebMvcTest` para tests novos — recomendacao baseada em padrao do codebase Phase 2.

### Deferred Ideas (OUT OF SCOPE)

- **WhatsAppCloudClient outbound** — Phase 4
- **WindowEnforcementService 24h** — Phase 4 (le `ultima_mensagem_em` que Phase 2/3 atualizam)
- **MediaCacheService outbound** (sha256→media_id, TTL 30d) — Phase 4 (Phase 3 baixa media de ENTRADA, nao SAIDA)
- **lib-whatsapp-client SPI/registry** — Phase 5
- **Persistencia de bytes da media de entrada** (filesystem ou blob) — fora desta milestone
- **Dead letter queue para callback ERP falhas definitivas** — Phase 6+ (RUNBOOK pode documentar como recuperar manualmente via logs com wamid)
- **Metric counters Micrometer** (callbacks succeeded/failed/circuit_open) — Phase 6
- **Persistencia de statuses Meta** — backlog opcional

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ROU-01 | Apos persistencia, `MessageRouter` invoca `ErpCallbackClient.entregar(comando)` em `@Async` — nao bloqueia ack 200 | §"Padrao 1: ack-first" + §"Wave 5" cobre o pattern `MensagemAsyncListener` com `@Async @TransactionalEventListener(AFTER_COMMIT)` |
| ROU-02 | `ErpCallbackClient` faz `POST {erpCallbackUrl}/api/modulos/whatsapp/comando` com payload `{telefone, comando, payload, idCliente}` | §"Wave 4" + §"ErpCallbackClient" — RestClient + Bearer header opcional + ComandoCallbackDTO com mediaBase64 |
| ROU-03 | ERP callback usa Resilience4j circuit breaker (10/50%/60s) + retry exponencial (3 tentativas, 1s/2s/4s) | §"Resilience4j Setup" — versao 2.2.0 + starter resilience4j-spring-boot3 + spring-boot-starter-aop + bloco yaml completo |
| ROU-04 | ERP callback timeout default 5s; timeout/erro nao trava webhook (ja respondeu 200), so loga | §"ErpCallbackClient timeout" — RestClient com SimpleClientHttpRequestFactory(callbackTimeout); fallback method log error sem propagar |
| ROU-05 | Download de media entrante e a PRIMEIRA acao async apos ack — URL Meta expira em 5min; bytes guardados em memoria pra entregar pro ERP no callback | §"Wave 3: MetaMediaClient" — 2-step Graph API; Bearer header (nao query param); 404 → Optional.empty + log.warn; bytes → base64 no ComandoCallbackDTO |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **Custo zero de Meta**: garantido por (a) sem API de template, (b) trava hard de janela 24h, (c) reativo puro. Phase 3 nao quebra: callback ERP e localhost (zero $); Meta media GET e gratis. Sem outbound Meta nesta phase.
- **Tech stack**: Spring Boot 3.5.9 + Java 21 + Maven — alinhado.
- **Padrao arquitetural**: `api-<dominio>` + `lib-<dominio>-client` com Resilience4j — espelhar exatamente lib-consultas-client.
- **Persistencia**: PostgreSQL local 5433, schema `whatsapp` isolado, Flyway no boot — Phase 3 nao adiciona migrations.
- **Idempotencia**: `wamid` UNIQUE — Phase 2 ja garante; Phase 3 reusa via `IdempotencyService.tentarPersistir` boolean novo.
- **HMAC**: validacao em `X-Hub-Signature-256` — Phase 1 ja entregue, Phase 3 nao toca.
- **Escopo cross-repo**: Engate em ERP-MUDAS e installer ficam fora.
- **Sem Lombok**: getters/setters explicitos.
- **PT-BR** em identificadores e mensagens user-facing.
- **Commit**: agentes GSD DEVEM commitar via `gsd-tools.cjs commit` apos cada plano executado.

## Standard Stack

### Core (novas dependencias para Phase 3)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| io.github.resilience4j:resilience4j-spring-boot3 | 2.2.0 (managed by parent) | Starter com auto-config + AOP support | Espelha lib-consultas-client; annotations declarativas funcionam; **resilience4j-spring-boot3** e a variante para Spring Boot 3.x (Jakarta EE) |
| org.springframework.boot:spring-boot-starter-aop | 3.5.9 (BOM) | AOP runtime para `@CircuitBreaker`/`@Retry` annotations | Sem esse starter, annotations Resilience4j viram no-op silencioso |
| (parent ja inclui) spring-boot-starter-web RestClient | 6.x via 3.5.9 | HTTP client moderno (RestClient over RestTemplate) | Spring 6+ recomenda RestClient para novo codigo; lib-consultas-client usa RestTemplate por idade, mas Phase 3 e novo |

**Verificacao de versao:** Parent `pom.xml` linha 30-37 ja declara `<resilience4j.version>2.2.0</resilience4j.version>` em `<properties>`. lib-consultas-client/pom.xml ja importa `resilience4j-circuitbreaker` + `resilience4j-retry` sem version (managed). Phase 3 adiciona `resilience4j-spring-boot3` que e o starter — precisa ser declarado em `dependencyManagement` do parent OU declarado com versao explicita no api-whatsapp/pom.xml. **Decisao recomendada:** adicionar ao `dependencyManagement` do parent para consistencia futura (Phase 4 outbound vai precisar do mesmo).

[VERIFIED: parent pom.xml linha 33] `<resilience4j.version>2.2.0</resilience4j.version>` — versao confirmada existente, mesma de lib-consultas-client.

[CITED: oneuptime.com/blog 2026-02-01 + resilience4j.readme.io] resilience4j-spring-boot3 + spring-boot-starter-aop sao os 2 deps minimos para annotations. spring-boot-starter-actuator e opcional (so para metrics). Phase 3 nao precisa actuator ainda — Phase 6 pode adicionar.

### Supporting (existentes, reusadas)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| spring-boot-starter-web | 3.5.9 | RestClient + ApplicationEventPublisher + @Async support | Listener + clientes HTTP |
| spring-context | 6.2.x | `@TransactionalEventListener`, `@EnableAsync`, `ThreadPoolTaskExecutor` | Async config |
| spring-tx | 6.2.x | `@TransactionalEventListener(AFTER_COMMIT)` infra | Listener |
| jackson-databind | 2.18.x (BOM) | JSON serialization para ComandoCallbackDTO + MetaMediaResultado | RestClient body conversion |

### WireMock (test scope) — alinhamento com QA-02 da Phase 6

| Library | Version | Purpose |
|---------|---------|---------|
| org.wiremock:wiremock-standalone | 3.10.0 | Mock HTTP server para Meta Graph + ERP callback em integration tests |

**Decisao:** WireMock 3.10.0 standalone (uber-jar com Jetty 12 incluso) evita conflito com Spring Boot 3.5 que NAO traz Jetty 11. Alternativa `wiremock-jetty12` requer adicionar `jetty-http2-server` separadamente — overhead extra. Standalone shadows tudo internamente.

[CITED: wiremock.org/docs/spring-boot + wiremock-examples GitHub] WireMock 3.5.2+ tem Jetty 12 support. Versao 3.10.0 e atual estavel (2025).

[ASSUMED] Phase 3 introduz a primeira dependencia de WireMock no projeto. QA-02 da Phase 6 menciona "WireMock 3.8.1" — Phase 3 pode usar 3.10.0 (newer minor) ou alinhar com 3.8.1 do plano. Recomendacao: alinhar com 3.10.0 (mais recente) e atualizar QA-02 quando Phase 6 chegar — ou aceitar 3.8.1 que tambem tem Jetty 12 support. **Risk se wrong:** versao errada nao quebra nada operacional, apenas inconsistencia de plano.

### Alternativas Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `RestClient` (Spring 6+) | `RestTemplate` (lib-consultas-client) | RestTemplate funciona mas Spring 6 marca para minor maintenance; novo codigo deve usar RestClient. lib-consultas-client e legado. |
| `@Async` + ThreadPool | Spring Cloud Stream / RabbitMQ | Overhead enorme; nao alinha com D1 on-premise. Pool em-processo e suficiente. |
| `@TransactionalEventListener(AFTER_COMMIT)` | Manual `@Async` apos save() | AFTER_COMMIT garante invariant: event NUNCA dispara se commit falhou. Manual approach quebra esse invariant. |
| Resilience4j annotations | Programmatic `decorateSupplier` | Programmatic e menos limpo mas mais flexivel. lib-consultas-client usa programmatic; Phase 3 usa annotations para simplicidade — sem composicao multi-decorator. |
| `wiremock-jetty12` modular | `wiremock-standalone` uber-jar | Modular requer 2 deps (wiremock + jetty-http2-server); standalone e 1 dep; tradeoff e size do JAR (Mb extras) — aceitavel em test scope. |

**Installation (api-whatsapp/pom.xml additions):**

```xml
<!-- Resilience4j Spring Boot 3 starter (auto-config + AOP-driven annotations) -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
<!-- AOP runtime para @CircuitBreaker + @Retry -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>

<!-- Test scope: WireMock para integration tests -->
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <version>3.10.0</version>
    <scope>test</scope>
</dependency>
```

**parent pom.xml addition (in `<dependencyManagement>`):**

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>${resilience4j.version}</version>
</dependency>
```

**Version verification:** Validar que parent tem `<resilience4j.version>2.2.0</resilience4j.version>` (ja confirmado). Sem mudanca de versao Java (21) ou Spring Boot (3.5.9).

## Architecture Patterns

### System Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Meta Cloud (origem do webhook)                                          │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │ POST /webhook/whatsapp
                                     │ X-Hub-Signature-256
                                     ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  api-whatsapp (porta 9193, Spring Boot 3.5.9, thread http-nio)           │
│                                                                          │
│  ╔══════════ FAST-PATH (sincrono na thread HTTP) ═══════════════╗       │
│  ║  HmacSignatureFilter (Phase 1, HIGHEST_PRECEDENCE)            ║       │
│  ║      └─ valida X-Hub-Signature-256 + cacheia body             ║       │
│  ║  ▼                                                             ║       │
│  ║  WebhookController.receber                                     ║       │
│  ║      └─ extrai byte[] do CachedBodyHttpServletRequest          ║       │
│  ║  ▼                                                             ║       │
│  ║  MensagemService.processarWebhook (REFATORADO em Phase 3)      ║       │
│  ║      ├─ WebhookPayloadParser.extrair (Jackson)                 ║       │
│  ║      ├─ for each msg:                                          ║       │
│  ║      │    ├─ IdempotencyService.tentarPersistir → boolean novo ║       │
│  ║      │    └─ if novo: publishEvent(MensagemPersistidaEvent)    ║       │
│  ║      └─ statuses: log.debug + ignora                           ║       │
│  ║  ▼                                                             ║       │
│  ║  return ResponseEntity.ok() ─── HTTP 200 ao Meta (<1s)         ║       │
│  ╚════════════════════════════════════════════════════════════════╝       │
│                                                                          │
│  -- AFTER_COMMIT --- Spring TransactionSynchronization callback ---------│
│                                                                          │
│  ╔══════════ ASYNC (thread whatsappTaskExecutor) ════════════════╗       │
│  ║  MensagemAsyncListener.aoMensagemPersistida                    ║       │
│  ║      ├─ [1] mediaId? → MetaMediaClient.baixar                  ║       │
│  ║      │      ├─ GET graph.facebook.com/v22.0/{id} → URL temp    ║       │
│  ║      │      └─ GET URL → byte[] + mime + filename              ║       │
│  ║      │      404 → Optional.empty + log.warn                    ║       │
│  ║      ├─ [2] ClienteZapService.identificar(telefone) → cliente  ║       │
│  ║      ├─ [3] ClienteZapService.atualizarUltimaMensagemEm        ║       │
│  ║      ├─ [4] ComandoExtractor.extrair(tipo, conteudo) → comando ║       │
│  ║      └─ [5] ErpCallbackClient.despachar(ComandoCallbackDTO)    ║       │
│  ║              @CircuitBreaker(erp-callback)                     ║       │
│  ║              @Retry(erp-callback)                              ║       │
│  ╚════════════════════════════════════════════════════════════════╝       │
│                                     │                                    │
│                                     │ POST /api/modulos/whatsapp/comando │
│                                     ▼                                    │
└──────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  ERP-MUDAS (localhost:8090) — outro processo, fora do escopo Phase 3     │
└──────────────────────────────────────────────────────────────────────────┘
```

**Trace flow do happy-path (cliente envia "orcamento 1234" sem media):**

1. Meta POST → HmacSignatureFilter valida → CachedBodyHttpServletRequest envolve
2. WebhookController.receber → MensagemService.processarWebhook
3. Parser → 1 MensagemEntranteDTO `(wamid="wamid.X", telefone="554784178525", tipo="text", conteudo="orcamento 1234", mediaId=null)`
4. IdempotencyService.tentarPersistir → INSERT em mensagens_log → returns `true` (novo)
5. eventPublisher.publishEvent(new MensagemPersistidaEvent(wamid, telefone, "text", "orcamento 1234", null, null))
6. **transacao do save commit** → Spring callback dispara TransactionalEventListener
7. **return 200** ao Meta (<100ms total) — neste momento listener pode ainda nao ter rodado
8. Spring agenda Runnable no whatsappTaskExecutor → thread `whatsapp-async-1` pega
9. listener: mediaId=null → skip media download
10. listener: clienteZap.identificar("554784178525") → recupera (Phase 2 ja inseriu)
11. listener: clienteZap.atualizarUltimaMensagemEm("554784178525") → REQUIRES_NEW → commit imediato
12. listener: comandoExtractor.extrair("text", "orcamento 1234") → "orcamento"
13. listener: erpCallbackClient.despachar(`ComandoCallbackDTO(telefone, "orcamento", "orcamento 1234", clienteId, null, null, null)`)
14. RestClient POST → 200 do ERP → fim

### Recommended Project Structure

```
api-whatsapp/src/main/java/br/com/erpkit/whatsapp/
├── WhatsAppApplication.java         # +@EnableAsync (modificar)
├── config/
│   ├── WhatsAppProperties.java     # SEM mudanca (Phase 1)
│   └── AsyncConfig.java            # NOVO — ThreadPoolTaskExecutor
├── controller/
│   ├── WebhookController.java      # SEM mudanca (Phase 2 ja delega)
│   ├── HealthController.java       # SEM mudanca
├── service/
│   ├── MensagemService.java        # REFATORAR (remove identificar+atualizar; +publishEvent)
│   ├── WebhookPayloadParser.java   # SEM mudanca (Phase 2)
│   ├── IdempotencyService.java     # SEM mudanca (Phase 2)
│   ├── ClienteZapService.java      # SEM mudanca (Phase 2)
│   ├── HmacValidator.java          # SEM mudanca (Phase 1)
│   ├── ComandoExtractor.java       # NOVO — text → primeira palavra; interactive → id
│   ├── MetaMediaClient.java        # NOVO — 2-step Graph API download
│   ├── ErpCallbackClient.java      # NOVO — RestClient + @CircuitBreaker + @Retry
│   └── MensagemAsyncListener.java  # NOVO — orquestrador async
├── event/
│   └── MensagemPersistidaEvent.java # NOVO — record imutavel
├── dto/                             # Phase 2 DTOs intactos +
│   ├── ComandoCallbackDTO.java     # NOVO — payload do callback
│   ├── MetaMediaResultado.java     # NOVO — record (bytes, mimeType, filename)
│   └── MediaMetadataDTO.java       # NOVO — Jackson DTO step 1 do download
└── ...
```

### Pattern 1: Ack-First com `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`

**What:** Webhook controller responde 200 OK assim que HMAC e validado e mensagem nova foi persistida. Operacoes lentas (media download, ERP callback) rodam em thread pool dedicado APOS commit.

**When to use:** Boundary HTTP onde o caller (Meta) tem timeout agressivo (5s) e qualquer I/O externo (ERP, Cloud API) pode demorar.

**Example:**

```java
// Source: Spring Framework docs — Transaction-bound Events
// https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html

@Service
public class MensagemService {

    private static final Logger log = LoggerFactory.getLogger(MensagemService.class);

    private final WebhookPayloadParser parser;
    private final IdempotencyService idempotency;
    private final ApplicationEventPublisher eventPublisher;

    public MensagemService(WebhookPayloadParser parser,
                           IdempotencyService idempotency,
                           ApplicationEventPublisher eventPublisher) {
        this.parser = parser;
        this.idempotency = idempotency;
        this.eventPublisher = eventPublisher;
    }

    public void processarWebhook(byte[] rawBody) throws IOException {
        ParsedWebhook parsed = parser.extrair(rawBody);
        log.info("Webhook recebido: {} mensagens, {} statuses",
                 parsed.mensagens().size(), parsed.statuses().size());

        for (MensagemEntranteDTO m : parsed.mensagens()) {
            boolean novo = idempotency.tentarPersistir(
                m.wamid(), m.telefone(), Direcao.in, m.tipo(), m.conteudo(), m.mediaId()
            );
            if (!novo) {
                log.debug("wamid={} duplicado — Meta reenviou, ignorando dispatch", m.wamid());
                continue;
            }
            // Disparar evento APOS commit do INSERT (AFTER_COMMIT phase).
            // Listener async cuida de media download, identificar cliente,
            // atualizar timestamp e callback ERP. NAO bloqueia o ack 200.
            eventPublisher.publishEvent(new MensagemPersistidaEvent(
                m.wamid(), m.telefone(), m.tipo(), m.conteudo(), m.mediaId(), null
            ));
        }

        for (StatusEntranteDTO s : parsed.statuses()) {
            log.debug("Status callback ignorado em Phase 3: wamid={} status={}", s.wamid(), s.status());
        }
    }
}
```

```java
// Listener async — outro bean, em outra thread
@Component
public class MensagemAsyncListener {

    private static final Logger log = LoggerFactory.getLogger(MensagemAsyncListener.class);

    private final MetaMediaClient metaMediaClient;
    private final ClienteZapService clienteZapService;
    private final ComandoExtractor comandoExtractor;
    private final ErpCallbackClient erpCallbackClient;

    public MensagemAsyncListener(MetaMediaClient metaMediaClient,
                                 ClienteZapService clienteZapService,
                                 ComandoExtractor comandoExtractor,
                                 ErpCallbackClient erpCallbackClient) {
        this.metaMediaClient = metaMediaClient;
        this.clienteZapService = clienteZapService;
        this.comandoExtractor = comandoExtractor;
        this.erpCallbackClient = erpCallbackClient;
    }

    @Async("whatsappTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoMensagemPersistida(MensagemPersistidaEvent event) {
        log.debug("Listener async: wamid={}", event.wamid());

        // [1] media download — PRIMEIRA acao (PITFALLS C-08: URL Meta expira em 5min)
        String mediaBase64 = null;
        String mediaMimeType = null;
        String mediaFilename = null;
        if (event.mediaId() != null) {
            try {
                Optional<MetaMediaResultado> media = metaMediaClient.baixar(event.mediaId());
                if (media.isPresent()) {
                    mediaBase64 = Base64.getEncoder().encodeToString(media.get().bytes());
                    mediaMimeType = media.get().mimeType();
                    mediaFilename = media.get().filename();
                    log.debug("Media baixada: wamid={} mimeType={} bytes={}",
                              event.wamid(), mediaMimeType, media.get().bytes().length);
                } else {
                    log.warn("Media expirada/nao-disponivel: wamid={} mediaId={} — prosseguindo sem bytes",
                             event.wamid(), event.mediaId());
                }
            } catch (Exception e) {
                log.warn("Erro baixando media: wamid={} mediaId={}: {}",
                         event.wamid(), event.mediaId(), e.getMessage());
                // prosseguir sem bytes
            }
        }

        // [2] identificar cliente
        ClienteZap cliente;
        try {
            cliente = clienteZapService.identificar(event.telefone());
        } catch (Exception e) {
            log.error("Falha identificando cliente: telefone={}: {}", event.telefone(), e.getMessage(), e);
            return;
        }

        // [3] atualizar ultima_mensagem_em (REQUIRES_NEW — commit imediato; PITFALLS C-01)
        try {
            clienteZapService.atualizarUltimaMensagemEm(event.telefone());
        } catch (Exception e) {
            log.error("Falha atualizando ultima_mensagem_em: telefone={}: {}",
                      event.telefone(), e.getMessage(), e);
            return;
        }

        // [4] extrair comando
        String comando = comandoExtractor.extrair(event.tipo(), event.conteudo());
        if (comando == null) {
            log.debug("Sem comando para tipo={} — skip dispatch ERP", event.tipo());
            return;
        }

        // [5] dispatch ERP callback (Resilience4j cuida de retry+CB)
        ComandoCallbackDTO payload = new ComandoCallbackDTO(
            event.telefone(), comando, event.conteudo(),
            cliente.getIdClienteErp(), mediaBase64, mediaMimeType, mediaFilename
        );
        try {
            erpCallbackClient.despachar(payload);
        } catch (Exception e) {
            // Resilience4j fallbackMethod ja capturou e logou. Catch-all defensivo
            // para qualquer excecao que escape (ex: bug, OOM em base64).
            log.error("Falha definitiva no callback ERP: wamid={} comando={}: {}",
                      event.wamid(), comando, e.getMessage(), e);
        }
    }
}
```

**Trade-offs:**
- Falhas pos-ack sao perdidas silenciosamente — mitigacao: log.error estruturado com wamid + comando para correlacao operacional.
- AFTER_COMMIT semantics: se publisher (MensagemService.processarWebhook) NAO esta em transacao, listener e silenciosamente descartado. Mitigacao: confirmar que `IdempotencyService.tentarPersistir` ou `ClienteZapService` agora nao rodam mais antes do publishEvent — apenas IdempotencyService faz save() que abre transacao Spring Data JPA implicitamente.

**CRITICO** [VERIFIED: docs.spring.io transaction event reference]: `If no transaction is running, the listener is not invoked at all`. Em Phase 3, o caller (`MensagemService.processarWebhook`) NAO tem `@Transactional` na classe (Phase 2 verificou — preserva proxy AOP cross-bean). MAS `IdempotencyService.tentarPersistir` chama `repository.save(...)` que Spring Data JPA envolve em transacao implicita. **A transacao do save() e o trigger do AFTER_COMMIT**: quando `tentarPersistir` retorna, o save commit ja aconteceu — entao `publishEvent` no `MensagemService` esta FORA da transacao do save (a transacao ja committou).

**Risk identificado (A1):** Se `eventPublisher.publishEvent` rodar fora de transacao ativa, AFTER_COMMIT NUNCA dispara → listener nao roda → bug silencioso (mensagem persiste mas ERP nunca recebe callback).

**Mitigacao recomendada:** Anotar `MensagemService.processarWebhook` com `@Transactional` para abrir explicitamente uma transacao que envolve TODO o loop. publishEvent dentro dela. Quando o metodo retorna, transacao commit → AFTER_COMMIT dispara para cada evento publicado. **Esta e a abordagem canonica do Spring docs.**

```java
@Service
public class MensagemService {
    @Transactional  // CRITICO em Phase 3: abre transacao para AFTER_COMMIT funcionar
    public void processarWebhook(byte[] rawBody) throws IOException { ... }
}
```

Validacao de teste obrigatoria em Wave 5: smoke test que aciona o flow completo e confirma que listener foi chamado (via Mockito spy ou assercao DB para chamada do ClienteZapService).

### Pattern 2: Resilience4j com Annotations Declarativas

**What:** `@CircuitBreaker(name="erp-callback", fallbackMethod="fallbackDespachar")` + `@Retry(name="erp-callback")` em metodo de service. Spring AOP intercepta via proxy. Configuracao em `application.yml` por nome de instance.

**When to use:** I/O externo idempotente onde retry pode reduzir transient failures (5xx, timeout) sem efeitos colaterais cumulativos. **Em Phase 3 ROU-03, retry NAO deve causar duplicate ERP side-effect** — daqui que `retryExceptions` exclui 4xx.

**Example:**

```java
// Source: lib-consultas-client/ConsultasClientImpl.java (programmatic) +
// resilience4j-spring-boot-demo (annotations) — adapted

@Service
public class ErpCallbackClient {

    private static final Logger log = LoggerFactory.getLogger(ErpCallbackClient.class);

    private final RestClient restClient;
    private final WhatsAppProperties properties;

    public ErpCallbackClient(WhatsAppProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getCallbackTimeout().toMillis());
        factory.setReadTimeout((int) properties.getCallbackTimeout().toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getErpCallbackUrl())
                .requestFactory(factory)
                .build();
    }

    @CircuitBreaker(name = "erp-callback", fallbackMethod = "fallbackDespachar")
    @Retry(name = "erp-callback")
    public void despachar(ComandoCallbackDTO payload) {
        log.debug("Dispatch ERP: telefone={} comando={}", payload.telefone(), payload.comando());
        restClient.post()
                .uri("/api/modulos/whatsapp/comando")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        log.info("ERP callback ok: telefone={} comando={}", payload.telefone(), payload.comando());
    }

    /**
     * Fallback chamado quando Resilience4j esgota retries OU circuit abre.
     * IMPORTANTE: signature deve ter Throwable como ULTIMO parametro.
     * SEM retry adicional, SEM rethrow — apenas log estruturado (D-08, ROU-03).
     */
    private void fallbackDespachar(ComandoCallbackDTO payload, Throwable t) {
        log.error("ERP callback falhou apos retry+CB: telefone={} comando={}: {}",
                  payload.telefone(), payload.comando(), t.getMessage());
        // ack-first: ERP pode ter executado parcialmente; nao retentar.
    }
}
```

**Trade-offs:**
- Annotations vs programmatic: annotations sao mais limpas mas exigem `spring-boot-starter-aop` no classpath. Sem AOP, annotations viram no-op SILENCIOSAMENTE — bug grave se esquecer.
- Aspect order (Spring Boot starter): `Retry(CircuitBreaker(...))` — Retry envolve CircuitBreaker. Significa: retry tenta 3x; cada falha conta como 1 chamada para sliding-window do CB. Se 5 das ultimas 10 chamadas (3 retries de 1 dispatch contam como 1 chamada para CB sliding window — Resilience4j sabe deduplicar via internal logic). **CRITICO testar:** spike no Wave 4 confirma que circuit conta corretamente.

[CITED: oneuptime.com 2026 + resilience4j docs] Self-call dentro da mesma classe NAO ativa AOP. ErpCallbackClient anotado e injetado no MensagemAsyncListener — ok. Mas se o listener tentasse `this.despachar()` em uma reentrancia interna, AOP NAO interceptaria.

### Pattern 3: 2-Step Media Download com Bearer Header

**What:** Meta media e referenciada por ID curto (~30 chars) no webhook. Para baixar bytes:
1. GET `https://graph.facebook.com/v22.0/{media_id}` → JSON com URL temporaria + metadata (mime_type, sha256, file_size, filename)
2. GET URL → bytes binarios

URL temporaria expira em 5 minutos. Bearer accessToken em Authorization header em ambas chamadas.

**When to use:** Sempre que webhook tem `messages[].image|document|audio|video.id` — Phase 3 listener faz isso como primeira acao para minimizar window.

**Example:**

```java
// Source: developers.facebook.com/docs/whatsapp/cloud-api/reference/media/media-api
// + Medium @shreyas.sreedhar (Node.js exemplo)

@Service
public class MetaMediaClient {

    private static final Logger log = LoggerFactory.getLogger(MetaMediaClient.class);

    private final RestClient restClient;
    private final WhatsAppProperties properties;

    public MetaMediaClient(WhatsAppProperties properties) {
        this.properties = properties;
        // Sem Resilience4j — primeira acao apos ack precisa ser RAPIDA.
        // Sem timeout custom — confiar no default do http client (Spring Boot 3.5
        // default 5s no SimpleClientHttpRequestFactory eh aceitavel para ~10MB).
        this.restClient = RestClient.builder()
                .baseUrl("https://graph.facebook.com/v22.0")
                .build();
    }

    /**
     * Baixa media de entrada do Meta. Sequencia:
     *   1. GET /{media_id} → MediaMetadataDTO (url, mime_type, filename)
     *   2. GET url → byte[]
     *
     * @param mediaId ID retornado em messages[].image|document|audio.id
     * @return Optional com bytes + metadata; Optional.empty() se URL expirada (404)
     */
    public Optional<MetaMediaResultado> baixar(String mediaId) {
        // Step 1: metadata
        MediaMetadataDTO metadata;
        try {
            metadata = restClient.get()
                    .uri("/{id}", mediaId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                    .retrieve()
                    .body(MediaMetadataDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Meta media metadata 404 — URL expirada (5min): mediaId={}", mediaId);
            return Optional.empty();
        }
        if (metadata == null || metadata.getUrl() == null) {
            log.warn("Meta media metadata sem URL: mediaId={}", mediaId);
            return Optional.empty();
        }

        // Step 2: bytes binarios. URL e absoluta (lookaside.fbsbx.com); o RestClient
        // segue. Bearer continua obrigatorio no header da CDN do Meta.
        byte[] bytes;
        try {
            bytes = restClient.get()
                    .uri(metadata.getUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                    .retrieve()
                    .body(byte[].class);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Meta media bytes 404 — URL expirada antes do step 2: mediaId={}", mediaId);
            return Optional.empty();
        }
        if (bytes == null || bytes.length == 0) {
            log.warn("Meta media bytes vazio: mediaId={}", mediaId);
            return Optional.empty();
        }

        return Optional.of(new MetaMediaResultado(bytes, metadata.getMimeType(), metadata.getFilename()));
    }
}
```

**MediaMetadataDTO (Jackson DTO — getters explicitos para auto-deserialization):**

```java
package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaMetadataDTO {

    private String url;
    @JsonProperty("mime_type")
    private String mimeType;
    private String filename;
    private String sha256;
    @JsonProperty("file_size")
    private Long fileSize;
    private String id;
    @JsonProperty("messaging_product")
    private String messagingProduct;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMessagingProduct() { return messagingProduct; }
    public void setMessagingProduct(String messagingProduct) { this.messagingProduct = messagingProduct; }
}
```

**MetaMediaResultado (record — uso interno):**

```java
package br.com.erpkit.whatsapp.dto;

public record MetaMediaResultado(byte[] bytes, String mimeType, String filename) { }
```

**Trade-offs:**
- Sem Resilience4j: aceitavel porque circuit breaker ON Meta media adicionaria latencia desnecessaria; 404 unico (URL expirou) NAO deveria abrir circuit.
- Sem timeout custom: SimpleClientHttpRequestFactory default e infinito — recomendar `spring.http.client.read-timeout=10s` no application.yml para defesa em profundidade.
- byte[] inteiro em memoria: PDFs >10MB podem stressar heap. Aceitavel para Phase 3 (volumes baixos on-premise). Phase 6+ pode revisitar via streaming se observar problema.

### Anti-Patterns to Avoid

- **Self-call de despachar() dentro do ErpCallbackClient**: Spring AOP nao intercepta self-call → annotations Resilience4j viram no-op silencioso. NUNCA chamar `this.despachar()` em metodo que tenta retry manual; sempre injetar `ErpCallbackClient` em outro bean.

- **publishEvent sem transacao ativa**: AFTER_COMMIT silenciosamente descarta. SEMPRE garantir que `MensagemService.processarWebhook` esta `@Transactional`. Spring docs sao explicitos: `If no transaction is running, the listener is not invoked at all` (Spring Framework reference).

- **DB writes dentro do listener AFTER_COMMIT na MESMA thread**: silently discarded (Javadoc TransactionalEventListener: "any data access code triggered at this point will still 'participate' in the original transaction, but changes will not be committed"). Uso de `@Async` move para outra thread sem transacao herdada — `ClienteZapService` no listener abre nova transacao (default `@Transactional` ja existe via Spring Data JPA save).

- **Bearer accessToken como query param**: PITFALLS C-14 — vaza em logs. SEMPRE usar `Authorization: Bearer` header.

- **`SimpleAsyncTaskExecutor` ou `@Async` sem qualificador**: cria thread por task → OOM em pico. SEMPRE `@Async("whatsappTaskExecutor")` referenciando bean explicito.

- **Logging do RestClient response com Bearer**: PITFALLS C-09. Spring Boot 3.5 RestClient nao loga headers por default em INFO; manter `org.springframework.web` em INFO (ja feito em application.yml Phase 1). NUNCA habilitar DEBUG em producao.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Async dispatch | Custom `Executors.newFixedThreadPool` + manual scheduling | `@Async("whatsappTaskExecutor")` + `@EnableAsync` + `ThreadPoolTaskExecutor` bean | Spring honra MDC propagation, exception handling, integracao com test framework |
| Transactional event semantics | Manual save → eventPublisher.publishEvent | `@TransactionalEventListener(AFTER_COMMIT)` | Garante invariant: event so dispara apos commit; sem isso, e possivel publicar event de mensagem que rolled back |
| Circuit breaker | Custom `AtomicInteger failures` + open/closed state | `@CircuitBreaker(name=...)` Resilience4j | State machine, sliding window, half-open transitions sao non-trivial — Resilience4j tem 5+ anos de production hardening |
| Retry exponencial | Custom `for (int i=0; i<3; i++) { try { ... } catch { Thread.sleep(...) } }` | `@Retry(name=...)` + config yaml | Resilience4j respeita exception type (5xx vs 4xx), backoff jitter, integracao com CB |
| HTTP timeout | Custom socket timeout via plain Java | `RestClient.builder().requestFactory(SimpleClientHttpRequestFactory)` com setConnectTimeout/setReadTimeout | Spring abstraction permite trocar Apache HttpClient5/Jetty/JDK sem mudar codigo |
| Base64 encoding | Custom byte→hex→string | `java.util.Base64.getEncoder().encodeToString` | JDK 8+ nativo; sem dependencia |
| Webhook payload caching | ContentCachingRequestWrapper | `CachedBodyHttpServletRequest` (Phase 1 ja entregue) | PITFALLS C-02; Phase 1 spike validou |
| Idempotency | SELECT-then-INSERT | `MensagemLogRepository.save` + catch DataIntegrityViolationException (Phase 2) | TOCTOU race; PITFALLS C-06 |
| Phone normalization | Custom regex inline | `TelefoneBR.normalizar` (Phase 2) | Regra ANATEL com 14 DDDs; Phase 2 entregou |

**Key insight:** Phase 3 reusa 6 servicos Phase 1+2 (HmacValidator, CachedBodyHttpServletRequest, WebhookPayloadParser, IdempotencyService, ClienteZapService, TelefoneBR) e adiciona apenas 4 novos (ComandoExtractor, MetaMediaClient, ErpCallbackClient, MensagemAsyncListener) + 1 evento + 3 DTOs + 1 config. Surface area minima.

## Common Pitfalls

### Pitfall 1: AFTER_COMMIT silently discarded sem transacao ativa

**What goes wrong:** `eventPublisher.publishEvent(...)` chamado fora de transacao Spring → listener com `@TransactionalEventListener(AFTER_COMMIT)` NUNCA roda. Mensagem persiste, mas ERP nunca recebe callback.

**Why it happens:** Phase 2 `MensagemService` nao tem `@Transactional` na classe (deliberadamente — preservar AOP cross-bean). Phase 3 adiciona publishEvent — em algum ponto da execucao, transacao ja committou (apos repository.save dentro de IdempotencyService). publishEvent dentro de `MensagemService.processarWebhook` esta fora de transacao.

**How to avoid:** Anotar `MensagemService.processarWebhook` com `@Transactional` em Phase 3. Spring abre transacao no entry point; persist + publishEvent dentro dela; commit ao retornar; AFTER_COMMIT dispara.

**Warning signs:**
- Tests passando mas ERP callback nunca acontece em producao.
- Logs mostram "Webhook recebido: 1 mensagens, 0 statuses" mas zero "Listener async: wamid=...".
- Para detectar em test: smoke integration test com WireMock para ERP — assercao `verify(postRequestedFor(...))`.

### Pitfall 2: Resilience4j annotations viram no-op silencioso sem `spring-boot-starter-aop`

**What goes wrong:** `@CircuitBreaker` + `@Retry` annotations nao tem efeito; metodo executa sem decorator.

**Why it happens:** Annotations Resilience4j Spring Boot dependem de Spring AOP proxy. Sem `spring-boot-starter-aop`, AspectJ runtime nao detecta `@Aspect` classes do Resilience4j → no-op.

**How to avoid:** Adicionar `spring-boot-starter-aop` ao pom.xml na Wave 1. Validar via test: invocar `despachar()` com WireMock retornando 500 — assert WireMock counter == 3 (retry funcionou). Sem AOP, counter == 1.

**Warning signs:**
- Circuit breaker nunca abre em producao mesmo com ERP offline (retries nao acontecem).
- WireMock test: post counter == 1 quando esperava 3.
- Application boot logs: ausencia de `Caused by: ... AspectJ` warnings.

### Pitfall 3: SyncTaskExecutor + AFTER_COMMIT race no test

**What goes wrong:** Test profile sobrescreve `whatsappTaskExecutor` com `SyncTaskExecutor` (executa inline na thread chamadora). Mas AFTER_COMMIT dispara DEPOIS do commit, e em test pode ser que commit ainda nao tenha acontecido quando test assert roda.

**Why it happens:** Spring TransactionalEventListener mecanismo: handler de TransactionSynchronization invoca listener dentro do `afterCommit()` callback do TransactionManager. Mesmo com SyncTaskExecutor (`task.run()` direto inline), o callback so dispara apos commit do save. Se test usa `@Transactional` no metodo de teste, commit so acontece quando test method retorna — listener roda DEPOIS do test method, nao antes.

**How to avoid:**
- **NAO** usar `@Transactional` no test method.
- `MensagemService.processarWebhook(...)` ABRE sua propria transacao via `@Transactional` (Pitfall 1) — quando metodo retorna, commit ja aconteceu, AFTER_COMMIT ja disparou (sync via SyncTaskExecutor) — assertions seguras.
- WebhookPersistenciaIntegrationTest e MensagemServiceTest existentes JA seguem esse padrao (sem `@Transactional`).

**Warning signs:**
- Test assert `clienteZap.getUltimaMensagemEm()` returns null mesmo apos publishEvent.
- Test passa quando rodado isolado mas falha em suite (cache de SpringContext compartilha estado).

### Pitfall 4: Resilience4j circuit breaker shared state entre tests causa flake

**What goes wrong:** `circuitBreakerRegistry` e singleton no SpringContext. Tests sequenciais que provocam circuit-open em test A vao encontrar circuit aberto em test B → todos os despachos falham com `CallNotPermittedException`.

**Why it happens:** Resilience4j state machine persiste entre tests do mesmo SpringContext. `@SpringBootTest` cacheia contexto.

**How to avoid:**
- `@BeforeEach` reset: `circuitBreakerRegistry.find("erp-callback").ifPresent(cb -> cb.reset())`.
- Alternativa: configuracao test-specific com sliding window grande (50) e threshold 90% — quase nunca abre em test.
- Ou: usar `@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)` — overhead alto mas seguro.

**Warning signs:**
- Test isolado passa, suite falha (state poluida).
- Logs `CallNotPermittedException` em test que NAO deveria provocar circuit.

### Pitfall 5: Bearer token vazando em log de exception do RestClient

**What goes wrong:** `HttpClientErrorException.message` pode incluir headers da request que falhou — incluindo Authorization. Em log `e.getMessage()`, Bearer aparece.

**Why it happens:** Default RestClient nao mascara. Phase 1 application.yml configura `org.springframework.web=INFO` (nao DEBUG) — protege contra access logs, mas `e.getMessage()` em log code nosso pode incluir.

**How to avoid:**
- NAO usar `log.error("Falha: {}", e)` — usa `e.toString()` que pode incluir tudo.
- USAR `log.error("Falha: {}", e.getMessage())` — getMessage() do HttpClientErrorException tem apenas status + statusText, nao headers.
- Defesa adicional: ClientHttpRequestInterceptor em RestClient builder que mascara `Authorization` se DEBUG ativo (Phase 6+).

**Warning signs:**
- Grep no log de producao: `Bearer eyJ` — alerta P1.
- Code review: qualquer `log.error("...", exception)` (passando exception inteira) e suspect.

### Pitfall 6: Aspect order Retry(CircuitBreaker) — counters errados

**What goes wrong:** Esperando que circuit veja exatamente 1 falha por dispatch (apesar de 3 retries), mas circuit pode contar cada retry como 1 falha → abre prematuramente.

**Why it happens:** Resilience4j Spring Boot starter aplica Retry POR FORA de CircuitBreaker — `Retry(CircuitBreaker(...))`. Significa: cada chamada interna ao CB e contada para sliding-window. 3 retries = 3 chamadas para CB.

**How to avoid:**
- Aceitar que para 1 dispatch falho, CB conta 3 chamadas. Configurar `slidingWindowSize=10` significa que 4 dispatches falhos consecutivos = 12 chamadas, todas falha → CB abre. **Em producao, isso e ok — ERP offline 4 mensagens consecutivas e razoavel para abrir circuit.**
- Documentar em `application.yml` comment.
- Test: dispatch 4x ERP-down → assert circuit OPEN.

**Warning signs:**
- Circuit abre apos 3 dispatches em producao quando esperava 10.
- Test: counter no WireMock == 9 (3 dispatches × 3 retries) mas circuit aberto antes de "10 chamadas".

## Code Examples

### Example 1: AsyncConfig + ThreadPoolTaskExecutor

```java
// Source: Spring Framework docs — https://docs.spring.io/spring-framework/reference/integration/scheduling.html
package br.com.erpkit.whatsapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Pool dedicado para o {@code MensagemAsyncListener} (D-02 do CONTEXT.md).
 *
 * <p>Pool dedicado em vez de SimpleAsyncTaskExecutor: SimpleAsync cria thread por task —
 * em pico de mensagens, OOM. Pool fixo com queueCapacity 100 + CallerRunsPolicy degrada
 * graciosamente: sob estresse extremo, listener roda inline na thread chamadora
 * (que e o async original, nao o webhook), mantendo ack-first valido.
 *
 * <p><b>Test profile:</b> sobrescreve este bean com SyncTaskExecutor via
 * {@code @TestConfiguration} ou Spring properties — async listener vira sincrono em test.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "whatsappTaskExecutor")
    public ThreadPoolTaskExecutor whatsappTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("whatsapp-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

### Example 2: WhatsAppApplication com @EnableAsync

```java
// MODIFICACAO em arquivo existente
package br.com.erpkit.whatsapp;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
// nao precisa de @EnableAsync aqui — AsyncConfig ja anota
// (manter centralizado em AsyncConfig)

@SpringBootApplication(scanBasePackages = "br.com.erpkit")
@EnableConfigurationProperties(WhatsAppProperties.class)
public class WhatsAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhatsAppApplication.class, args);
    }
}
```

**Decisao:** `@EnableAsync` fica em `AsyncConfig` (so 1 lugar) — nao no `WhatsAppApplication`. Spring detecta via component scan.

### Example 3: MensagemPersistidaEvent

```java
// Source: Spring Framework docs — events
package br.com.erpkit.whatsapp.event;

/**
 * Event imutavel disparado APOS persistencia bem-sucedida em mensagens_log.
 *
 * <p>Listener async ({@code MensagemAsyncListener}) consome em pool dedicado.
 * AFTER_COMMIT garante que evento nao dispara se INSERT rolled back.
 *
 * @param wamid       ID unico do Meta (correlacao)
 * @param telefone    JA NORMALIZADO via TelefoneBR (parser fez)
 * @param tipo        TipoMensagem constants (text, interactive_button, document, etc.)
 * @param conteudo    payload extraido (texto cru, "id|title", filename) — pode ser null
 * @param mediaId     id Meta para media (document/image/audio) — null se sem media
 * @param idClienteErp campo reservado, sempre null neste evento — listener busca via service
 */
public record MensagemPersistidaEvent(
    String wamid,
    String telefone,
    String tipo,
    String conteudo,
    String mediaId,
    Long idClienteErp
) { }
```

### Example 4: ComandoExtractor

```java
package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.util.TipoMensagem;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Extrai keyword/comando de uma mensagem entrante (D-05 do CONTEXT.md).
 *
 * <ul>
 *   <li><b>text:</b> primeira palavra do conteudo, lowercase. Ex: "Orcamento 1234" → "orcamento"</li>
 *   <li><b>interactive_button / interactive_list:</b> id (parser Phase 2 formata "id|title").
 *       Ex: "aprovar_42|Aprovar" → "aprovar_42"</li>
 *   <li><b>document / image / audio / video:</b> tipo literal — "document", "image", etc.</li>
 *   <li><b>desconhecido / null:</b> retorna null (skip dispatch ERP)</li>
 * </ul>
 *
 * <p>Logica pura sem I/O — testavel sem Spring context (preferir
 * {@code @ExtendWith(MockitoExtension.class)} ou JUnit puro).
 */
@Service
public class ComandoExtractor {

    public String extrair(String tipo, String conteudo) {
        if (tipo == null) {
            return null;
        }
        return switch (tipo) {
            case TipoMensagem.TEXT -> primeiraPalavra(conteudo);
            case TipoMensagem.INTERACTIVE_BUTTON, TipoMensagem.INTERACTIVE_LIST -> idDeInteractive(conteudo);
            case TipoMensagem.DOCUMENT, TipoMensagem.IMAGE, TipoMensagem.AUDIO -> tipo;
            default -> null;  // desconhecido: skip dispatch
        };
    }

    private String primeiraPalavra(String conteudo) {
        if (conteudo == null || conteudo.isBlank()) {
            return null;
        }
        return conteudo.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
    }

    private String idDeInteractive(String conteudo) {
        // parser Phase 2 formata como "id|title"
        if (conteudo == null) {
            return null;
        }
        int sep = conteudo.indexOf('|');
        return sep > 0 ? conteudo.substring(0, sep).toLowerCase(Locale.ROOT) : null;
    }
}
```

### Example 5: ComandoCallbackDTO

```java
package br.com.erpkit.whatsapp.dto;

/**
 * Payload do callback POST /api/modulos/whatsapp/comando ao ERP (D-06 do CONTEXT.md).
 *
 * <p>Record imutavel. Jackson auto-serializa para JSON via convertor padrao do Spring Boot.
 *
 * @param telefone        normalizado via TelefoneBR (digitos)
 * @param comando         keyword extraida (ex: "orcamento", "aprovar_42", "document")
 * @param payload         conteudo cru da mensagem (texto, "id|title", filename) — pode ser null
 * @param idCliente       id_cliente_erp resolvido — null se cliente nao mapeado
 * @param mediaBase64     bytes da media em base64 — null se sem media ou expirado
 * @param mediaMimeType   MIME do arquivo — null se sem media
 * @param mediaFilename   nome original — null se sem media
 */
public record ComandoCallbackDTO(
    String telefone,
    String comando,
    String payload,
    Long idCliente,
    String mediaBase64,
    String mediaMimeType,
    String mediaFilename
) { }
```

### Example 6: application.yml additions (Resilience4j)

```yaml
# api-whatsapp/src/main/resources/application.yml — ADICIONAR ao final

# Resilience4j: circuit breaker + retry para ErpCallbackClient (D-03 + ROU-03).
#
# Aspect order (Resilience4j Spring Boot starter): Retry POR FORA de CircuitBreaker.
# Significa: 1 dispatch failed = 3 retries (com backoff 1s/2s/4s) = 3 calls counted
# pelo CB sliding-window. 4 dispatches failed consecutivos = 12 calls = circuit open.
#
# retry-exceptions: APENAS transient errors. NAO 4xx categoricos (400/401/403/404 etc.)
# — esses indicam bug de configuracao no ERP, nao falha temporaria; retry duplicaria
# side effect (PITFALLS C-05 / ROU-03 D-08).
resilience4j:
  circuitbreaker:
    instances:
      erp-callback:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 3
        register-health-indicator: false  # Phase 6 pode habilitar via actuator
  retry:
    instances:
      erp-callback:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2.0
        retry-exceptions:
          - org.springframework.web.client.HttpServerErrorException
          - java.net.SocketTimeoutException
          - java.io.IOException
        # 4xx categoricos NAO em retry-exceptions — Resilience4j por default NAO retenta
        # excecoes nao listadas. HttpClientErrorException (4xx) NAO retenta.

# Spring HTTP client global timeout — defesa em profundidade para MetaMediaClient
# (sem Resilience4j proprio). RestClient pode ainda override per-instance.
spring:
  http:
    client:
      connect-timeout: 5s
      read-timeout: 10s
```

### Example 7: Test profile override com SyncTaskExecutor

**Opcao A (recomendada): @TestConfiguration em test class:**

```java
package br.com.erpkit.whatsapp.service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@TestConfiguration
public class AsyncTestConfig {

    /**
     * Substitui o bean {@code whatsappTaskExecutor} de produção em testes.
     * SyncTaskExecutor executa task.run() inline na thread chamadora — listener
     * @Async vira sincrono. Combinado com @TransactionalEventListener(AFTER_COMMIT),
     * o listener dispara apos commit do save() do MensagemService — quando o metodo
     * retorna, todo o flow ja rodou (sync) — assertions DB seguras sem Awaitility.
     */
    @Bean(name = "whatsappTaskExecutor")
    @Primary
    public TaskExecutor whatsappTaskExecutor() {
        return new SyncTaskExecutor();
    }
}
```

Uso no test:

```java
@SpringBootTest(classes = WhatsAppApplication.class, webEnvironment = MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AsyncTestConfig.class)
class WebhookAsyncIntegrationTest {
    // ... assertions DB sincronas pos-publishEvent
}
```

**Opcao B (alternativa via property): adicionar em `application-test.yml`:**

```yaml
# Nao funciona — Spring Boot 3.5 NAO tem property para override TaskExecutor.
# Forçar @TestConfiguration ou bean override programatico.
```

[ASSUMED] Property-based override para `whatsappTaskExecutor` nao existe no Spring Boot — `@TestConfiguration` e a abordagem canonica. Risk se wrong: minimal — abordagem standard.

### Example 8: pom.xml additions

```xml
<!-- api-whatsapp/pom.xml — ADICIONAR ao <dependencies> -->

<!-- Resilience4j Spring Boot 3 starter (D-03) -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>

<!-- AOP runtime para @CircuitBreaker + @Retry annotations -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>

<!-- WireMock standalone para integration tests (Phase 3 Wave 6) -->
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <version>3.10.0</version>
    <scope>test</scope>
</dependency>
```

```xml
<!-- pom.xml (parent) — ADICIONAR em <dependencyManagement>/<dependencies> -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>${resilience4j.version}</version>
</dependency>
```

## Implementation Strategy Overview (6 Waves)

### Wave 1: pom.xml + AsyncConfig + @EnableAsync + application.yml

**Goal:** Infrastruture base — Resilience4j + AOP no classpath, ThreadPoolTaskExecutor configurado, application.yml com bloco resilience4j + spring.http.client.

**Tasks:**
- Editar parent `pom.xml` adicionando `resilience4j-spring-boot3` ao dependencyManagement (versao via `${resilience4j.version}`).
- Editar `api-whatsapp/pom.xml`: adicionar `resilience4j-spring-boot3` + `spring-boot-starter-aop` + `wiremock-standalone` (test).
- Criar `config/AsyncConfig.java` com `@Configuration @EnableAsync` + `@Bean whatsappTaskExecutor`.
- Editar `application.yml`: bloco `resilience4j.*` + `spring.http.client.*` (timeouts globais).
- Smoke test: `./mvnw test -pl api-whatsapp -Dtest=...` + boot do app via SpringBootTest classe simples confirma sem erros de bean.

**Commitable:** sim. Sem mudanca funcional, mas infra adicionada.

**Verification:**
- `./mvnw verify -pl api-whatsapp -am` BUILD SUCCESS.
- Boot logs: `whatsappTaskExecutor` bean criado.
- Sem warnings de AspectJ ausente.

### Wave 2: MensagemPersistidaEvent + ComandoExtractor + ComandoCallbackDTO

**Goal:** Tipos de dados e logica pura. Sem I/O. Testavel via JUnit puro.

**Tasks:**
- Criar `event/MensagemPersistidaEvent.java` (record).
- Criar `service/ComandoExtractor.java` com switch-statement.
- Criar `dto/ComandoCallbackDTO.java` (record).
- Criar `dto/MetaMediaResultado.java` (record).
- Criar `dto/MediaMetadataDTO.java` (Jackson DTO).
- Tests: `ComandoExtractorTest` cobrindo:
  - text → primeira palavra lowercase (incluindo "Orçamento 1234" → "orçamento")
  - text vazio/null → null
  - interactive_button "aprovar_42|Aprovar" → "aprovar_42"
  - interactive_button sem "|" → null
  - document → "document"
  - desconhecido → null

**Commitable:** sim. Tipos novos sem comportamento integrado.

**Verification:**
- `./mvnw test -pl api-whatsapp -Dtest=ComandoExtractorTest` 6+ tests verdes.

### Wave 3: MetaMediaClient

**Goal:** 2-step Graph API media download com 404 graceful.

**Tasks:**
- Criar `service/MetaMediaClient.java` com RestClient + 2 GETs.
- Tests `MetaMediaClientTest` com WireMock simulando Graph API:
  - happy path: GET `/v22.0/{id}` retorna 200 + JSON metadata; GET URL retorna 200 + bytes.
  - 404 step 1: GET `/v22.0/{id}` retorna 404 → returns Optional.empty + log.warn.
  - 404 step 2: GET `/v22.0/{id}` retorna 200 mas GET URL retorna 404 → Optional.empty.
  - URL null em metadata → Optional.empty.
  - Bearer presente em ambas requests (assert via WireMock matchers).

**Commitable:** sim.

**Verification:**
- `./mvnw test -pl api-whatsapp -Dtest=MetaMediaClientTest` 4+ tests verdes.
- WireMock assertion: `verify(getRequestedFor(...).withHeader("Authorization", equalTo("Bearer test-access-token")))`.

### Wave 4: ErpCallbackClient

**Goal:** RestClient + Resilience4j @CircuitBreaker + @Retry. Comportamento de retry/fallback verificado.

**Tasks:**
- Criar `service/ErpCallbackClient.java` com RestClient + annotations.
- Tests `ErpCallbackClientTest` com WireMock:
  - happy path: POST → 200, no retry — counter == 1.
  - 5xx: 1ª e 2ª retornam 500, 3ª retorna 200 — counter == 3.
  - 5xx persistente: 3 falhas → fallback chamado, log.error captured (LogCaptor).
  - 4xx: POST → 400 → no retry, fallback chamado — counter == 1.
  - timeout (delay > callback-timeout): retry, eventualmente fallback — counter == 3.
  - circuit breaker reset entre tests via `circuitBreakerRegistry.find("erp-callback").get().reset()` em @BeforeEach.

**Commitable:** sim.

**Verification:**
- `./mvnw test -pl api-whatsapp -Dtest=ErpCallbackClientTest` 5+ tests verdes.

### Wave 5: MensagemAsyncListener + refactor MensagemService

**Goal:** Integracao do flow async + refactor do orquestrador.

**Tasks:**
- Editar `MensagemService.java`:
  - Remover `clienteZap.identificar()` + `clienteZap.atualizarUltimaMensagemEm()` do loop.
  - Adicionar `ApplicationEventPublisher` no constructor.
  - Trocar pelo `eventPublisher.publishEvent(new MensagemPersistidaEvent(...))` quando `novo == true`.
  - Anotar metodo com `@Transactional` (CRITICO — Pitfall 1).
- Criar `service/MensagemAsyncListener.java` com `@Async("whatsappTaskExecutor") @TransactionalEventListener(AFTER_COMMIT)`.
  - Sequencia: media download → identificar → atualizar timestamp → comando extraction → ErpCallbackClient.
  - Try/catch em cada step + log error sem propagar.
- Refatorar `MensagemServiceTest.java` (Phase 2 4 tests):
  - Mockar `ApplicationEventPublisher`.
  - Verify `publishEvent(any(MensagemPersistidaEvent.class))` para cada mensagem nova.
  - NAO chamar `clienteZap` nem `metaMediaClient` (esses sao do listener agora).
  - 4 tests adaptados.
- Criar `MensagemAsyncListenerTest.java` com mocks (sem Spring context):
  - listener com mediaId=null → media skip → identificar/atualizar/extract/callback chamados.
  - listener com mediaId presente → MetaMediaClient.baixar → bytes em base64 no DTO callback.
  - listener com mediaId mas 404 → DTO callback sem mediaBase64.
  - listener com tipo=desconhecido → comandoExtractor retorna null → callback NAO chamado.
  - listener com falha em identificar → return early; atualizar/callback NAO chamados.

**Commitable:** sim.

**Verification:**
- `./mvnw test -pl api-whatsapp -Dtest=MensagemServiceTest,MensagemAsyncListenerTest` verdes.

### Wave 6: Integration tests E2E + atualizar Phase 2 tests

**Goal:** Confirmacao empirica dos 5 ROADMAP success criteria de Phase 3.

**Tasks:**
- Criar `WebhookAsyncIntegrationTest.java`:
  - `@SpringBootTest` + `@Import(AsyncTestConfig.class)` (SyncTaskExecutor override).
  - `@AutoConfigureWireMock(port = 0)` ou WireMock manual com `@RegisterExtension`.
  - Testes:
    - **SC-1 ack-first timing:** WireMock para ERP delay 10s. POST webhook → assert response < 1000ms (TimedAssertion). Validar que ERP recebeu o callback eventualmente (poll ate 12s).
    - **SC-2 ErpCallback payload:** webhook text "orcamento 1234" → assert WireMock recebeu POST com body JSON contendo `comando: "orcamento"` + `telefone: "554784178525"`.
    - **SC-3 callback timeout/error:** WireMock retorna 500 sempre — assert webhook ainda retorna 200 + log.error capturado.
    - **SC-4 media download:** webhook document type → WireMock para Meta v22 retorna URL → bytes; assert WireMock para ERP recebeu callback com `mediaBase64` populated.
    - **SC-5 idempotency duplicate:** 2 POSTs mesmo wamid → assert ERP WireMock recebeu apenas 1 callback (counter == 1).
  - Helper `computeSignature(byte[], appSecret)` reusado de WebhookPersistenciaIntegrationTest.
- Atualizar `WebhookPersistenciaIntegrationTest.java` (Phase 2):
  - Adicionar `@Import(AsyncTestConfig.class)` + WireMock stub para ERP callback (responde 200 sempre).
  - Tests Phase 2 (SC-1..SC-5) continuam verdes — pre-condicao para closeout Phase 3.
- Configurar test profile com WireMock URL para `app.modulos.whatsapp.erp-callback-url` (porta dinamica do WireMock).

**Commitable:** sim — phase gate.

**Verification:**
- `./mvnw verify -pl api-whatsapp -am` BUILD SUCCESS.
- Total: 183 tests Phase 2 + ~20 novos Phase 3 = ~200+ tests.
- Anti-pattern grep: `grep -r "this.despachar\|this.aoMensagemPersistida" src/main/java` → 0 (no self-call AOP killer).

## Test Strategy

### Unit Test Structure
- **ComandoExtractorTest** (Wave 2) — JUnit puro, sem Spring.
- **MensagemAsyncListenerTest** (Wave 5) — `@ExtendWith(MockitoExtension.class)`, mocks de MetaMediaClient, ClienteZapService, ComandoExtractor, ErpCallbackClient.

### Integration Test Structure (WireMock)
- **MetaMediaClientTest** (Wave 3) — `@SpringBootTest` + WireMock para Meta Graph (porta dinamica). Override `https://graph.facebook.com/v22.0` com WireMock URL via `@DynamicPropertySource` ou subclass com URL configuravel.
- **ErpCallbackClientTest** (Wave 4) — `@SpringBootTest` + WireMock para ERP. Override `app.modulos.whatsapp.erp-callback-url`.
- **WebhookAsyncIntegrationTest** (Wave 6) — `@SpringBootTest` + WireMock para Meta + ERP + SyncTaskExecutor override.

### MetaMediaClient base URL configuravel para tests
Como `MetaMediaClient` hard-coda `https://graph.facebook.com/v22.0`, teste precisa override. Opcoes:

**Opcao A (recomendada):** Adicionar property `app.modulos.whatsapp.metaApiBaseUrl` (default `https://graph.facebook.com/v22.0`) em `WhatsAppProperties`. Em test profile/test class, override com WireMock URL via `@DynamicPropertySource`.

```java
// MetaMediaClient.java — change
public MetaMediaClient(WhatsAppProperties properties) {
    this.properties = properties;
    this.restClient = RestClient.builder()
            .baseUrl(properties.getMetaApiBaseUrl())  // <-- nova property
            .build();
}

// WhatsAppProperties.java — add
private String metaApiBaseUrl = "https://graph.facebook.com/v22.0";
public String getMetaApiBaseUrl() { return metaApiBaseUrl; }
public void setMetaApiBaseUrl(String metaApiBaseUrl) { this.metaApiBaseUrl = metaApiBaseUrl; }

// MetaMediaClientTest.java
@DynamicPropertySource
static void overrideUrl(DynamicPropertyRegistry registry) {
    registry.add("app.modulos.whatsapp.metaApiBaseUrl", () -> "http://localhost:" + wiremock.port());
}
```

**Opcao B:** Hard-coded URL ainda; usar Mockito spy + ReflectionTestUtils para sobrescrever `restClient` field em test. Mais fragil.

Decisao: **Opcao A** — pequena adicao em WhatsAppProperties (sem `@NotBlank`, default valido) + reuso em produc + test sem hack.

### SC-1 timing assertion exemplo

```java
@Test
void sc1_ack_first_retorna_200_em_menos_de_1s_mesmo_com_erp_lento() throws Exception {
    // ERP demora 10s — WireMock delay
    wireMockServer.stubFor(post("/api/modulos/whatsapp/comando")
        .willReturn(aResponse().withStatus(200).withFixedDelay(10000)));

    long start = System.currentTimeMillis();
    mockMvc.perform(post("/webhook/whatsapp")
            .header("X-Hub-Signature-256", "sha256=" + computeSignature(body, appSecret))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk());
    long elapsed = System.currentTimeMillis() - start;

    assertThat(elapsed)
        .as("Ack-first: 200 ao Meta deve retornar em <1s mesmo com ERP delay 10s")
        .isLessThan(1000);
    // listener async ainda esta pendente — em test SyncTaskExecutor isso seria sincrono
    // entao precisa AsyncTaskExecutor real para este test especifico.
    // OU: usar Awaitility para verificar que callback eventualmente chegou.
}
```

**CUIDADO:** SC-1 testa **timing** — exige executor REAL (nao SyncTaskExecutor). Decisao para WebhookAsyncIntegrationTest:
- 5 tests dos 5 SCs:
  - SC-1 (timing): usa executor real (pool de prod) → assertion timing + Awaitility para callback.
  - SC-2..SC-5 (funcional): usa SyncTaskExecutor via `@Import(AsyncTestConfig.class)`.

Implementacao: 2 nested classes ou 2 test classes separadas (uma com `@Import`, outra sem).

### Phase 2 tests existentes — atualizar

**WebhookPersistenciaIntegrationTest** precisa:
1. WireMock stub para ERP callback (porta configurada via property).
2. `@Import(AsyncTestConfig.class)` para SyncTaskExecutor.

Sem essas mudancas, tests SC-3/SC-4/SC-5 que verificam `clienteZap.ultimaMensagemEm` IS NOT NULL podem falhar pois listener async nao rodou ainda → `atualizarUltimaMensagemEm` nao executou.

Com SyncTaskExecutor + AFTER_COMMIT: quando `mensagemService.processarWebhook` retorna, transacao commit → AFTER_COMMIT dispara → SyncTaskExecutor.execute(task) inline → listener roda → atualizarUltimaMensagemEm chamado → DB updated → assertions passam.

**MensagemServiceTest** (4 tests Phase 2):
- Refatorar para mockar `ApplicationEventPublisher`.
- Tests viram: "publishEvent foi chamado N vezes para N mensagens novas".
- Tests Phase 2 que verificavam estado de `clientes_zap` movem para `MensagemAsyncListenerTest` (com mocks de ClienteZapService).

## Runtime State Inventory

> Phase 3 e refactor de codigo dentro de api-whatsapp. Sem rename/migracao de strings persistidas.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — nenhum schema novo, nenhum dado existente movido. mensagens_log + clientes_zap intactos. | Nenhuma. |
| Live service config | None — Cloudflare Tunnel, ERP-MUDAS endpoint nao mudam. | Nenhuma. |
| OS-registered state | None — Windows Service WinSW config nao muda. | Nenhuma. |
| Secrets/env vars | None novos. WHATSAPP_ACCESS_TOKEN ja usado por Phase 1 (HMAC) e agora por MetaMediaClient (Bearer). Nome do env var igual. | Nenhuma. |
| Build artifacts | api-whatsapp.jar mudara de tamanho (Resilience4j + AOP + WireMock test deps adicionadas). Em deploy, instalador WinSW reinstala JAR — sem acao manual. | Nenhuma — release.sh ja gera JAR completo. |

**Verificado:** None. Phase 3 nao tem state legacy para migrar.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 21 | api-whatsapp build | ✓ | 21.0.10 | — |
| Maven Wrapper | build | ✓ | 3.9.x | — |
| PostgreSQL 5433 | runtime (Phase 2 ja usa) | ✓ | 15 | H2 in-memory em test profile |
| H2 (test scope) | unit/integration tests | ✓ (Phase 1) | 2.3.232 | — |
| Spring Boot 3.5.9 | classpath | ✓ (parent BOM) | 3.5.9 | — |
| Resilience4j 2.2.0 | annotations | ✓ (parent dep mgmt) | 2.2.0 | — |
| WireMock 3.10.0 | test scope | ✗ (a adicionar Wave 1) | 3.10.0 | — |
| graph.facebook.com | runtime media download | external | v22.0 | 404 graceful + skip media |
| ERP-MUDAS localhost:8090 | runtime callback | external | n/a | Resilience4j fallback (log error) |
| Internet access | runtime para Meta API | external | n/a | Sem fallback — modulo requer conectividade |

**Missing dependencies with fallback:** None bloqueador. WireMock e adicao trivial em pom.xml.

**Internet/Meta:** sob restricao operacional, instancia api-whatsapp REQUER conectividade saida para `graph.facebook.com` em produc. Falta de internet = Phase 3 listener sempre falha em media download. Aceitavel — operadores sabem.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Mockito + Spring Boot Test + WireMock 3.10.0 |
| Config file | api-whatsapp/pom.xml + application-test.yml |
| Quick run command | `./mvnw test -pl api-whatsapp -Dtest=ComandoExtractorTest` |
| Full suite command | `./mvnw verify -pl api-whatsapp -am` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ROU-01 | publishEvent + listener async dispatch | unit + integration | `pytest`-equiv: `./mvnw test -pl api-whatsapp -Dtest=MensagemServiceTest,MensagemAsyncListenerTest,WebhookAsyncIntegrationTest` | ❌ Wave 5+6 |
| ROU-02 | POST callback payload format | integration | `./mvnw test -pl api-whatsapp -Dtest=ErpCallbackClientTest,WebhookAsyncIntegrationTest` | ❌ Wave 4+6 |
| ROU-03 | CB + retry 3x backoff | integration | `./mvnw test -pl api-whatsapp -Dtest=ErpCallbackClientTest` | ❌ Wave 4 |
| ROU-04 | timeout 5s + log error sem trava | integration | `./mvnw test -pl api-whatsapp -Dtest=ErpCallbackClientTest` | ❌ Wave 4 |
| ROU-05 | media 2-step download | integration | `./mvnw test -pl api-whatsapp -Dtest=MetaMediaClientTest,WebhookAsyncIntegrationTest` | ❌ Wave 3+6 |
| SC-1 | <1s ack timing | integration | `./mvnw test -pl api-whatsapp -Dtest=WebhookAsyncIntegrationTest#sc1*` | ❌ Wave 6 |
| SC-2 | callback payload | integration | idem `#sc2*` | ❌ Wave 6 |
| SC-3 | timeout/error log | integration | idem `#sc3*` | ❌ Wave 6 |
| SC-4 | media base64 callback | integration | idem `#sc4*` | ❌ Wave 6 |
| SC-5 | duplicate dispatch == 1 | integration | idem `#sc5*` | ❌ Wave 6 |

### Sampling Rate

- **Per task commit:** `./mvnw test -pl api-whatsapp -Dtest={TestClass}` — wave especifico (~5-30s).
- **Per wave merge:** `./mvnw verify -pl api-whatsapp -am` — full suite (~2-3min com ~200 tests).
- **Phase gate:** Full suite green + grep anti-pattern + manual validation Phase 2 tests passam.

### Wave 0 Gaps

- [ ] `api-whatsapp/pom.xml` — adicionar resilience4j-spring-boot3 + spring-boot-starter-aop + wiremock-standalone (Wave 1)
- [ ] `pom.xml` parent — adicionar resilience4j-spring-boot3 ao dependencyManagement (Wave 1)
- [ ] `api-whatsapp/src/main/resources/application.yml` — bloco resilience4j + spring.http.client (Wave 1)
- [ ] `api-whatsapp/src/main/java/.../config/AsyncConfig.java` — novo (Wave 1)
- [ ] `api-whatsapp/src/test/java/.../service/AsyncTestConfig.java` — `@TestConfiguration` (Wave 6)
- [ ] WireMock setup helpers para integration tests (Wave 3+4+6)

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | sim — API key (Phase 1) ja em endpoints internos; webhook publico via HMAC | ApiKeyFilter (lib-shared); HmacSignatureFilter (Phase 1) |
| V3 Session Management | nao | — |
| V4 Access Control | parcialmente — webhook e publico (validado por HMAC); endpoints internos protegidos por API key | Phase 1 SecurityConfig |
| V5 Input Validation | sim — payload Meta + payload ERP callback | Jackson com @JsonIgnoreProperties(ignoreUnknown=true); fields opcionais tratados; ComandoExtractor sanitiza inputs |
| V6 Cryptography | sim — HMAC-SHA256 para webhook (Phase 1); Bearer token nunca hand-rolled | MessageDigest.isEqual (constant-time) — Phase 1; Bearer accessToken via Authorization header (nao query param) — Phase 3 |
| V8 Data Protection | sim — accessToken/appSecret/verifyToken sao secrets | WhatsAppProperties.toString() mascara (Phase 1); logging.level org.springframework.web=INFO (nao DEBUG) |
| V11 Communications | sim — egress para graph.facebook.com (TLS) e para ERP localhost (HTTP ok pois loopback) | RestClient default segue HTTPS para Meta; ERP em localhost dispensa TLS |
| V12 Files and Resources | parcialmente — bytes da media em memoria (~10MB); base64 → +33% size | Validacao implicita via Spring boundary; Phase 6 pode adicionar size cap |

### Known Threat Patterns for {Spring Boot 3.5 + Resilience4j + RestClient}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| accessToken vazado em logs | Information Disclosure | Bearer no header (PITFALLS C-09 + C-14); WhatsAppProperties.toString() mascara; org.springframework.web=INFO |
| ERP callback url externa (configurada por instalador) | Spoofing | erpCallbackUrl em WhatsAppProperties — fail-fast no boot via @NotBlank (Phase 1 ja); operador usa `http://localhost:8090` por convencao |
| Bytes de media maliciosa (zip-bomb, malformed PDF) | Denial of Service | Phase 3 nao processa bytes — apenas passa base64 ao ERP. ERP responsavel por sanitizar. Phase 6 pode adicionar file_size cap (Meta retorna em metadata). |
| Webhook payload spoofed | Spoofing | HMAC validation Phase 1 (ja entregue) — pre-condicao para Phase 3. |
| Replay attack (mesmo wamid) | Replay | UNIQUE wamid Phase 2 — mesma defesa em Phase 3. |
| Command injection via comando | Injection | ComandoExtractor lowercase + sanitize — caracteres como `|`, espaco, `\n` filtrados via `split("\\s+")[0]`. |
| 4xx categorico retried causa duplicate | (operational) | retry-exceptions explicit em yml — apenas 5xx/timeout/IOException retentam. |
| Media URL leakage | Information Disclosure | URL temporaria nao logada; bytes baixados em memoria; nao persistidos em filesystem (Phase 3 NAO armazena). |

## Risks / Open Questions

### A1 (HIGH): publishEvent fora de transacao silently descarta listener

**What we know:** Spring docs explicitos: `If no transaction is running, the listener is not invoked at all`. Em Phase 2, `MensagemService.processarWebhook` NAO tem `@Transactional` na classe (preserva proxy AOP cross-bean).

**What's unclear:** Se `IdempotencyService.tentarPersistir` retorna, a transacao do save() ja committou — e publishEvent no MensagemService roda APOS o save commit, mas o codigo ainda esta dentro do callstack do `processarWebhook`. Precisa transacao ATIVA quando publishEvent acontece.

**Recommendation:** Anotar `MensagemService.processarWebhook` com `@Transactional` em Phase 3. Tradeoff: evita o silent-discard. Risk: PITFALLS Phase 2 mencionou "MensagemService nao tem @Transactional para preservar AOP cross-bean" — mas esse cuidado e sobre **classes destino** (`@Transactional` na classe que define o REQUIRES_NEW). MensagemService e CALLER, nao callee.

**Validation in Wave 5:** smoke test que aciona o flow completo + Mockito spy em MensagemAsyncListener para verificar invocation. Sem `@Transactional`, listener nao roda.

[VERIFIED: docs.spring.io transaction event reference]

### A2 (MEDIUM): SyncTaskExecutor + AFTER_COMMIT timing em test

**What we know:** SyncTaskExecutor executa `task.run()` inline. AFTER_COMMIT dispara apos commit do save. Em test sem `@Transactional`, save de `IdempotencyService` commit independente de cada repository.save().

**What's unclear:** Quantos commits acontecem? `repository.save()` commit imediato. Quando o loop do `processarWebhook` faz 2 saves (2 mensagens), 2 commits acontecem; cada commit dispara o AFTER_COMMIT respectivo. Listener roda inline (SyncTaskExecutor) — quando processarWebhook retorna, ambos listeners ja rodaram. **Em PRINCIPIO, isso funciona.**

**Risk:** se `processarWebhook` for `@Transactional` (per A1), TODOS os saves estao dentro de UMA transacao + 1 commit ao return. Listener dispara apos esse 1 commit, processando todos os events que estavam pendentes na fila do TransactionalEventListener (Spring buffers events ate commit).

**Recommendation:** Wave 5+6 inclui smoke test explicito que confirma DB state apos `processarWebhook` retornar (com SyncTaskExecutor + @Transactional). Empirico, nao teorico.

### A3 (MEDIUM): Resilience4j circuit breaker shared state entre tests

**What we know:** `circuitBreakerRegistry` e bean Singleton. Entre tests do mesmo SpringContext, state persiste.

**Recommendation:** Em `ErpCallbackClientTest.@BeforeEach`, reset:

```java
@Autowired CircuitBreakerRegistry registry;

@BeforeEach
void resetCB() {
    registry.find("erp-callback").ifPresent(CircuitBreaker::reset);
}
```

### A4 (LOW): WireMock setup com porta dinamica

**What we know:** WireMock standalone supports porta dinamica via `wireMockConfig().dynamicPort()`. Spring Boot test pode injetar via `@DynamicPropertySource`.

**Recommendation:** Padrao do codebase Phase 2 nao tem WireMock. Phase 3 introduz. Usar `@RegisterExtension` JUnit 5 + WireMockExtension (sem Spring port property) OU `WireMockServer` programatico com `@DynamicPropertySource` para sobrescrever `app.modulos.whatsapp.erp-callback-url` e `app.modulos.whatsapp.metaApiBaseUrl`.

[CITED: wiremock.org/docs/spring-boot] Library `wiremock-spring-boot` (org.wiremock.integrations) tem `@EnableWireMock + @InjectWireMock` mas adiciona dep extra. Para Phase 3, manter dependencia minima — apenas `wiremock-standalone` + manual setup.

### A5 (LOW): Bearer token mascarado em logs do RestClient

**What we know:** Spring 6 RestClient nao loga headers por default em INFO. `org.springframework.web=INFO` (Phase 1 application.yml) suficiente.

**Recommendation:** Sem acao adicional em Phase 3. Phase 6 pode adicionar `ClientHttpRequestInterceptor` mascarando Authorization se algum dia DEBUG for habilitado. Nao critico para Phase 3.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `MensagemService.processarWebhook` sem `@Transactional` causa silent-discard de AFTER_COMMIT listener | §"Pattern 1 ack-first" + §"Risks A1" | HIGH — sem `@Transactional`, listener nunca dispara em runtime; dev nota apenas em integration test |
| A2 | SyncTaskExecutor + `@Transactional` em test method NAO funcionam juntos (commit fora) | §"Pitfall 3" | MEDIUM — tests podem ficar flaky se test method e `@Transactional` |
| A3 | WireMock 3.10.0 funciona com Spring Boot 3.5.9 sem conflito Jetty | §"Standard Stack" | LOW — alternativa wiremock-jetty12 disponivel |
| A4 | property-based override de TaskExecutor nao existe em Spring Boot 3.5 | §"Test profile override" | LOW — alternativa @TestConfiguration funciona |
| A5 | Default `SimpleClientHttpRequestFactory` connect/read timeout INFINITO sem `spring.http.client.*` props | §"MetaMediaClient" + Code Example 6 | LOW — adicionado spring.http.client.connect-timeout=5s + read-timeout=10s defesa em profundidade |
| A6 | Resilience4j sem `spring-boot-starter-aop` causa annotations no-op SILENCIOSO (sem warning) | §"Pitfall 2" | HIGH — bug grave silencioso; mitigado por Wave 4 test que valida retry counter |
| A7 | RestClient com `byte[].class` body retorna bytes raw sem conversao Jackson | §"MetaMediaClient step 2" | LOW — Spring 6 RestClient suporta byte[] como body type via ByteArrayHttpMessageConverter (ja registrado por default em spring-boot-starter-web) |

**Conclusao:** A1 e A6 sao HIGH e devem ter teste explicito em Wave 5/Wave 4. Demais sao mitigaveis via design ja recomendado.

## Open Questions

1. **`MensagemService.processarWebhook` deve abrir 1 transacao para todo o loop, ou 1 transacao por mensagem?**
   - What we know: Phase 2 funciona com 1 transacao por save() (default Spring Data). publishEvent fora de transacao silencia.
   - What's unclear: `@Transactional` no metodo abre 1 grande transacao — todos saves + publishEvents bufferados, commit ao retornar.
   - Recommendation: 1 transacao por metodo (`@Transactional` na assinatura). Vantagem: comportamento atomico em respeito a falhas mid-loop. Desvantagem: rollback de erro em mensagem 5/10 desfaz mensagens 1-4. Trade-off: assumir que mensagens 1-4 ja foram persistidas no commit anterior. **Como cada mensagem entra com `tentarPersistir` que ja faz catch DataIntegrityViolationException, raramente erros mid-loop. Aceitavel.**

2. **Listener async deve usar @Transactional propria?**
   - What we know: Listener roda em outra thread (whatsappTaskExecutor). Nao herda transacao do publisher.
   - What's unclear: `clienteZapService.identificar()` ja tem `@Transactional` interno; `atualizarUltimaMensagemEm` tem REQUIRES_NEW. Listener nao precisa propria.
   - Recommendation: NAO anotar `MensagemAsyncListener.aoMensagemPersistida` com `@Transactional`. Cada chamada interna abre sua propria. Mantem isolamento.

3. **Como assert "primeira acao" do listener e media download?**
   - What we know: Sequencia esta clara no codigo.
   - What's unclear: Como **testar empiricamente** que media download nao foi atrasado por outras coisas?
   - Recommendation: Test unit com Mockito InOrder verifying ordem de invocations: metaMediaClient → clienteZapService → comandoExtractor → erpCallbackClient. Sem dependencia de timing real.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| RestTemplate | Spring 6 RestClient | Spring 6.1 (2023) | RestClient e fluent API; recomendado para novo codigo. lib-consultas-client e legacy. |
| Programmatic Resilience4j (decorateSupplier) | Annotation-driven (`@CircuitBreaker @Retry`) | Resilience4j 2.x (2023+) | Annotations exigem AOP starter mas codigo mais limpo. |
| `@TransactionalEventListener` syncrono | + `@Async` em outra thread | Spring 5+ | `@Async` libera thread do tx para responder ack rapido. |
| `ContentCachingRequestWrapper` | Custom `CachedBodyHttpServletRequest` | (sempre) | PITFALLS C-02 — Spring built-in nao cacheia eager. |
| WireMock 2.x | WireMock 3.x + Jetty 12 (standalone shadow) | WireMock 3.5.2+ (2024) | Jetty 11 conflita com Spring Boot 3.x; Jetty 12 ou standalone evita. |

**Deprecated/outdated:**
- `RestTemplate` para novo codigo: nao deprecated mas Spring docs marca para minor maintenance. lib-consultas-client mantem por idade. Phase 3 e novo → RestClient.
- `@EnableAsync` em main app class: ainda valido, mas convencao e `@Configuration` dedicada (AsyncConfig).

## Sources

### Primary (HIGH confidence)

- [VERIFIED: codebase] `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/ConsultasClientImpl.java` — pattern Resilience4j programmatic (referencia para annotations).
- [VERIFIED: codebase] `lib-consultas-client/pom.xml` — deps Resilience4j confirmadas (parent provee `${resilience4j.version}`).
- [VERIFIED: codebase] `pom.xml` parent linha 33 — `<resilience4j.version>2.2.0</resilience4j.version>`.
- [VERIFIED: codebase] `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MensagemService.java` (Phase 2) — current sync orchestrator, baseline para refactor.
- [VERIFIED: codebase] `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/IdempotencyService.java` (Phase 2) — boolean novo gate, reusado.
- [VERIFIED: codebase] `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/ClienteZapService.java` (Phase 2) — REQUIRES_NEW pattern preservado.
- [VERIFIED: codebase] `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/WhatsAppProperties.java` (Phase 1) — accessToken + erpCallbackUrl + callbackTimeout — usado por Phase 3.
- [CITED] `.planning/phases/03-roteamento-boundary-async/03-CONTEXT.md` — D-01..D-08 (locked decisions).
- [CITED] `.planning/research/PITFALLS.md` §C-05/C-08/C-09/C-14 — async + 5min media + Bearer leak.
- [CITED] `.planning/research/ARCHITECTURE.md` §"Pattern 1" + §"Component Responsibilities".
- [CITED] `.planning/REQUIREMENTS.md` §"Roteamento" — ROU-01..05.
- [CITED] `.planning/ROADMAP.md` Phase 3 — 5 SC.
- [CITED] [Spring Framework reference — Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html) — AFTER_COMMIT semantics; "If no transaction is running, the listener is not invoked at all".
- [CITED] [Spring Framework Javadoc — TransactionalEventListener](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html) — fallbackExecution flag.
- [CITED] [Resilience4j docs — Getting Started](https://resilience4j.readme.io/docs/getting-started-3) — Spring Boot 3 starter.
- [CITED] [Meta — Media API reference](https://developers.facebook.com/docs/whatsapp/cloud-api/reference/media/media-api) — 2-step download + Bearer header.
- [CITED] [Meta — Authorization Tokens blog](https://developers.facebook.com/blog/post/2022/12/05/auth-tokens/) — Bearer pattern.
- [CITED] [SimpleClientHttpRequestFactory Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/client/SimpleClientHttpRequestFactory.html) — timeout config.

### Secondary (MEDIUM confidence)

- [WebSearch verified] [Baeldung — Resilience4j with Spring Boot](https://www.baeldung.com/spring-boot-resilience4j) — annotations + AOP requirement.
- [WebSearch verified] [oneuptime.com — Circuit Breaker with Resilience4j 2026](https://oneuptime.com/blog/post/2026-02-01-spring-resilience4j-circuit-breaker/view) — Aspect order Retry(CB).
- [WebSearch verified] [Medium @ibrahimgunduz34 — RestClient global config](https://ibrahimgunduz34.medium.com/how-to-configure-global-settings-for-spring-restclient-620b10772e93) — `spring.http.client.*` props.
- [WebSearch verified] [DZone — Spring Boot Timeout RestClient](https://dzone.com/articles/timeout-in-spring-boot-with-restclient-webclient-a) — Spring Boot 3.5.x.
- [WebSearch verified] [WireMock — Spring Boot Integration](https://wiremock.org/docs/spring-boot/) — Jetty 12 + standalone.
- [WebSearch verified] [WireMock — Jetty 12 docs](https://wiremock.org/docs/jetty-12/) — modular vs standalone.
- [WebSearch verified] [Medium @shreyas.sreedhar — Downloading WhatsApp media](https://medium.com/@shreyas.sreedhar/downloading-media-using-whatsapps-cloud-api-webhooks-and-uploading-it-to-aws-s3-bucket-via-nodejs-07c5cbae896f) — 2-step pattern node.js exemplo.
- [WebSearch verified] [DZone — Transaction Synchronization @TransactionalEventListener](https://dzone.com/articles/transaction-synchronization-and-spring-application) — async + AFTER_COMMIT pattern.

### Tertiary (LOW confidence — needing validation)

- [ASSUMED] `wiremock-standalone` 3.10.0 e a versao mais recente estavel que nao quebra com Spring Boot 3.5.9. Recomendado: validar versao em mvnrepository.com antes de Wave 1 commit. Fallback: 3.8.1 (mencionado em QA-02 da Phase 6).
- [ASSUMED] `@TransactionalEventListener(AFTER_COMMIT)` em metodo `@Async` do bean — comportamento esperado e: Spring publica events em buffer durante transacao; commit dispara TransactionSynchronization.afterCommit(); afterCommit() chama listener via `@Async` mecanismo (via `whatsappTaskExecutor.execute`). Em production threading, listener roda em outra thread; em test SyncTaskExecutor, inline. Exato timing nao documentado em detalhe; spike Wave 5 valida.
- [ASSUMED] `byte[].class` body em RestClient retorna bytes raw sem Jackson — funciona via ByteArrayHttpMessageConverter (Spring default). Validacao: Wave 3 MetaMediaClientTest happy-path.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — versoes confirmadas via parent pom.xml + WebSearch validacao.
- Architecture: HIGH — Pattern 1+2+3 sao standard Spring; lib-consultas-client e baseline empirico.
- Pitfalls: HIGH — Phase 2 Verification empirico + WebSearch confirma cada item.
- Test strategy: MEDIUM — SyncTaskExecutor + AFTER_COMMIT timing precisa de spike Wave 5/6 para validar.
- Risks A1/A6: HIGH severity, mitigado por Wave 5/Wave 4 teste explicito.

**Research date:** 2026-05-05
**Valid until:** 2026-06-05 (1 mes — Spring Boot e estavel; Resilience4j 2.2.0 sem release iminente; Meta Cloud API v22 estavel)
