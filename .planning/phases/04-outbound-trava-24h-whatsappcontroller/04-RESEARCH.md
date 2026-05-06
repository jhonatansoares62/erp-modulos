# Phase 4: Outbound + Trava 24h + WhatsAppController - Research

**Researched:** 2026-05-05
**Domain:** Spring Boot 3.5.9 outbound HTTP client + Spring AOP custom aspect + Jakarta validation + JPA cache + WhatsApp Cloud API v22.0
**Confidence:** HIGH

## Summary

Phase 4 entrega o caminho outbound completo do `api-whatsapp`: 4 metodos publicos no `WhatsAppCloudClient` (texto/documento/botoes/lista — sem `enviarTemplate`), trava hard de janela 24h via aspect `@JanelaProtegida` + `WindowEnforcementService`, cache de `media_id` com TTL estrito 30d, e 5 endpoints REST internos no `WhatsAppController`. Toda a infraestrutura de Resilience4j (`@CircuitBreaker` + `@Retry` annotation-driven), `RestClient`, AOP runtime (`spring-boot-starter-aop`), WireMock 3.10.0 e padroes de race-protection (save+catch `DataIntegrityViolationException`) ja foi validada empiricamente em Phase 2 e Phase 3 — Phase 4 reaproveita os patterns sem reabrir decisoes.

As 4 decisoes de implementacao estao locked em CONTEXT.md (D-01 JSON+base64 entre ERP e api-whatsapp; D-02 traducao Meta→ERP com `codigo` field; D-03 aspect via annotation `@JanelaProtegida` + telefone posicional `args[0]` + `@Order(HIGHEST_PRECEDENCE)`; D-04 MediaCache TTL estrito + `/status` minimal). User delegou as 4 decisoes a Claude apos ver o menu — todas reversiveis se atrito surgir. A pesquisa foca em **como implementar** dentro destas decisoes, nao em alternativas.

Risco residual: 1 area que ainda nao foi empiricamente provada neste codebase — a interacao do `@Order(HIGHEST_PRECEDENCE)` do aspect customizado com a cadeia ja-existente Retry+CircuitBreaker do Resilience4j Spring Boot starter. A pesquisa documental (web search da issue Resilience4j #2383) confirma que `HIGHEST_PRECEDENCE` no aspect customizado o coloca FORA do Retry — alinhado com D-03. Validacao empirica final acontece via test que conta `windowService.verificarJanela` invocations == 1 em scenario de 3 retries.

**Primary recommendation:** Reusar exatamente o pattern `ErpCallbackClient` (Phase 3 Plan 04) para o `WhatsAppCloudClient`, com 4 ajustes especificos: (1) Bearer header per-request em cada `restClient.post().header(AUTHORIZATION, "Bearer " + token)`; (2) `fallbackMethod` no `@Retry` (nao no `@CircuitBreaker`) — gotcha 03-04 ja resolvido; (3) annotation `@JanelaProtegida` em cada metodo publico com aspect `@Order(HIGHEST_PRECEDENCE)`; (4) instance Resilience4j dedicada `whatsapp-cloud` com `retry-exceptions` whitelist explicita incluindo `ResourceAccessException`. `MediaCacheService.registrarUpload` reusa o pattern save+catch de `IdempotencyService`. `MediaCacheRepository.findByArquivoHashAndExpiraEmAfter` ja existe (Phase 2).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01 — Contrato `enviar-documento`: JSON+base64 (NAO multipart/form-data) entre ERP e api-whatsapp.**
ERP envia bytes do PDF/arquivo como base64 dentro de JSON regular. Controller decodifica via `Base64.getDecoder().decode(req.mediaBase64())` para `byte[] bytes` antes de invocar `WhatsAppCloudClient.enviarDocumento(...)`. Multipart Meta-side e detalhe interno: `WhatsAppCloudClient.uploadMedia(byte[], mimeType, filename)` faz multipart para `graph.facebook.com/v22.0/{phoneNumberId}/media` internamente — ERP nunca ve multipart. Aceito trade-off de 33% de inflation base64 (PDF 10MB → ~13MB) — localhost loopback, irrelevante.

**D-02 — Mapeamento erro Meta → ERP traduzido com `codigo` field.**
| Cenario | Status HTTP | `codigo` no body |
|---------|-------------|------------------|
| Janela > 24h | 409 | `JANELA_24H_FECHADA` |
| Validation request (>3 botoes, >10 itens, telefone vazio, base64 invalido) | 400 | `VALIDATION_ERROR` |
| Meta 400/401/403 (categorico) | 422 | `META_ERROR` + `metaErrorCode` |
| Meta 5xx apos 3 retries | 502 | `META_INDISPONIVEL` + `metaErrorCode` |
| Timeout apos 3 retries | 504 | `META_TIMEOUT` |
| Circuit aberto | 503 | `CIRCUIT_OPEN` |

`MetaApiException extends ModuloException` carrega `metaErrorCode` (Integer) + `tipo` enum {CATEGORIA_4XX, INDISPONIVEL_5XX, TIMEOUT, CIRCUIT_OPEN}. `fallbackMethod` no `@Retry` converte `Throwable` em `MetaApiException` com tipo apropriado.

**D-03 — Aspect via annotation marker `@JanelaProtegida` + telefone posicional args[0] + `@Order(Ordered.HIGHEST_PRECEDENCE)`.**
Annotation `@interface JanelaProtegida { }` (sem atributos). Aspect `@Around("@annotation(...)")` le `args[0]` como `String telefone` (convencao posicional), invoca `WindowEnforcementService.verificarJanela(telefone)` antes de `pjp.proceed()`. Order HIGHEST_PRECEDENCE crucial: garante 1 check por chamada (antes do Retry — nao 3x). Validacao em test via Mockito counter.

**D-04 — MediaCacheService TTL estrito 30d sem sliding + Status endpoint minimal.**
`buscarMediaId(byte[] bytes)`: sha256 → `findByArquivoHashAndExpiraEmAfter(hash, Instant.now())` (filtra expirados no banco). `registrarUpload(bytes, mediaId)`: save com `expira_em = now() + 30d`; race protection via try/catch `DataIntegrityViolationException`. TTL estrito (sem sliding) — turnover natural de 30 em 30 dias. `StatusResponse{status, circuitBreakerState, phoneNumberId}` minimo; expansao Phase 6.

### Claude's Discretion

User delegou todas as 4 areas para Claude apos ver o menu (mensagem literal: "pode implementar da melhor maneira que encontrar"). Decisoes acima sao defaults recomendados — todas reversiveis se a implementacao mostrar atrito. Areas de discricao livre dentro do escopo de Phase 4:
- Ordem dos plans / split de waves (RESEARCH §Volume estimate sugere 6-7 plans)
- Decisao tatica entre passar `byte[]` vs `byte[] + sha256` entre controller e service (ver §Specific Ideas em CONTEXT)
- Forma de validacao do total de itens em `EnviarListaRequest`: `@AssertTrue` em metodo `isTotalItensValido()` no record (recomendado — simples, sem classe extra) ou custom `@Constraint` separado. Pesquisa abaixo recomenda `@AssertTrue`.
- Modificacao do `ErrorResponse` em `lib-shared` para incluir campo `codigo` (necessario por D-02). Mudanca compativel — verificar antes de modificar.

### Deferred Ideas (OUT OF SCOPE)

- `lib-whatsapp-client` (LIB-01..08) — **Phase 5**
- README.md / RUNBOOK.md / SpringDoc OpenAPI exhaustivo (QA-04..06) — **Phase 6**
- `subscribed_apps` validation (PITFALLS C-12) no `/status` — **Phase 6**
- Categorizacao estruturada de erros Meta por `error.code` em metricas Micrometer — **v2 milestone**
- Volume metrics (mensagens in/out por dia, por tipo, por erro) Micrometer — **v2 milestone**
- Mark-as-read + typing indicator — **v2 milestone**
- Retencao automatica de mensagens_log >90 dias — **v2 milestone**
- E2E real com WABA do Meta — **milestone seguinte** (D7)
- Engate ERP-MUDAS — **outro repo, outro GSD project**
- Multipart no controller para `enviar-documento` — fallback se PDFs piloto MUDAS forem >15MB; por enquanto JSON+base64
- Meta error codes pass-through (sem traducao) — fallback se ERP tiver dificuldade
- Pointcut por nome em vez de annotation — fallback se aspect annotation tiver bug Spring AOP self-call
- Status endpoint richer — Phase 6 expansao
- Sliding TTL no MediaCache — fallback se reupload mensal causar custo Meta
- Persistencia de bytes da media outbound — fora do escopo
- Persistencia de statuses Meta (sent/delivered/read/failed) — backlog v2
- Maquina de estado complexa de conversa — D6 PROJECT.md proibe
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| OUT-01 | `WhatsAppCloudClient.enviarTexto(telefone, texto)` chama `POST graph.facebook.com/v22.0/{phoneNumberId}/messages` com `messaging_product=whatsapp, type=text` via Spring `RestClient` | §Cloud API Payload Reference §1; §Code Examples §1.1 |
| OUT-02 | `enviarDocumento(telefone, bytes, filename, mimeType, caption)` faz upload via `POST /{phoneNumberId}/media` (multipart) entao envia `type=document` referenciando `media_id` retornado | §Cloud API Payload Reference §2 + §Multipart Upload; §Code Examples §1.2 + §3 |
| OUT-03 | `enviarBotoes(telefone, texto, botoes)` envia `type=interactive` com `interactive.type=button` (ate 3 botoes) — falha early se >3 | §Cloud API Payload Reference §3; §Code Examples §1.3; §Validation Bean §1 |
| OUT-04 | `enviarLista(telefone, texto, secoes)` envia `type=interactive` com `interactive.type=list` (ate 10 itens totais) — falha early se >10 | §Cloud API Payload Reference §4; §Code Examples §1.4; §Validation Bean §2 |
| OUT-05 | NAO existir `enviarTemplate(...)` — trava custo zero #1 garantida por ausencia | §Standard Stack — sem dep alguma de template; gate de grep documentado em §Common Pitfalls |
| OUT-06 | `WindowEnforcementService.verificarJanela(telefone)` consulta `ultima_mensagem_em` via query direta (pula JPA cache) FORA da transacao do webhook — diff > 24h lanca `JanelaConversaFechadaException` → 409 `JANELA_24H_FECHADA` | §Architecture Patterns §2; §Code Examples §2; PITFALLS C-01 |
| OUT-07 | Antes de cada chamada Cloud API, hook `@Aspect` invoca verificacao — interceptor inviolavel via aspect, nao dependendo de cada metodo lembrar | §Architecture Patterns §3; §Aspect Order Investigation; D-03 (CONTEXT) |
| OUT-08 | `MediaCacheService` — sha256(bytes), busca em `media_cache.arquivo_hash`. Hit (nao expirado) → reusa media_id. Miss → upload + grava com `expira_em = now() + 30d` | §Architecture Patterns §4; §Code Examples §4; PITFALLS C-07; D-04 (CONTEXT) |
| OUT-09 | Persiste mensagem de saida em `mensagens_log` com `direcao=out` + `wamid` retornado pelo Meta apos sucesso | §Code Examples §5; reusa `MensagemLogRepository.save` Phase 2 |
| OUT-10 | 4xx categoricos (400/401/403) NAO retentar, logar `meta_error_code`. 5xx + timeout: Resilience4j retry exponencial 3x (1s/2s/4s) | §Standard Stack §Resilience4j config; §Common Pitfalls §3 |
| OUT-11 | Endpoints internos: `POST /api/whatsapp/enviar-{texto,documento,botoes,lista}` + `GET /api/whatsapp/status` — todos delegam pro client com trava 24h aplicada | §Code Examples §6; §Architecture Patterns §5 |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Recepcao request ERP (5 endpoints) | API/Backend (`WhatsAppController`) | — | REST publico interno; valida payload, decodifica base64, delega a service |
| Validacao request (telefone, base64, max botoes/itens) | API/Backend (DTOs com Jakarta Validation) | API/Backend (`@AssertTrue` em record para listas) | Bean Validation no entry-point; falha early antes de qualquer I/O |
| Trava 24h (cross-cutting) | API/Backend (`JanelaEnforcementAspect` + `WindowEnforcementService`) | Database/Storage (native query em `clientes_zap`) | Aspect AOP intercepta antes de retries; query nativa skip JPA L1 cache |
| Resolucao de media_id por sha256 | API/Backend (`MediaCacheService`) | Database/Storage (`media_cache` table) | Calcula hash, le DB com filtro de expiracao no banco; race via UNIQUE |
| Upload de media para Meta | API/Backend (`WhatsAppCloudClient.uploadMedia`) | External (Meta Graph API) | Multipart `RestClient` → `/v22.0/{phoneNumberId}/media`; Bearer per-request |
| Envio de mensagem outbound | API/Backend (`WhatsAppCloudClient.enviar*`) | External (Meta Graph API) | RestClient JSON → `/v22.0/{phoneNumberId}/messages`; Bearer per-request |
| Resiliencia (CB + Retry) | API/Backend (Resilience4j Spring AOP) | Config (`application.yml`) | Annotation-driven; instance dedicada `whatsapp-cloud` |
| Persistencia outbound | API/Backend (`MensagemLogRepository.save`) | Database/Storage (`mensagens_log`) | Direcao=out + wamid retornado pelo Meta; UNIQUE wamid global |
| Traducao erro Meta → HTTP ERP | API/Backend (`MetaApiException` + `GlobalExceptionHandler`) | lib-shared (`ErrorResponse` com campo `codigo`) | Fallback method converte Throwable; handler mapeia status+codigo |
| Status endpoint | API/Backend (`WhatsAppController.status`) | Resilience4j (`CircuitBreakerRegistry`) + Config (`WhatsAppProperties`) | Le state CB + phoneNumberId; minimal v1 |

**Por que esses tiers:** Phase 4 e 100% backend (Java) — nao ha frontend nem CDN. A unica fronteira externa e o Meta Cloud API. Trava 24h e arquitetonicamente cross-cutting (aspect) porque precisa rodar fora dos retries e ser inviolavel — colocar a checagem dentro de cada metodo publico do `WhatsAppCloudClient` violaria DRY e seria facil esquecer ao adicionar metodo novo.

## Standard Stack

### Core (ja no codebase, **NAO requer add**)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.5.9 | Framework runtime | Monorepo standard; reator inteiro `[VERIFIED: pom.xml root]` |
| Java | 21 | Language target | Monorepo standard `[VERIFIED: pom.xml]` |
| Spring `RestClient` | 6.x (transitive Boot) | HTTP client outbound | Modern blocking client (replaces `RestTemplate`); ja usado em `MetaMediaClient` (Phase 3) e `ErpCallbackClient` (Phase 3) `[VERIFIED: codebase]` |
| Spring Data JPA | 3.x (transitive Boot) | Repository abstraction | `MediaCacheRepository` ja existe Phase 2 `[VERIFIED: codebase]` |
| Resilience4j Spring Boot 3 | 2.2.0 | `@CircuitBreaker` + `@Retry` annotation-driven | Padrao Phase 3; risk A6 RESOLVED empiricamente `[VERIFIED: api-whatsapp/pom.xml]` |
| spring-boot-starter-aop | 3.5.9 | AOP runtime (aspectjweaver 1.9.25.1) | Adicionado em 03-01; necessario para Resilience4j annotation E para `JanelaEnforcementAspect` `[VERIFIED: api-whatsapp/pom.xml]` |
| spring-boot-starter-validation | 3.5.9 | Jakarta Bean Validation (Hibernate Validator) | `@Valid`, `@NotBlank`, `@Size`, `@AssertTrue` em DTOs `[VERIFIED: api-whatsapp/pom.xml]` |
| Jackson (transitive Boot) | 2.18.x | JSON serialization | Suporta records nativamente; pattern Phase 3 `[VERIFIED: codebase]` |
| WireMock standalone | 3.10.0 | Test double Cloud API | Validado em Phase 3 com Boot 3.5.9 `[VERIFIED: api-whatsapp/pom.xml + Phase 3 SUMMARY]` |

**Versoes verificadas via `pom.xml` direto. Sem necessidade de `npm view` equivalente — Maven dependencies pinned no parent + per-module.**

### Supporting (ja no codebase)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| H2 | 2.3.232 (test scope) | In-memory test DB | `MediaCacheServiceTest` race scenarios `[VERIFIED: pom.xml]` |
| awaitility | 4.x (test) | Async test assertions | Disponivel; nao essencial Phase 4 (Phase 3 ja usa) `[VERIFIED: pom.xml]` |
| spring-boot-starter-test | 3.5.9 (test) | Mockito + JUnit + AssertJ | `WhatsAppControllerTest` (`@WebMvcTest`), `MediaCacheServiceTest` (Mockito puro), `JanelaEnforcementAspectTest` `[VERIFIED: pom.xml]` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `RestClient` + `MultiValueMap` para multipart | `RestTemplate` + `HttpEntity<MultiValueMap>` | Funciona, mas inconsistente com Phase 3 (que usa `RestClient`); mantemos `RestClient` |
| `@AssertTrue` em metodo do record para validar total itens | Custom `@Constraint` + `ConstraintValidator<TotalItensValido, EnviarListaRequest>` | Custom mais verboso (3 arquivos) sem ganho; `@AssertTrue isXxx()` no record e idiomatico Spring 3 e suficiente para v1 `[CITED: jakarta.ee/specifications/bean-validation/3.0]` |
| Validacao de >3 botoes / >10 itens via `@AssertTrue` | `@Size(max=3)` em `List<BotaoDto>` (botoes) + `@AssertTrue` total itens em listas (porque listas tem secoes aninhadas) | Botoes: `@Size(max=3)` direto e mais simples e gera mensagem padrao melhor; lista exige `@AssertTrue` (soma cross-secoes). Hybrid recomendado |
| Aspect via `execution(* WhatsAppCloudClient.enviar*(..))` pointcut por nome | Annotation marker `@JanelaProtegida` (D-03 locked) | Locked em CONTEXT |
| Aspect com atributo `@JanelaProtegida(telefoneArgIndex = N)` | Convencao posicional `args[0]` (D-03 locked) | Locked em CONTEXT |
| Persistir `out` em transacao separada `REQUIRES_NEW` | `repository.save(MensagemLog)` em transacao corrente | Phase 2 PER-07 ja garantiu padrao implicito (Spring Data save atomico). Outbound nao precisa REQUIRES_NEW — nao alimenta a trava 24h (ele ja passou pela trava antes) |

**Installation:** Sem novas dependencias Maven. Phase 4 reusa 100% do que esta em `api-whatsapp/pom.xml` apos Phase 3.

**Version verification:**
```bash
# Sem npm equivalente — Maven dependencies pinned em pom.xml
./mvnw -pl api-whatsapp dependency:tree | grep -E '(resilience4j|aop|aspectjweaver|wiremock|spring-boot-starter)'
```
Esperado: `resilience4j-spring-boot3:2.2.0`, `aspectjweaver:1.9.25.1`, `spring-boot-starter-aop:3.5.9`, `wiremock-standalone:3.10.0`, `spring-boot-starter-validation:3.5.9`. Todas confirmadas em Phase 3 03-01 SUMMARY.

## Architecture Patterns

### System Architecture Diagram

```
                    [ERP-MUDAS / piloto]                                        [Meta Cloud API v22.0]
                            │                                                       ▲          ▲
                            │ JSON+base64 (D-01)                                    │          │ Bearer header
                            │ POST /api/whatsapp/enviar-{texto,documento,botoes,    │          │ per-request
                            │       lista}  +  GET /api/whatsapp/status             │          │ (PITFALLS C-09/C-14)
                            ▼                                                       │          │
                ┌────────────────────────┐                                          │          │
                │  WhatsAppController    │  HTTP entrypoint, @Valid + base64 decode │          │
                │  + 5 DTOs Request      │ ◄─── 400 VALIDATION_ERROR (Jakarta)      │          │
                │  + EnvioResponse       │ ◄─── 422 META_ERROR (4xx Meta)           │          │
                │  + StatusResponse      │ ◄─── 502 META_INDISPONIVEL (5xx Meta)    │          │
                └───────┬────────────────┘ ◄─── 504 META_TIMEOUT                    │          │
                        │                  ◄─── 503 CIRCUIT_OPEN                    │          │
                        │ Java direct call ◄─── 409 JANELA_24H_FECHADA (D-02)       │          │
                        ▼                                                           │          │
                ┌─────────────────────────────────────────────────────────┐         │          │
                │  @JanelaProtegida annotation (D-03)                     │         │          │
                │       ▼                                                 │         │          │
                │  JanelaEnforcementAspect @Order(HIGHEST_PRECEDENCE)     │ ── lanca JanelaConversaFechadaException
                │       │                                                 │         │          │
                │       ├─► WindowEnforcementService.verificarJanela      │         │          │
                │       │       │                                         │         │          │
                │       │       └─► native @Query SELECT ultima_mensagem  │         │          │
                │       │              FROM whatsapp.clientes_zap         │         │          │
                │       │              WHERE telefone = ?                 │         │          │
                │       │                                                 │         │          │
                │       ▼ (passou aspect — Resilience4j chain) │         │          │
                │  @CircuitBreaker(name="whatsapp-cloud") @Retry(...)     │         │          │
                │       ▼                                                 │         │          │
                │  WhatsAppCloudClient                                    │         │          │
                │       │                                                 │         │          │
                │       ├─► [enviarDocumento somente]                     │         │          │
                │       │     MediaCacheService.buscarMediaId(bytes)      │ ◄─── le media_cache.arquivo_hash
                │       │     │ HIT: reusa media_id                       │         │          │
                │       │     │ MISS:                                     │         │          │
                │       │     │   ├─ uploadMedia(bytes,mime,filename)     │ ────────┼──────────┘ POST /v22.0/{pn}/media multipart
                │       │     │   │   (messaging_product, file, type)     │         │
                │       │     │   ├─ MediaCacheService.registrarUpload()  │ ◄─── INSERT media_cache (race via UNIQUE)
                │       │     │   └─ retorna media_id novo                │         │
                │       │                                                 │         │
                │       ├─► RestClient.post()                             │ ────────┘ POST /v22.0/{pn}/messages JSON
                │       │     .header(AUTHORIZATION, "Bearer "+token)     │
                │       │     .body(payloadJson)                          │ ◄─── retorna {messages: [{id: wamid}]}
                │       │                                                 │
                │       ├─► MensagemLogRepository.save(direcao=out, wamid)│ ─── INSERT mensagens_log
                │       │                                                 │
                │       └─► retorna EnvioResponse{wamid}                  │
                │                                                         │
                │   [fallbackMethod no @Retry — converte Throwable]       │
                │   - HttpClientErrorException → MetaApiException(4XX)    │
                │   - HttpServerErrorException (apos retries) → MetaApiException(5XX)
                │   - ResourceAccessException → MetaApiException(TIMEOUT) │
                │   - CallNotPermittedException → MetaApiException(CIRCUIT_OPEN)
                └─────────────────────────────────────────────────────────┘

ERROR FLOW (GlobalExceptionHandler em lib-shared):
  ModuloException → ErrorResponse{status, erro, mensagem, timestamp, campos?, codigo?}
                    HTTP status conforme exception.getStatus()
```

Componentes existentes (Phase 1-3) **reaproveitados sem modificacao**:
- `WhatsAppProperties` (phoneNumberId, accessToken, metaApiBaseUrl) — config
- `MensagemLogRepository.save` (direcao=out)
- `MediaCacheRepository.findByArquivoHashAndExpiraEmAfter`
- `ApiKeyFilter` (registra `/api/whatsapp/*` exigindo X-API-Key)
- Padrao `@SpringBootTest(classes=WhatsAppApplication.class)` + WireMock + `cbRegistry.find().reset()`

### Recommended Project Structure

```
api-whatsapp/src/main/java/br/com/erpkit/whatsapp/
├── controller/
│   └── WhatsAppController.java                # NOVO — 5 endpoints
├── service/
│   ├── WhatsAppCloudClient.java               # NOVO — 4 metodos publicos + uploadMedia interno
│   ├── MediaCacheService.java                 # NOVO — hit/miss/race
│   └── WindowEnforcementService.java          # NOVO — native query
├── aspect/                                    # NOVO sub-package
│   ├── JanelaProtegida.java                   # @interface marker
│   └── JanelaEnforcementAspect.java           # @Aspect @Order(HIGHEST_PRECEDENCE)
├── exception/                                 # NOVO sub-package
│   ├── JanelaConversaFechadaException.java    # extends ModuloException, 409
│   └── MetaApiException.java                  # extends ModuloException, carrega metaErrorCode + tipo
├── dto/
│   ├── EnviarTextoRequest.java                # NOVO record
│   ├── EnviarDocumentoRequest.java            # NOVO record (mediaBase64)
│   ├── EnviarBotoesRequest.java               # NOVO record + BotaoDto
│   ├── BotaoDto.java                          # NOVO record
│   ├── EnviarListaRequest.java                # NOVO record + @AssertTrue total itens
│   ├── SecaoDto.java                          # NOVO record
│   ├── ItemDto.java                           # NOVO record
│   ├── EnvioResponse.java                     # NOVO record {wamid}
│   └── StatusResponse.java                    # NOVO record {status, circuitBreakerState, phoneNumberId}
└── (reusa: model/, repository/, util/, config/, web/, event/)

api-whatsapp/src/main/resources/application.yml  # MOD — adicionar bloco resilience4j whatsapp-cloud
api-whatsapp/src/test/resources/application-test.yml # MOD — espelhar bloco whatsapp-cloud com timeouts curtos

api-whatsapp/src/test/java/br/com/erpkit/whatsapp/
├── controller/
│   └── WhatsAppControllerTest.java            # NOVO @WebMvcTest validacao 200/400/409/422/502
├── service/
│   ├── WhatsAppCloudClientTest.java           # NOVO @SpringBootTest+WireMock — 4 envios+upload+4xx+5xx+circuit
│   ├── MediaCacheServiceTest.java             # NOVO @SpringBootTest+H2 — hit/miss/expirado/race
│   └── WindowEnforcementServiceTest.java      # NOVO @SpringBootTest+H2 — janela aberta/fechada/cliente novo
└── aspect/
    └── JanelaEnforcementAspectTest.java       # NOVO @SpringBootTest — invocations==1 em 3 retries

lib-shared/src/main/java/br/com/erpkit/shared/dto/
└── ErrorResponse.java                          # MOD — adicionar campo Optional `codigo` (mudanca compativel)
```

### Pattern 1: Annotation-driven Resilience4j com fallback no @Retry (Phase 3 herdado)

**What:** Cada metodo publico de `WhatsAppCloudClient` carrega 3 annotations: `@JanelaProtegida` (custom AOP — outermost), `@CircuitBreaker(name="whatsapp-cloud")` (Resilience4j default order LOWEST_PRECEDENCE-2), `@Retry(name="whatsapp-cloud", fallbackMethod="fallbackEnviar...")` (Resilience4j default order LOWEST_PRECEDENCE-3 = FORA do CB).

**When to use:** Todos os 4 metodos publicos `enviarTexto`, `enviarDocumento`, `enviarBotoes`, `enviarLista`. NAO em `uploadMedia` interno (privado, chamado por `enviarDocumento`).

**Why fallback no @Retry e nao @CircuitBreaker:** Gotcha empiricamente descoberto em 03-04. Quando ambas annotations coexistem e fallback fica no INNER (CircuitBreaker), o fallback inner converte excecao em retorno void de sucesso ANTES da OUTER (Retry) ver o erro — Retry recebe "sucesso" e nao retenta. Solucao: por fallbackMethod no `@Retry` (outer). CircuitBreaker inner continua contabilizando attempts no sliding-window.

**Example:**
```java
// Source: api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/ErpCallbackClient.java (Phase 3 D-04 RESOLVED)
@JanelaProtegida
@CircuitBreaker(name = "whatsapp-cloud")
@Retry(name = "whatsapp-cloud", fallbackMethod = "fallbackEnviarTexto")
public EnvioResponse enviarTexto(String telefone, String texto) {
    // payload JSON conforme Cloud API v22.0
    Map<String, Object> body = Map.of(
        "messaging_product", "whatsapp",
        "recipient_type", "individual",
        "to", telefone,
        "type", "text",
        "text", Map.of("body", texto)
    );
    Map response = restClient.post()
        .uri("/{phoneNumberId}/messages", properties.getPhoneNumberId())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Map.class);
    String wamid = extrairWamid(response);  // ((List<Map>) response.get("messages")).get(0).get("id")
    mensagemLogRepository.save(new MensagemLog(wamid, telefone, Direcao.out, "text", texto, null));
    return new EnvioResponse(wamid);
}

@SuppressWarnings("unused")
private EnvioResponse fallbackEnviarTexto(String telefone, String texto, Throwable t) {
    throw classificar(t);  // converte para MetaApiException com tipo apropriado
}
```

### Pattern 2: Custom Aspect Outside Resilience4j Chain (D-03)

**What:** `JanelaEnforcementAspect` com `@Order(Ordered.HIGHEST_PRECEDENCE)` (= `Integer.MIN_VALUE`) garante execucao FORA da cadeia Retry+CircuitBreaker.

**Why HIGHEST_PRECEDENCE:** Spring `@Order` semantica — **lower numeric value = higher precedence = outermost**. Resilience4j Spring Boot starter: Retry default order = `LOWEST_PRECEDENCE-3` (Integer.MAX_VALUE - 3), CircuitBreaker = `LOWEST_PRECEDENCE-2`. HIGHEST_PRECEDENCE rodaria PRIMEIRO de todos. Resultado: 1 check de janela por chamada, antes de qualquer retry. `[CITED: github.com/resilience4j/resilience4j/issues/2383]` `[CITED: docs.spring.io Ordered interface]`

**When to use:** Cross-cutting que precisa rodar 1x por chamada externa (nao 1x por tentativa).

**Example:**
```java
// Source: WindowEnforcementService.java + JanelaEnforcementAspect.java (Phase 4 NOVO)
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JanelaEnforcementAspect {
    private final WindowEnforcementService windowService;

    public JanelaEnforcementAspect(WindowEnforcementService windowService) {
        this.windowService = windowService;
    }

    @Around("@annotation(br.com.erpkit.whatsapp.aspect.JanelaProtegida)")
    public Object enforce(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        if (args.length == 0 || !(args[0] instanceof String telefone)) {
            throw new IllegalStateException(
                "Metodo @JanelaProtegida deve ter telefone como primeiro argumento String: " + pjp.getSignature());
        }
        windowService.verificarJanela(telefone);  // throws JanelaConversaFechadaException se > 24h
        return pjp.proceed();
    }
}
```

**Empirical validation:** Test `JanelaEnforcementAspectTest.aspect_invoca_apenas_uma_vez_em_3_retries` — Mockito spy/counter sobre `WindowEnforcementService.verificarJanela`, WireMock stub 500/500/200, assert `verify(windowService, times(1)).verificarJanela(any())`.

### Pattern 3: Native Query for Skip-JPA-Cache Read (PER-07 herdado)

**What:** `WindowEnforcementService.verificarJanela` usa `@Query(value="SELECT ultima_mensagem_em FROM whatsapp.clientes_zap WHERE telefone = ?1", nativeQuery=true)` retornando `Optional<Instant>`.

**Why native:** JPQL nao suporta `NOW()` portavel; alem disso, native query NAO passa pelo JPA L1 cache (1st-level/Persistence Context cache) nem pela query cache, garantindo committed read fresco — exatamente o que OUT-06 pede ("fora da transacao do webhook"). Em Spring Boot 3.x + Hibernate 6, retornar `Optional<Instant>` em native query funciona em PostgreSQL e em H2 PG-mode `[CITED: docs.spring.io/spring-data/jpa]`.

**When to use:** Qualquer leitura que precise contornar cache JPA, especialmente lendo dados escritos por outra transacao recem-comitada (Phase 2 PER-07 escreve via `REQUIRES_NEW + NOW()`, Phase 4 le).

**Example:**
```java
// Source: WindowEnforcementService.java + ClienteZapRepository.java (Phase 4 NOVO; reusa pattern Phase 2)
public interface ClienteZapRepository extends JpaRepository<ClienteZap, Long> {
    // ja existente (Phase 2): findByTelefone, atualizarUltimaMensagemEm
    @Query(value =
        "SELECT ultima_mensagem_em FROM whatsapp.clientes_zap WHERE telefone = ?1",
        nativeQuery = true)
    Optional<Instant> buscarUltimaMensagemEm(String telefone);
}

@Service
public class WindowEnforcementService {
    private final ClienteZapRepository repository;
    public WindowEnforcementService(ClienteZapRepository repository) {
        this.repository = repository;
    }

    public void verificarJanela(String telefone) {
        String normalizado = TelefoneBR.normalizar(telefone);
        Optional<Instant> ultima = repository.buscarUltimaMensagemEm(normalizado);
        if (ultima.isEmpty()) {
            log.warn("Janela 24h: telefone={} nao tem mensagem entrante registrada", normalizado);
            throw new JanelaConversaFechadaException(normalizado, null);
        }
        Duration diff = Duration.between(ultima.get(), Instant.now());
        if (diff.compareTo(Duration.ofHours(24)) > 0) {
            log.warn("Janela 24h fechada: telefone={} ultima_mensagem_em={} diff={}h",
                normalizado, ultima.get(), diff.toHours());
            throw new JanelaConversaFechadaException(normalizado, ultima.get());
        }
    }
}
```

**Note on H2 compatibility:** Hibernate 6 + H2 PG-mode aceita `Optional<Instant>` como return type de native query. Se houver atrito (raro), fallback alternativo e retornar `Instant` nullable (sem Optional) — funciona identico, apenas requer `if (ultima == null)` no service.

### Pattern 4: Save+Catch DataIntegrityViolationException (race protection — Phase 2 herdado)

**What:** `MediaCacheService.registrarUpload(bytes, mediaId)` calcula sha256, faz `repository.save(new MediaCache(...))` envolvido em try/catch de `DataIntegrityViolationException`. PK conflict (race com outro thread fazendo upload do mesmo arquivo) e silenciado — outro thread ja registrou.

**When to use:** Race protection em INSERT idempotente onde a PK/UNIQUE e o gate atomico. Padrao validado empiricamente em `IdempotencyService` (Phase 2 wamid) e `ClienteZapService.identificar` (Phase 2 telefone).

**Example:**
```java
// Source: api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/IdempotencyService.java (Phase 2)
public void registrarUpload(byte[] bytes, String mediaId) {
    String hash = sha256Hex(bytes);
    Instant expira = Instant.now().plus(30, ChronoUnit.DAYS);
    try {
        repository.save(new MediaCache(hash, mediaId, expira));
        log.debug("MediaCache registrado: hash={} mediaId={}", hash, mediaId);
    } catch (DataIntegrityViolationException e) {
        // Race: outro thread fez upload do mesmo arquivo concorrentemente. PK arquivo_hash
        // disparou. Silenciar — proxima leitura ve registro existente. Pattern Phase 2.
        log.debug("MediaCache race em registrarUpload hash={} — outro thread ja registrou", hash);
    }
}
```

### Pattern 5: WhatsAppController Thin Wrapper (Spring conventions)

**What:** Controller tem 5 metodos thin (5-15 linhas cada) — `@RequestBody @Valid DTO`, decode base64 (apenas em documento), delega a service, retorna `ResponseEntity<...>`.

**Why thin:** Convencao do monorepo (`EmailController`, `StorageController`, `ConsultasController`). GlobalExceptionHandler ja captura `ModuloException` (e seus filhos `JanelaConversaFechadaException`, `MetaApiException`) e mapeia para HTTP — controller nao precisa try/catch.

**Example:**
```java
// Source: api-email/src/main/java/br/com/erpkit/email/controller/EmailController.java (pattern do monorepo)
@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppController {
    private final WhatsAppCloudClient cloudClient;
    private final WhatsAppProperties properties;
    private final CircuitBreakerRegistry cbRegistry;

    public WhatsAppController(WhatsAppCloudClient cloudClient,
                              WhatsAppProperties properties,
                              CircuitBreakerRegistry cbRegistry) {
        this.cloudClient = cloudClient;
        this.properties = properties;
        this.cbRegistry = cbRegistry;
    }

    @PostMapping("/enviar-texto")
    public ResponseEntity<EnvioResponse> enviarTexto(@Valid @RequestBody EnviarTextoRequest req) {
        return ResponseEntity.ok(cloudClient.enviarTexto(req.telefone(), req.texto()));
    }

    @PostMapping("/enviar-documento")
    public ResponseEntity<EnvioResponse> enviarDocumento(@Valid @RequestBody EnviarDocumentoRequest req) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(req.mediaBase64());
        } catch (IllegalArgumentException e) {
            throw new ModuloException("mediaBase64 invalido", HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(cloudClient.enviarDocumento(
            req.telefone(), bytes, req.filename(), req.mimeType(), req.caption()));
    }

    @PostMapping("/enviar-botoes")
    public ResponseEntity<EnvioResponse> enviarBotoes(@Valid @RequestBody EnviarBotoesRequest req) {
        return ResponseEntity.ok(cloudClient.enviarBotoes(req.telefone(), req.texto(), req.botoes()));
    }

    @PostMapping("/enviar-lista")
    public ResponseEntity<EnvioResponse> enviarLista(@Valid @RequestBody EnviarListaRequest req) {
        return ResponseEntity.ok(cloudClient.enviarLista(req.telefone(), req.texto(), req.secoes()));
    }

    @GetMapping("/status")
    public ResponseEntity<StatusResponse> status() {
        String state = cbRegistry.find("whatsapp-cloud")
            .map(cb -> cb.getState().name())
            .orElse("UNKNOWN");
        return ResponseEntity.ok(new StatusResponse("UP", state, properties.getPhoneNumberId()));
    }
}
```

### Anti-Patterns to Avoid

- **Bytes serializados em log:** NUNCA `log.info("upload: bytes={}", bytes)` — bytes ficam como `[B@hash` ou (pior, com toString customizado) o conteudo binario inteiro. Apenas `log.info("upload: hash={} mimeType={} sizeBytes={}", hash, mime, bytes.length)`.
- **`enviarTemplate(...)`:** Proibido por design (D9 PROJECT.md, OUT-05). Gate de grep automatizado: `grep -r 'enviarTemplate\|template' api-whatsapp/src/main/java/.../service/WhatsAppCloudClient.java` deve retornar 0 hits (excluindo Javadoc explicativo).
- **`@Transactional` em metodos do `WhatsAppCloudClient`:** Outbound nao deve abrir transacao — `MensagemLogRepository.save` ja e atomic. Transacao desnecessaria mantem connection do pool ocupada durante chamada HTTP externa de 1-3s.
- **Reusar mesmo `RestClient` para outbound + uploadMedia:** `RestClient` e thread-safe e pode ser reusado, mas Content-Type difere (JSON vs multipart). Recomendado: 1 `RestClient` builder unico no construtor, e cada metodo seta `.contentType(...)` per-request.
- **Self-call dentro de `WhatsAppCloudClient`:** Ex: `enviarDocumento` chamando `enviarTexto` para enviar caption. Annotation Spring AOP NAO ativa em self-call (proxy bypass). Se aparecer necessidade, refatorar via injection de outro bean.
- **Bearer no `RestClient.builder().defaultHeader(...)` global:** PITFALLS C-09 / C-14 — token vaza facilmente em interceptor mal configurado. Phase 3 D-04 lockou per-request. Mantemos.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Circuit breaker / retry com backoff exponencial | `@Scheduled` + counter + Thread.sleep | Resilience4j `@CircuitBreaker` + `@Retry` (ja no codebase) | Annotation-driven; sliding-window robusto; aspect order configuravel |
| HTTP client para Cloud API | `HttpURLConnection` ou apache `HttpClient` direto | Spring `RestClient` (ja no Phase 3) | Integracao com Resilience4j AOP, Jackson auto, error handling estatus 4xx/5xx |
| Multipart body construction manual | Concatenar `--boundary\r\nContent-Disposition: form-data; name="..."\r\n...` strings | `MultiValueMap<String, Object>` + `MediaType.MULTIPART_FORM_DATA` | Boundary auto, encoding correto. Padrao Spring `[CITED: baeldung.com/spring-rest-template-multipart-upload]` |
| sha256 hex calculation | Iteracao bit-a-bit de `MessageDigest` | `HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))` | Java 17+ standard `[CITED: docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HexFormat.html]` |
| Aspect AOP custom | `Proxy.newProxyInstance` + reflection | `@Aspect` + `@Around` + Spring `spring-boot-starter-aop` | Padrao monorepo + Resilience4j ja usa |
| Race protection em INSERT | `synchronized(this)` + flag global | UNIQUE constraint + try/catch `DataIntegrityViolationException` | Pattern Phase 2 validado empiricamente; portavel H2/PostgreSQL |
| Validacao request fields | If-throw chain | Jakarta `@Valid` + `@NotBlank` + `@Size` + `@AssertTrue` | GlobalExceptionHandler ja mapeia `MethodArgumentNotValidException` → 400 |
| Mascarar Bearer em log | `String.replace("Bearer ", "Bearer [REDACTED]")` toda vez | NAO logar a request inteira; logar apenas metadados (telefone, tipo, wamid). Bearer fica so no header per-request | Mais seguro do que tentar sanitizar — alinhado PITFALLS C-09 |
| Counter de retries | Thread-local counter | WireMock `verify(N, postRequestedFor(...))` para test; runtime, fiar Resilience4j | Pattern Phase 3 03-04 reusable |

**Key insight:** Custom solutions sao quase sempre piores no dominio HTTP outbound + AOP. Exemplos da Phase 3: implementar retry manual via Thread.sleep e contador esquece de checar interruption, nao integra com circuit breaker, e gera codigo nao-testavel sem WireMock counter assertions. Resilience4j Spring AOP resolve tudo isso com annotations + yml config.

## Common Pitfalls

### Pitfall 1: Aspect order regression — `@Order(HIGHEST_PRECEDENCE)` perdido em refactor (D-03)

**What goes wrong:** Alguem refatora `JanelaEnforcementAspect` removendo `@Order` (acha que e redundante). Aspect default order = `LOWEST_PRECEDENCE` → roda DENTRO da cadeia Retry+CircuitBreaker. Em scenario 5xx + 3 retries, `windowService.verificarJanela` e chamado 3 vezes em vez de 1 — desperdicio + race em boundary 24h: retry 1 ok, retry 3 (apos 4-6s de backoff) ja fora da janela.

**Why it happens:** `@Order` parece decorativo/optional. Defaults Spring AOP nao garantem ordering — "without explicit configuration, the behavior is undefined and may change between JVM restarts" `[CITED: github.com/resilience4j/resilience4j/issues/2383]`.

**How to avoid:**
- Test `JanelaEnforcementAspectTest.aspect_invoca_apenas_uma_vez_em_3_retries` — Mockito spy/counter sobre service, WireMock stub 500/500/200, assert `verify(windowService, times(1)).verificarJanela(any())`. Regression test obrigatorio.
- Javadoc explicito no aspect explicando POR QUE HIGHEST_PRECEDENCE.

**Warning signs:**
- WireMock counter == 3 mas `verifyService.verificarJanela` invocations == 3 (deveria ser 1).
- Em prod, log warn "Janela 24h fechada" durante backoff de retry de mensagem que comecou dentro da janela.

### Pitfall 2: Bearer token vaza em log de stack trace (PITFALLS C-09)

**What goes wrong:** `restClient.post().retrieve().body(Map.class)` lanca `HttpClientErrorException`. Excecao tem `getResponseHeaders()` que pode incluir headers de request enviados — em alguns drivers, `Authorization: Bearer eyJ...` aparece no `t.toString()` ou `printStackTrace`. Se fallback fizer `log.error("Erro: {}", t)` (passando exception inteira), Bearer vaza para arquivo de log.

**Why it happens:** Drivers HTTP variam — alguns incluem request headers em error context, outros nao. Verboso por default em DEBUG.

**How to avoid:**
- Phase 3 ja estabeleceu pattern: log apenas `t.getMessage()` no fallback, NAO `t` inteiro:
  ```java
  log.error("Cloud API falhou apos retry+CB: telefone={} tipo={}: {}", telefone, tipo, t.getMessage());
  ```
- NAO `log.error("...", t.getMessage(), t)` (segundo arg e o stack trace; segundo arg passar a exception inteira).
- Em `application.yml`, `org.springframework.web` em INFO (nunca DEBUG).
- `WhatsAppProperties.toString()` ja mascara accessToken `[VERIFIED: WhatsAppProperties.java:103]` — defesa em profundidade.

**Warning signs:**
- Log file grep: `grep -i 'Bearer ey' logs/api-whatsapp.log` deve retornar 0.
- Test `bearer_nunca_em_log` pode opcional: capture log via `OutputCaptureExtension`, assert nao contem "Bearer".

### Pitfall 3: Resilience4j retry-exceptions whitelist incompleta (gotcha 03-04 reaproveitado)

**What goes wrong:** Configurar `retry-exceptions: [HttpServerErrorException, SocketTimeoutException, IOException]` mas omitir `ResourceAccessException`. Spring `RestClient.retrieve()` empacota `SocketTimeoutException` em `ResourceAccessException` — sem este entry na whitelist, timeouts NAO retentariam mesmo com `SocketTimeoutException` listado (Resilience4j compara via `instanceof`).

**Why it happens:** Documentacao Resilience4j foca em `IOException`. Spring wrapping no `RestClient` nao e obvio.

**How to avoid:**
- Phase 3 03-04 ja descobriu empiricamente. Whitelist obrigatoria para `whatsapp-cloud` instance:
  ```yaml
  retry-exceptions:
    - org.springframework.web.client.HttpServerErrorException
    - org.springframework.web.client.ResourceAccessException  # <-- CRUCIAL
    - java.net.SocketTimeoutException
    - java.io.IOException
  ```
- Test `WhatsAppCloudClientTest.timeout_retry_e_fallback` (delay > timeout) — counter > 1.

**Warning signs:**
- Test de timeout: `wireMock.findAll(...).size() == 1` quando deveria ser 3 → falta `ResourceAccessException` na whitelist.

### Pitfall 4: Optional native query — quirks H2 vs PostgreSQL

**What goes wrong:** `@Query(value="SELECT ultima_mensagem_em FROM ...", nativeQuery=true) Optional<Instant>` pode falhar em H2 com erro tipo "could not extract resultset" se H2 retornar `TIMESTAMP` (sem timezone) e Hibernate 6 tentar mapear para `Instant` (que precisa offset).

**Why it happens:** H2 in-memory PG-mode armazena `TIMESTAMP` como `LocalDateTime`-equivalent (sem TZ). PostgreSQL real usa `TIMESTAMP` (sem TZ) ou `TIMESTAMP WITH TIME ZONE`. Mapeamento JDBC → `Instant` precisa decisao.

**How to avoid:**
- Migration V1 (Phase 1) usa `ultima_mensagem_em TIMESTAMP` (sem TZ) — `[VERIFIED: V1__criar_tabela_clientes_zap.sql via Plan 01-04]`.
- Entity `ClienteZap.ultimaMensagemEm` mapeada como `Instant` `[VERIFIED: ClienteZap.java:42]`.
- Phase 2 `ClienteZapServiceTest` ja testa native query `UPDATE ... NOW()` em H2 PG-mode com sucesso — `Instant` field funciona.
- Para LEITURA, validacao empirica do test `WindowEnforcementServiceTest.cliente_com_ultima_em_23h_passa` em H2.
- Plano fallback: se `Optional<Instant>` quebrar, retornar `Instant` nullable (sem Optional wrapper). Funciona identico no service: `if (ultima == null) throw...`.

**Warning signs:**
- Hibernate `MappingException` ao instanciar repository.
- Test que retorna `Optional<Instant>` com `ultima_mensagem_em` populado mas `Optional.isEmpty()` — provavel mapping error.

### Pitfall 5: Multipart Content-Type boundary missing (Cloud API upload)

**What goes wrong:** Setar `Content-Type: multipart/form-data` HARDCODED (sem `boundary=...`) faz Meta retornar 400 "missing or malformed multipart body". Spring `RestClient` com `MultiValueMap<String, Object>` body computa boundary automaticamente — mas se desenvolvedor fizer override manual do header, quebra.

**Why it happens:** Documentacao Meta mostra exemplos curl sem detalhar que `-F` cuida do boundary; copy-paste em codigo Java sem `RestClient` integration funciona "as vezes".

**How to avoid:**
- `RestClient` com `MultiValueMap` + `.contentType(MediaType.MULTIPART_FORM_DATA)` — boundary auto `[CITED: docs.spring.io/spring-framework MultipartBodyBuilder]`.
- Test `WhatsAppCloudClientTest.upload_media_envia_3_campos_obrigatorios` — WireMock matcher `multipart()` valida `messaging_product=whatsapp`, `type=<mime>`, `file=<bytes>`.
- Wave 1 do Phase 4 deve incluir empirical spike: 1 test isolado de upload com WireMock + assertion dos 3 fields. Se quebrar, ajusta antes de avancar.

**Warning signs:**
- Meta retorna `(#100) The parameter messaging_product is required` ou `The parameter file is required`.
- WireMock `verify` falha em `multipart()` matcher.

### Pitfall 6: `enviarTemplate` reaparece via copy-paste de exemplo Meta

**What goes wrong:** Desenvolvedor encontra exemplo Meta de `type=template` (gera custo) e copia para `WhatsAppCloudClient` "para completude". Custo zero quebrado.

**Why it happens:** Meta docs documentam template como tipo first-class. Copy-paste sem ler PROJECT.md D9 ou OUT-05.

**How to avoid:**
- Gate de grep em CI/Phase 6 verify:
  ```bash
  grep -rn 'enviarTemplate\|"template"\|type.*=.*template' \
    api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/WhatsAppCloudClient.java
  # Esperado: 0 hits (Javadoc explicativo OK em outros arquivos)
  ```
- Test `WhatsAppCloudClientTest.metodos_publicos_nao_inclui_template` — reflection lista todos `Method m : WhatsAppCloudClient.class.getDeclaredMethods()` filtra `Modifier.isPublic`, assert `none startsWith "template"`.
- Javadoc da classe `WhatsAppCloudClient` explicito: "Por design (D9 PROJECT.md, OUT-05), este cliente NAO expoe `enviarTemplate(...)`. Templates geram custo Meta — proibido."

**Warning signs:**
- PR review com diff adicionando metodo publico `template`/`enviarTemplate`.
- Fatura Meta com line item para qualquer numero — auditar mensagens_log direcao=out.

## Code Examples

Verified patterns from Cloud API v22.0 docs and existing codebase.

### 1. Cloud API Payload Reference (Meta v22.0+ — payload structure unchanged across v20-v23)

**1.1 Texto** `[CITED: developers.facebook.com/docs/whatsapp/cloud-api/reference/messages]`
```json
{
  "messaging_product": "whatsapp",
  "recipient_type": "individual",
  "to": "5547984178525",
  "type": "text",
  "text": { "body": "Olá, seu orçamento está pronto." }
}
```
Java construction:
```java
Map<String, Object> body = Map.of(
    "messaging_product", "whatsapp",
    "recipient_type", "individual",
    "to", telefone,
    "type", "text",
    "text", Map.of("body", texto)
);
```

**1.2 Documento (referenciando media_id ja uploadado)** `[CITED: developers.facebook.com/docs/whatsapp/cloud-api/messages/document-messages]`
```json
{
  "messaging_product": "whatsapp",
  "recipient_type": "individual",
  "to": "5547984178525",
  "type": "document",
  "document": {
    "id": "1234567890123456",
    "filename": "orcamento-42.pdf",
    "caption": "Seu orçamento"
  }
}
```
Java construction:
```java
Map<String, Object> documento = new LinkedHashMap<>();
documento.put("id", mediaId);
documento.put("filename", filename);
if (caption != null && !caption.isBlank()) documento.put("caption", caption);

Map<String, Object> body = Map.of(
    "messaging_product", "whatsapp",
    "recipient_type", "individual",
    "to", telefone,
    "type", "document",
    "document", documento
);
```

**1.3 Botoes (interactive button — max 3 reply buttons)** `[CITED: developers.facebook.com/docs/whatsapp/cloud-api/messages/interactive-reply-buttons-messages]`
```json
{
  "messaging_product": "whatsapp",
  "recipient_type": "individual",
  "to": "5547984178525",
  "type": "interactive",
  "interactive": {
    "type": "button",
    "body": { "text": "Aprovar orçamento 1234?" },
    "action": {
      "buttons": [
        { "type": "reply", "reply": { "id": "aprovar 1234", "title": "Aprovar" } },
        { "type": "reply", "reply": { "id": "recusar 1234", "title": "Recusar" } }
      ]
    }
  }
}
```
Constraints (Meta enforced — devem espelhar em validation):
- Body text ≤ 1024 chars
- Button title ≤ 20 chars
- Button id ≤ 256 chars
- Max 3 buttons (Phase 4 valida via `@Size(max=3)` no DTO)

**1.4 Lista (interactive list — max 10 itens totais)** `[CITED: developers.facebook.com/docs/whatsapp/cloud-api/messages/interactive-list-messages]`
```json
{
  "messaging_product": "whatsapp",
  "recipient_type": "individual",
  "to": "5547984178525",
  "type": "interactive",
  "interactive": {
    "type": "list",
    "body": { "text": "Escolha uma opção" },
    "action": {
      "button": "Ver opções",
      "sections": [
        {
          "title": "Pedidos abertos",
          "rows": [
            { "id": "ver 1234", "title": "Pedido 1234", "description": "R$ 250,00" },
            { "id": "ver 1235", "title": "Pedido 1235" }
          ]
        }
      ]
    }
  }
}
```
Constraints:
- Max 10 sections, max 10 rows TOTAIS (somando todas sections) — Meta hard-rejects
- Section title obrigatorio se >1 section
- Row description opcional
- `action.button` (label do botao trigger) obrigatorio, max 20 chars

### 2. Multipart Upload Media (Cloud API v22.0)

`[CITED: developers.facebook.com/docs/whatsapp/cloud-api/reference/media]`

**Endpoint:** `POST {metaApiBaseUrl}/{phoneNumberId}/media`
**Headers:** `Authorization: Bearer {accessToken}`, `Content-Type: multipart/form-data` (boundary auto)
**Body fields (3 obrigatorios):**
1. `messaging_product` = `"whatsapp"` (literal string)
2. `file` = bytes binarios + filename + Content-Type
3. `type` = MIME type (ex: `application/pdf`, `image/jpeg`)

**Java com Spring `RestClient`:**
```java
// Source: pattern Spring docs §FormHttpMessageConverter [CITED: docs.spring.io/spring-framework]
private String uploadMedia(byte[] bytes, String mimeType, String filename) {
    ByteArrayResource fileResource = new ByteArrayResource(bytes) {
        @Override
        public String getFilename() { return filename; }
    };

    MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    parts.add("messaging_product", "whatsapp");
    parts.add("type", mimeType);
    parts.add("file", fileResource);

    Map response = restClient.post()
        .uri("/{phoneNumberId}/media", properties.getPhoneNumberId())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(parts)
        .retrieve()
        .body(Map.class);
    return (String) response.get("id");  // media_id retornado pelo Meta
}
```

**Response Meta:**
```json
{ "id": "1234567890123456" }
```

### 3. WhatsAppCloudClient.enviarDocumento — pipeline completo (cache + upload + send)

```java
@JanelaProtegida
@CircuitBreaker(name = "whatsapp-cloud")
@Retry(name = "whatsapp-cloud", fallbackMethod = "fallbackEnviarDocumento")
public EnvioResponse enviarDocumento(String telefone, byte[] bytes,
                                     String filename, String mimeType, String caption) {
    // 1. Cache lookup (sha256 hex digest)
    Optional<String> cached = mediaCacheService.buscarMediaId(bytes);
    String mediaId = cached.orElseGet(() -> {
        // 2. Cache miss — upload + register
        String novoMediaId = uploadMedia(bytes, mimeType, filename);
        mediaCacheService.registrarUpload(bytes, novoMediaId);
        return novoMediaId;
    });

    // 3. Send message referenciando media_id
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("id", mediaId);
    doc.put("filename", filename);
    if (caption != null && !caption.isBlank()) doc.put("caption", caption);

    Map response = restClient.post()
        .uri("/{phoneNumberId}/messages", properties.getPhoneNumberId())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of(
            "messaging_product", "whatsapp",
            "recipient_type", "individual",
            "to", telefone,
            "type", "document",
            "document", doc
        ))
        .retrieve()
        .body(Map.class);

    String wamid = extrairWamid(response);
    mensagemLogRepository.save(new MensagemLog(wamid, telefone, Direcao.out, "document",
                                               caption, mediaId));
    return new EnvioResponse(wamid);
}

@SuppressWarnings("unused")
private EnvioResponse fallbackEnviarDocumento(String telefone, byte[] bytes,
                                              String filename, String mimeType, String caption,
                                              Throwable t) {
    throw classificar(t);
}
```

### 4. MediaCacheService — TTL estrito 30d (D-04)

```java
@Service
public class MediaCacheService {
    private static final Logger log = LoggerFactory.getLogger(MediaCacheService.class);
    private static final Duration TTL = Duration.ofDays(30);

    private final MediaCacheRepository repository;

    public MediaCacheService(MediaCacheRepository repository) {
        this.repository = repository;
    }

    public Optional<String> buscarMediaId(byte[] bytes) {
        String hash = sha256Hex(bytes);
        return repository.findByArquivoHashAndExpiraEmAfter(hash, Instant.now())
            .map(MediaCache::getMediaId);
    }

    public void registrarUpload(byte[] bytes, String mediaId) {
        String hash = sha256Hex(bytes);
        Instant expira = Instant.now().plus(TTL);
        try {
            // Se ja existir (expirado), upsert: atualiza media_id + expira_em.
            // Padrao: deletar antigo + save novo (mais simples que UPDATE custom).
            repository.findById(hash).ifPresent(repository::delete);
            repository.save(new MediaCache(hash, mediaId, expira));
            log.debug("MediaCache: hash={} mediaId={} expira={}", hash, mediaId, expira);
        } catch (DataIntegrityViolationException e) {
            // Race: outro thread fez upload do mesmo arquivo concorrentemente.
            // Pattern Phase 2 IdempotencyService — silenciar (PK e gate atomico).
            log.debug("MediaCache race: hash={} — outro thread ja registrou", hash);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }
}
```

### 5. Persistencia outbound (OUT-09) — usa entity + repo Phase 2

```java
// Apos sucesso da chamada Meta, dentro de cada metodo enviar* do WhatsAppCloudClient:
mensagemLogRepository.save(
    new MensagemLog(
        wamid,           // retornado por response.messages[0].id
        telefone,        // ja recebido como argumento
        Direcao.out,     // enum Phase 2
        tipo,            // "text" | "document" | "interactive_button" | "interactive_list"
        conteudoResumo,  // texto / caption / ID do button selecionado (resumo, NAO PII completa)
        mediaId          // null para texto/botoes/lista; preenchido para documento
    )
);
```
Nao precisa `@Transactional` — `save` e atomico via Spring Data JPA. Wamid UNIQUE constraint global previne colisao com Phase 2 inbound (in/out tem prefixos diferentes na semantica Meta).

### 6. DTOs com Bean Validation (Jakarta)

**EnviarTextoRequest:**
```java
public record EnviarTextoRequest(
    @NotBlank(message = "telefone obrigatorio")
    @Pattern(regexp = "^\\d{10,15}$", message = "telefone deve conter apenas digitos (10-15)")
    String telefone,

    @NotBlank(message = "texto obrigatorio")
    @Size(max = 4096, message = "texto excede 4096 chars")
    String texto
) {}
```

**EnviarDocumentoRequest (D-01 JSON+base64):**
```java
public record EnviarDocumentoRequest(
    @NotBlank String telefone,
    @NotBlank @Size(max = 18_000_000, message = "mediaBase64 excede limite (~13MB binario)")
    String mediaBase64,
    @NotBlank @Pattern(regexp = "^[a-z]+/[a-z0-9.+-]+$") String mimeType,
    @NotBlank @Size(max = 255) String filename,
    @Size(max = 1024) String caption
) {}
```

**EnviarBotoesRequest:**
```java
public record EnviarBotoesRequest(
    @NotBlank String telefone,
    @NotBlank @Size(max = 1024) String texto,
    @NotEmpty @Size(max = 3, message = "Maximo 3 botoes (Cloud API limit)")
    @Valid List<BotaoDto> botoes
) {}

public record BotaoDto(
    @NotBlank @Size(max = 256) String id,
    @NotBlank @Size(max = 20, message = "title max 20 chars (Cloud API limit)") String title
) {}
```

**EnviarListaRequest com `@AssertTrue` para soma cross-secoes:**
```java
public record EnviarListaRequest(
    @NotBlank String telefone,
    @NotBlank @Size(max = 1024) String texto,
    @NotEmpty @Size(max = 10, message = "Maximo 10 secoes")
    @Valid List<SecaoDto> secoes
) {
    @AssertTrue(message = "Total de itens em todas as secoes excede 10 (Cloud API limit)")
    @JsonIgnore
    public boolean isTotalItensValido() {
        if (secoes == null) return true;  // @NotEmpty pega o caso nulo
        int total = secoes.stream()
            .filter(Objects::nonNull)
            .mapToInt(s -> s.itens() == null ? 0 : s.itens().size())
            .sum();
        return total <= 10;
    }
}

public record SecaoDto(
    @NotBlank @Size(max = 24) String titulo,
    @NotEmpty @Valid List<ItemDto> itens
) {}

public record ItemDto(
    @NotBlank @Size(max = 200) String id,
    @NotBlank @Size(max = 24) String title,
    @Size(max = 72) String description
) {}
```

`[CITED: jakarta.ee/specifications/bean-validation/3.0]` — `@AssertTrue` em metodo `isXxx()` publico do record e idiomatico, com `@JsonIgnore` para nao serializar na response.

### 7. application.yml — bloco resilience4j whatsapp-cloud (espelha erp-callback)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      erp-callback:
        # ja existente Phase 3
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 3
      whatsapp-cloud:                    # NOVO Phase 4
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 3
        register-health-indicator: false
  retry:
    instances:
      erp-callback:
        # ja existente Phase 3
      whatsapp-cloud:                    # NOVO Phase 4
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2.0
        retry-exceptions:
          - org.springframework.web.client.HttpServerErrorException
          - org.springframework.web.client.ResourceAccessException
          - java.net.SocketTimeoutException
          - java.io.IOException
        # 4xx (HttpClientErrorException) NAO em retry-exceptions — Resilience4j
        # default NAO retenta excecoes nao listadas (gotcha 03-04 RESOLVED).
```

`application-test.yml` espelha com janelas curtas (igual erp-callback test profile):
```yaml
resilience4j:
  retry:
    instances:
      whatsapp-cloud:
        max-attempts: 3
        wait-duration: 50ms              # tests rapidos
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2.0
        retry-exceptions:
          - org.springframework.web.client.HttpServerErrorException
          - org.springframework.web.client.ResourceAccessException
          - java.net.SocketTimeoutException
          - java.io.IOException
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `RestTemplate` para HTTP outbound | `RestClient` (Spring 6.1+) | Spring 6.1 (2023) | Phase 3 ja adotou; consistencia |
| `@Order` no aspect "talvez nao seja necessario" | Spring Boot 3 — explicito sempre | Boot 3 (2022) — defaults Resilience4j mudaram | Aspect order sem `@Order` e undefined behavior `[CITED: github.com/resilience4j/resilience4j/issues/2383]` |
| Multipart manual com boundary string | `MultiValueMap<String, Object>` + `MULTIPART_FORM_DATA` | Spring Framework 5+ | Padrao desde Boot 2; `RestClient` honra o pattern |
| Hex encoding via `Apache commons-codec` | `HexFormat.of().formatHex(byte[])` | Java 17+ | Sem dep extra |
| `@Constraint` custom para qualquer validation cross-field | `@AssertTrue isXxx()` em metodo do record (com `@JsonIgnore`) | Bean Validation 3.0 + Java 16 records | Menos verboso |
| `accessToken` em query param | `Authorization: Bearer ...` header | Sempre (Meta deprecation) | PITFALLS C-14 — gate de regression test |

**Deprecated/outdated:**
- `RestTemplate`: ainda funciona, mas `RestClient` e o futuro. Phase 4 segue Phase 3.
- `accessToken=...` query param: NUNCA usar.
- `@Cacheable` Caffeine para media: nao adequado — TTL baseado em UPLOAD timestamp (nao access), Caffeine sliding por default. JPA + DB e o pattern correto.

## Assumptions Log

> Lista de claims `[ASSUMED]` neste research. Planner/discuss-phase usam para identificar decisoes que precisam de confirmacao do usuario.

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Hibernate 6 + H2 PG-mode aceita `Optional<Instant>` em native query retornando `TIMESTAMP` | §Pattern 3 + §Pitfall 4 | Alto se quebrar — mas fallback `Instant` nullable e trivial. Validar empiricamente em primeira wave (spike de 5 min). |
| A2 | `@Order(HIGHEST_PRECEDENCE)` em aspect customizado sempre roda fora da cadeia Resilience4j default no Spring Boot 3.5.x | §Pattern 2 + §Pitfall 1 | Medio — issue Resilience4j #2383 confirma documentalmente. Validacao empirica via test counter==1 em 3 retries. |
| A3 | Cloud API v22.0 aceita `messaging_product/file/type` exatamente como nomes de field em multipart upload (sem variantes camelCase) | §Code Examples §2 + §Pitfall 5 | Alto se Meta atualizar. Web search 2026 confirma; usar Phase 4 spike Wave 1 para empirical validation com WireMock antes de avancar. |
| A4 | Meta v22.0 retorna response shape `{messages: [{id: <wamid>}]}` para todos os 4 tipos de envio | §Code Examples §3 + §1 | Medio — pattern confirmado em docs oficiais para text e document; assumido identico para interactive button/list. Phase 4 deve testar via WireMock cada um. |
| A5 | `ResourceAccessException` e o wrapper que `RestClient.retrieve()` produz para timeout em qualquer Java HTTP driver | §Pitfall 3 | Baixo — empiricamente validado em Phase 3 03-04 com mesmo `RestClient` factory `SimpleClientHttpRequestFactory`. |
| A6 | Limite de payload Spring Boot default (`spring.servlet.multipart.max-request-size=10MB`) e suficiente para piloto MUDAS (PDFs ate 10MB binario = ~13MB base64) | §D-01 trade-off | Baixo — D-01 ja documenta como reversivel; Phase 6 RUNBOOK pode ajustar se cliente usar PDF maior. |
| A7 | `mensagens_log.tipo VARCHAR(50)` aceita strings "text", "document", "interactive_button", "interactive_list" | §Code Examples §5 | Baixo — Phase 2 ja persiste "text", "interactive_button" etc para inbound; Phase 4 espelha. |

**Mitigacao geral:** Phase 4 Plan 04-04 (WhatsAppCloudClient + tests) deve incluir Wave 0 spike de 5-10min: 1 test isolado de upload media com WireMock + 1 test isolado de envio texto, validando empiricamente A1, A3, A4 antes de continuar com os demais metodos.

## Open Questions (RESOLVED)

1. **Tem alguma trava server-side em `wamid` UNIQUE compartilhado entre direcao=in (Phase 2) e direcao=out (Phase 4)?**
   - What we know: Phase 2 escreve `direcao=in` com wamid prefixado por Meta para mensagens entrantes. Phase 4 escreve `direcao=out` com wamid prefixado para mensagens saidas. Meta documenta wamids distintos para in/out (prefixos diferentes na semantica).
   - What's unclear: Se um wamid out duplicado (improvavel mas possivel se Meta API responder duas vezes) lanca DataIntegrityViolation hoje, qual o comportamento? Phase 4 nao tem idempotency-pattern equivalente em outbound.
   - Recommendation: Plan inclui try/catch defensivo em `mensagensLogRepository.save(direcao=out)` — se duplicate, logar warn + ainda retornar response com wamid (idempotente do ponto de vista do ERP). Reuso do pattern Phase 2 IdempotencyService.
   - **RESOLVED:** try/catch defensivo em `mensagensLogRepository.save` no Plan 04-04 task de outbound persistence cobre OUT-09 / SC-5 — duplicate wamid out (improvavel) loga warn + retorna wamid; reuso do pattern Phase 2 IdempotencyService.

2. **`/api/whatsapp/status` deve incluir validacao de subscribed_apps (PITFALLS C-12) na Phase 4 ou Phase 6?**
   - What we know: D-04 lockou `StatusResponse` minimal (status + circuitBreakerState + phoneNumberId) — sem `subscribed_apps`. PITFALLS C-12 sugere checagem em Phase 6.
   - What's unclear: Phase 4 status endpoint pode ser fonte de bug "shadow delivery" se nao tiver subscribe check.
   - Recommendation: Confirmar D-04 escopo. Phase 6 expande. Phase 4 nao trava o operador piloto MUDAS porque RUNBOOK manual da Phase 6 cobre setup correto.
   - **RESOLVED:** D-04 lockou StatusResponse minimal v1 (status + circuitBreakerState + phoneNumberId); subscribed_apps adiado para Phase 6 (PITFALLS C-12 territory) — RUNBOOK manual cobre setup correto no piloto MUDAS.

3. **`MetaApiException` carrega `tipo` enum publico?**
   - What we know: D-02 lockou que excepcao carrega `metaErrorCode` Integer e `tipo` enum {CATEGORIA_4XX, INDISPONIVEL_5XX, TIMEOUT, CIRCUIT_OPEN}. ErrorResponse precisa expor `codigo` String.
   - What's unclear: `tipo` enum e expostado no JSON response ou apenas usado internamente? Recomendado: enum interno; `codigo` no JSON e String literal (`"META_ERROR"`, `"META_INDISPONIVEL"`, `"META_TIMEOUT"`, `"CIRCUIT_OPEN"`) derivado do `tipo`.
   - Recommendation: Plan documenta mapping table tipo → codigo no JSON. ErrorResponse expoe `codigo` String + opcionalmente `metaErrorCode` Integer (apenas quando relevante — nao em CIRCUIT_OPEN).
   - **RESOLVED:** D-02 expoe somente `codigo` String publicamente; `tipo` interno usado apenas para mapping HTTP status. Plan 04-04 implementa MetaApiException com `tipo` como enum interno (package-private getter) e CodigoCarrier expondo apenas `codigo` + `metaErrorCode` no JSON serializado.

4. **`ErrorResponse` em lib-shared aceita campo opcional `codigo` sem quebrar api-email/api-storage/api-consultas?**
   - What we know: Atualmente `ErrorResponse` tem `status, erro, mensagem, timestamp, campos` — sem `codigo`. `[VERIFIED: lib-shared/ErrorResponse.java]`
   - What's unclear: Adicionar `private String codigo` (nullable) e `setCodigo`/`getCodigo` deveria ser compativel — Jackson serialize null como null ou omitir; outros modulos nao referenciam o campo.
   - Recommendation: Plan inclui modificacao no `lib-shared/ErrorResponse.java` (mudanca compativel, validar build dos 3 outros modulos verde). Adicionar tambem `metaErrorCode` Integer nullable, OU manter so `codigo` e colocar metaErrorCode em `campos` map.
   - **RESOLVED:** Plan 04-01 task 1 adiciona `codigo` String + `metaErrorCode` Integer como campos opcionais (nullable) em `lib-shared/ErrorResponse.java`. Mudanca backward-compativel: Jackson ignora campos extras nos modulos consumidores; build verde dos 3 outros api-* validado em Plan 04-06 task 1 (`./mvnw verify` reator inteiro).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java JDK | Build + runtime | ✓ | 21 | — |
| Maven (mvnw) | Build | ✓ | 3.9.x via wrapper | — |
| H2 in-memory | Test scope | ✓ | 2.3.232 | — |
| Resilience4j Spring Boot 3 | `@CircuitBreaker` + `@Retry` | ✓ | 2.2.0 | — |
| spring-boot-starter-aop | `@Aspect` + Resilience4j AOP | ✓ | 3.5.9 | — |
| WireMock standalone | Integration tests | ✓ | 3.10.0 | — |
| Spring `RestClient` | HTTP outbound | ✓ | 6.x (transitive) | — |
| Jackson | JSON serialization (records) | ✓ | 2.18.x (transitive) | — |
| awaitility | Async test (opcional) | ✓ | Boot BOM | — |
| PostgreSQL local 5433 | Production runtime | n/a (codigo nao roda local em test) | — | H2 PG-mode em test |
| Cloud API real | E2E real (Phase 6+) | n/a (Phase 4 = WireMock) | — | WireMock simula |

**Missing dependencies with no fallback:** Nenhuma. Phase 4 e 100% codigo + config + test — sem novas deps externas.
**Missing dependencies with fallback:** Nenhuma — todas presentes.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + AssertJ + WireMock 3.10.0 (via spring-boot-starter-test 3.5.9) |
| Config file | `api-whatsapp/src/test/resources/application-test.yml` |
| Quick run command | `./mvnw -pl api-whatsapp test -Dtest='<TestClass>'` (single class) |
| Full suite command | `./mvnw -pl api-whatsapp verify` (api-whatsapp aggregate) ou `./mvnw verify` (reator inteiro) |

### Phase Requirements → Test Map (5 SC + 11 OUT-XX)

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| SC-1 / OUT-05 | Sem `enviarTemplate` no codigo do client | grep gate + reflection unit | `grep -rn 'enviarTemplate\|"template"' api-whatsapp/src/main/java/.../service/WhatsAppCloudClient.java` (CI gate) + `WhatsAppCloudClientTest.metodos_publicos_nao_inclui_template` reflection assertion | ❌ Wave 0 (Plan 04-04) |
| SC-2 / OUT-06+OUT-07 | 409 + `JANELA_24H_FECHADA` antes de chamar Meta | unit (`WindowEnforcementServiceTest`) + integration (`WhatsAppControllerTest` aspect via `@SpringBootTest`) | `./mvnw -pl api-whatsapp test -Dtest='WindowEnforcementServiceTest+WhatsAppControllerTest#janela_fechada_retorna_409'` | ❌ Wave 0 (Plans 04-02 + 04-05) |
| SC-3 / OUT-08 | enviarDocumento mesmo PDF 2x → 1 upload; expirado → reupload | unit (`MediaCacheServiceTest` 4 cenarios — hit/miss/expirado/race) | `./mvnw -pl api-whatsapp test -Dtest='MediaCacheServiceTest'` | ❌ Wave 0 (Plan 04-03) |
| SC-4a / OUT-10 | 4xx (400/401/403) NAO retenta + log estruturado | integration WireMock (`WhatsAppCloudClientTest.quatrocentos_no_retry_counter_1`) | `./mvnw -pl api-whatsapp test -Dtest='WhatsAppCloudClientTest#quatrocentos_no_retry'` | ❌ Wave 0 (Plan 04-04) |
| SC-4b / OUT-10 | 5xx + timeout retentam 3x exponencial | integration WireMock (`WhatsAppCloudClientTest.cinquecentos_recupera_counter_3` + `timeout_retry_e_fallback`) | `./mvnw -pl api-whatsapp test -Dtest='WhatsAppCloudClientTest#cinquecentos_recupera+timeout_retry'` | ❌ Wave 0 (Plan 04-04) |
| SC-4c / PITFALLS C-09 | Bearer NUNCA em log/query param | grep gate + WireMock `getAllServeEvents().forEach(... !contains "access_token=")` | `./mvnw -pl api-whatsapp test -Dtest='WhatsAppCloudClientTest#bearer_nunca_em_query_param'` | ❌ Wave 0 (Plan 04-04) |
| SC-5 / OUT-09 + OUT-11 | Mensagem outbound persiste com `direcao=out` + wamid + 200 OK | integration `@WebMvcTest` mockando `WhatsAppCloudClient` + assertion JdbcTemplate em `mensagens_log` (via @SpringBootTest separado) | `./mvnw -pl api-whatsapp test -Dtest='WhatsAppControllerTest+WhatsAppCloudClientTest'` | ❌ Wave 0 (Plans 04-04 + 04-05) |
| OUT-01 | enviarTexto chama POST /messages JSON | WireMock stub + `verify(1, postRequestedFor(urlEqualTo("/...")).withRequestBody(matchingJsonPath("$.type", equalTo("text"))))` | (incluso no `WhatsAppCloudClientTest`) | ❌ |
| OUT-02 | enviarDocumento upload + send 2-step | WireMock 2 stubs (media + messages) + `verify(1, multipart)` + `verify(1, postRequestedFor(messages))` | (incluso no `WhatsAppCloudClientTest`) | ❌ |
| OUT-03 | enviarBotoes max 3 / falha early | unit `EnviarBotoesRequestValidationTest` Bean Validation (com `Validator` programatico) | `./mvnw -pl api-whatsapp test -Dtest='*ValidationTest'` | ❌ Wave 0 (Plan 04-05 ou 04-01) |
| OUT-04 | enviarLista max 10 itens / falha early | unit `EnviarListaRequestValidationTest` `@AssertTrue` | (igual OUT-03) | ❌ |
| OUT-07 | aspect intercepta antes de Cloud API | integration `JanelaEnforcementAspectTest.aspect_invoca_apenas_uma_vez_em_3_retries` Mockito spy + WireMock 500/500/200 | `./mvnw -pl api-whatsapp test -Dtest='JanelaEnforcementAspectTest'` | ❌ Wave 0 (Plan 04-02) |
| OUT-11 | 5 endpoints + GET /status | `WhatsAppControllerTest @WebMvcTest` 5 happy paths + 4 erros + 1 status | `./mvnw -pl api-whatsapp test -Dtest='WhatsAppControllerTest'` | ❌ Wave 0 (Plan 04-05) |

### Sampling Rate
- **Per task commit:** `./mvnw -pl api-whatsapp test -Dtest='<TestClass>'` (single class, ~5-15s)
- **Per wave merge:** `./mvnw -pl api-whatsapp verify` (api-whatsapp aggregate, ~30-60s)
- **Phase gate:** Reator inteiro `./mvnw verify` BUILD SUCCESS (~2-3min) — todos os 7 modulos verde, zero regressao em Phase 1+2+3 (152 tests existentes + ~30-40 novos Phase 4)

### Wave 0 Gaps
- [ ] `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/WhatsAppCloudClientTest.java` — covers SC-1, SC-4a, SC-4b, SC-4c, SC-5, OUT-01..04, OUT-08, OUT-10
- [ ] `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/MediaCacheServiceTest.java` — covers SC-3, OUT-08
- [ ] `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/WindowEnforcementServiceTest.java` — covers SC-2, OUT-06
- [ ] `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/aspect/JanelaEnforcementAspectTest.java` — covers SC-2, OUT-07 (counter==1 em 3 retries)
- [ ] `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WhatsAppControllerTest.java` — covers OUT-11, SC-2, SC-5 + validation 400 paths
- [ ] (opcional) `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/dto/EnviarBotoesRequestValidationTest.java` + `EnviarListaRequestValidationTest.java` — covers OUT-03, OUT-04 isoladamente (Bean Validation programatico)
- [ ] Framework install: nenhum — toda dependencia ja em pom.xml

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | API Key middleware ja no `ApiKeyFilter` (Phase 1) cobre `/api/whatsapp/*`; webhook Meta cobre via HMAC. Phase 4 reusa — sem novo controle. |
| V3 Session Management | n/a | Sem sessoes — todo trafico stateless (REST + bearer Meta) |
| V4 Access Control | yes | `/api/whatsapp/*` requer `X-API-Key` (Phase 1 SecurityConfig); Phase 4 confia |
| V5 Input Validation | **yes (Phase 4)** | Jakarta Bean Validation: `@NotBlank`, `@Size`, `@Pattern` (telefone), `@AssertTrue` (cross-field). Falha early via `MethodArgumentNotValidException` → 400 (GlobalExceptionHandler). |
| V6 Cryptography | n/a | Phase 4 nao implementa cripto. SHA-256 para hash (nao secret) via `MessageDigest.getInstance("SHA-256")` JDK standard. Bearer token do Meta consumido como string opaco. |
| V7 Error Handling | **yes (Phase 4)** | Estruturado via `ErrorResponse` + `codigo`. NAO logar stack trace inteira do throwable em fallback (PITFALLS C-09 — pode vazar Bearer). Apenas `t.getMessage()`. |
| V8 Data Protection | yes | `accessToken` mascarado em `WhatsAppProperties.toString()` `[VERIFIED]`. mensagens_log.conteudo TEXT — telefones e textos persistidos sao PII; LGPD compliance backlog v2 (DIFF-05). |
| V9 Communication | yes | HTTPS para Cloud API (URL `https://graph.facebook.com/v22.0`). HTTP localhost para ERP callback (loopback OK). Bearer header per-request (PITFALLS C-09/C-14). |
| V10 Malicious Code | n/a | Sem deserializacao de fonte externa nao-confiavel; payload Meta e schema fixo |
| V12 Files and Resources | yes | `mediaBase64` decodificado para `byte[]` — limite via `@Size(max=18MB)` no DTO. `ByteArrayResource` no upload — sem temp file no disco. |

### Known Threat Patterns for Spring Boot 3 + Meta Cloud API

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Bearer token leak via log | Information disclosure | Per-request header (NUNCA `defaultHeader`); `t.getMessage()` em fallback (nao `t` inteiro); `org.springframework.web` em INFO; `WhatsAppProperties.toString()` mascarado (PITFALLS C-09) |
| Bearer token via query param | Information disclosure (CDN/proxy logs) | Sempre header, NUNCA `?access_token=...` (PITFALLS C-14). Test gate via `getAllServeEvents().forEach(... !contains "access_token=")` |
| Custom solution para HMAC outbound | Tampering | Phase 4 NAO implementa HMAC outbound — Cloud API usa apenas Bearer; HMAC e inbound only (Phase 1) |
| ReDoS em `@Pattern` regex | DoS | Regex de telefone simples `^\\d{10,15}$` — sem alternation/backtracking exponencial |
| Janela 24h race (TOCTOU) | Bypass control | PER-07 Phase 2 `REQUIRES_NEW + NOW()` ja escreveu; Phase 4 le via native query (skip JPA cache) — committed read garantido (PITFALLS C-01) |
| Custom mensagens_log injection via texto/caption | Tampering / Data integrity | Bean Validation `@Size` limita; `@Column(name="conteudo", columnDefinition="TEXT")` com Spring Data JPA escapa via PreparedStatement automatico |
| Bytes binarios em log | Information disclosure | NUNCA logar `bytes` direto — logar apenas `hash`, `size`, `mime`, `filename` |
| Botoes/Lista contendo PII no `id` (nao apenas no `title`) | Information disclosure (id volta em webhook payload de cliente) | Convencao: `id` deve ser keyword + numero opaco (`"aprovar 1234"`), NAO dados de cliente. Documentar em RUNBOOK Phase 6. |
| Custom validation skip via reflection/Jackson | Bypass control | `@Valid` no controller forca Jakarta antes de qualquer logic; records imutaveis impedem reassignment apos validation. |

## Sources

### Primary (HIGH confidence)
- `[VERIFIED: api-whatsapp/pom.xml]` — confirmacao de versoes Resilience4j 2.2.0, spring-boot-starter-aop, WireMock 3.10.0, Boot 3.5.9
- `[VERIFIED: api-whatsapp/src/main/resources/application.yml]` — bloco resilience4j erp-callback como modelo
- `[VERIFIED: api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/ErpCallbackClient.java]` — pattern annotation-driven com fallback no @Retry, Bearer per-request
- `[VERIFIED: api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/IdempotencyService.java]` — pattern save+catch DataIntegrityViolationException para race
- `[VERIFIED: api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/ClienteZapService.java]` — pattern native @Query + REQUIRES_NEW
- `[VERIFIED: api-whatsapp/src/test/java/.../service/ErpCallbackClientTest.java]` — pattern WireMock 3.10.0 + `cbRegistry.find().reset()` reusable
- `[VERIFIED: lib-shared/src/main/java/br/com/erpkit/shared/dto/ErrorResponse.java]` — shape atual SEM campo `codigo`; mudanca compativel necessaria
- `[VERIFIED: api-whatsapp/src/main/java/br/com/erpkit/whatsapp/repository/MediaCacheRepository.java]` — `findByArquivoHashAndExpiraEmAfter` ja existe
- `[CITED: developers.facebook.com/docs/whatsapp/cloud-api/reference/messages]` — payloads texto/document/interactive
- `[CITED: developers.facebook.com/docs/whatsapp/cloud-api/messages/interactive-reply-buttons-messages]` — limites buttons (3 max, title 20 chars, id 256 chars)
- `[CITED: developers.facebook.com/docs/whatsapp/cloud-api/messages/interactive-list-messages]` — limites list (10 sections, 10 rows total)
- `[CITED: developers.facebook.com/docs/whatsapp/cloud-api/reference/media]` — multipart upload field names: messaging_product/file/type
- `[CITED: github.com/resilience4j/resilience4j/issues/2383]` — Spring Boot 3 default aspect order: Retry outside CircuitBreaker; HIGHEST_PRECEDENCE para custom aspect
- `[CITED: jakarta.ee/specifications/bean-validation/3.0]` — `@AssertTrue` em metodo `isXxx()` publico
- `[CITED: docs.spring.io/spring-framework MultipartBodyBuilder + FormHttpMessageConverter]` — multipart Java/Spring patterns

### Secondary (MEDIUM confidence)
- `[CITED: baeldung.com/spring-rest-template-multipart-upload]` — exemplos `MultiValueMap<String, Object>` para multipart
- `[CITED: dev.to/akdevcraft retry circuit breaker spring boot]` — confirmacao annotation order irrelevante; aspect-order properties controla
- Phase 3 03-04 SUMMARY — fallback no @Retry empiricamente validado neste codebase

### Tertiary (LOW confidence — nao critico)
- Web search "Cloud API v22.0 vs v23.0" — payloads sao stable across v20-v23; PROJECT.md trava em v22.0 mas v23 nao quebraria

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — todas dependencies verificadas em pom.xml; padroes Phase 3 ja empiricamente validados
- Architecture: HIGH — patterns 1-5 sao reaproveitamento direto de Phase 2 (race save+catch) e Phase 3 (Resilience4j annotation-driven, Bearer per-request, native query); pattern 2 (custom aspect HIGHEST_PRECEDENCE) tem documentacao Resilience4j explicita + validacao planejada empirica
- Pitfalls: HIGH — pitfalls 1-3 sao herdados de Phase 3 (ja validados); pitfalls 4-6 documentados em PITFALLS.md + Wave 0 spike valida 4-5
- Cloud API payloads: HIGH (texto, documento) / MEDIUM (interactive button/list — limites confirmados via web search 2026 + Cognigy reference docs)
- Security: HIGH — apenas reuso de PITFALLS C-01/C-09/C-14 ja enderecados; novo controle: V5 Input Validation via Bean Validation Jakarta

**Research date:** 2026-05-05
**Valid until:** 2026-06-05 (30 dias — domain stable: Cloud API v22.0+, Spring Boot 3.5.9, Resilience4j 2.2.0; revalidar se Phase 4 atrasar >30 dias)
