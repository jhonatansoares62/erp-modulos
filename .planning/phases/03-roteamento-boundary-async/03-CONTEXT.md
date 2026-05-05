# Phase 3: Roteamento + Boundary Async - Context

**Gathered:** 2026-05-05
**Status:** Ready for planning
**Mode:** `--auto` (user delegated end-to-end execution)

<domain>
## Phase Boundary

Refatorar `MensagemService` da Phase 2 (sincrono) para o pattern **ack-first / process-later**: webhook sincrono so faz HMAC + parse + idempotency + persistencia + return 200; o resto (cliente identification, atualizar timestamp, media download, ERP callback) roda em `@Async` event listener disparado por `ApplicationEventPublisher`. Adicionar `ErpCallbackClient` com Resilience4j circuit breaker + retry exponencial. Adicionar media download (URL Meta expira em 5 min — primeira acao apos ack). Sem retry no callback (ERP pode ter executado parcialmente — PITFALLS).

**Em escopo:**
- `pom.xml` da api-whatsapp: adicionar `io.github.resilience4j:resilience4j-spring-boot3` + `io.github.resilience4j:resilience4j-reactor` (versao gerenciada pelo parent ja em 2.2.0)
- `application.yml`: bloco `resilience4j.circuitbreaker.instances.erp-callback.*` + `resilience4j.retry.instances.erp-callback.*`
- `WhatsAppProperties`: ja tem `erpCallbackUrl` + `callbackTimeout`. Sem novos campos.
- `WhatsAppApplication.java`: anotar com `@EnableAsync` para o `@Async` listener
- `config/AsyncConfig.java`: `@Configuration` com `@Bean ThreadPoolTaskExecutor` configurado (corePool=2, maxPool=10, queueCapacity=100, threadName=`whatsapp-async-`)
- `event/MensagemPersistidaEvent.java`: record imutavel `(String wamid, String telefone, String tipo, String conteudo, String mediaId, Long idClienteErp)` — disparado apos persistencia bem-sucedida
- `service/ComandoExtractor.java`: extrai keyword `comando` de uma mensagem entrante. text → primeira palavra, button_reply → id, list_reply → id, document/image/audio → tipo literal
- `service/MetaMediaClient.java`: cliente HTTP que (1) GET `graph.facebook.com/v22.0/{media_id}` para obter URL temporaria, (2) GET URL para baixar bytes. Bearer accessToken em header. Returns `byte[]` ou empty se 404 (URL expirada). Sem retry — primeira acao apos ack precisa ser rapida.
- `service/ErpCallbackClient.java`: HTTP `RestClient` com `@CircuitBreaker(name="erp-callback")` + `@Retry(name="erp-callback")` Resilience4j. POST `${erpCallbackUrl}/api/modulos/whatsapp/comando` com body `ComandoCallbackDTO`. Timeout via RestClient. Sem retry no fallback (per ROU-03).
- `dto/ComandoCallbackDTO.java`: record `(String telefone, String comando, String payload, Long idCliente, String mediaBase64, String mediaMimeType, String mediaFilename)`. mediaBase64 nullable.
- `service/MensagemAsyncListener.java`: `@Async @TransactionalEventListener(phase = AFTER_COMMIT)` ouvindo `MensagemPersistidaEvent`. Sequencia: 1. download media (se tem mediaId), 2. clienteZapService.identificar(telefone), 3. clienteZapService.atualizarUltimaMensagemEm(telefone), 4. comandoExtractor.extrair(...), 5. erpCallbackClient.despachar(payload). Captura excecao em cada step + log estruturado, nao propaga.
- `service/MensagemService.java`: refatorar `processarWebhook` para fast-path sync (parse + idempotency + persist + dispatch event); ja nao chama identificar/atualizar — listener async faz.
- Tests: `MensagemAsyncListenerTest` (unit), `ErpCallbackClientTest` (unit + WireMock), `MetaMediaClientTest` (WireMock para Graph API), `WebhookAsyncIntegrationTest` (E2E com WireMock para ERP + Meta media + assercao de tempo <1s do POST)

**Fora de escopo:**
- `WhatsAppCloudClient` (envio outbound) — Phase 4
- `WindowEnforcementService` 24h — Phase 4
- `MediaCacheService` (sha256 → media_id, TTL 30d) — Phase 4 (Phase 3 baixa media de ENTRADA, nao SAIDA)
- `lib-whatsapp-client` — Phase 5
- Persistencia de bytes da media — Phase 3 baixa em-memoria e passa pro ERP via callback. Se ERP precisar reusar, ERP guarda. api-whatsapp NAO armazena media de entrada.

</domain>

<decisions>
## Implementation Decisions

### D-01: Pattern ack-first via `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`

`MensagemService.processarWebhook(byte[])` reescrito para o caminho fast-path sincrono apenas:
```java
public void processarWebhook(byte[] rawBody) {
    ParsedWebhook parsed = parser.extrair(rawBody);   // sync
    for (MensagemEntranteDTO m : parsed.mensagens()) {
        boolean novo = idempotencyService.tentarPersistir(...); // sync
        if (novo) {
            eventPublisher.publishEvent(new MensagemPersistidaEvent(
                m.wamid(), m.telefone(), m.tipo(), m.conteudo(), m.mediaId(), null));
        }
    }
    // statuses ignorados (Phase 4 territory)
}
```

`MensagemAsyncListener.aoMensagemPersistida(...)`:
```java
@Async("whatsappTaskExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void aoMensagemPersistida(MensagemPersistidaEvent event) {
    String mediaBase64 = null;
    String mediaMimeType = null;
    String mediaFilename = null;
    if (event.mediaId() != null) {
        try {
            MetaMediaResultado media = metaMediaClient.baixar(event.mediaId());
            if (media != null) {
                mediaBase64 = Base64.encode(media.bytes());
                mediaMimeType = media.mimeType();
                mediaFilename = media.filename();
            }
        } catch (Exception e) {
            log.warn("Falha ao baixar media: wamid={}, mediaId={}: {}", event.wamid(), event.mediaId(), e.getMessage());
            // Prosseguir sem media; ERP recebe payload sem mediaBase64.
        }
    }
    
    ClienteZap cliente;
    try {
        cliente = clienteZapService.identificar(event.telefone());
    } catch (Exception e) {
        log.error("Falha ao identificar cliente: telefone={}: {}", event.telefone(), e.getMessage(), e);
        return;
    }
    
    try {
        clienteZapService.atualizarUltimaMensagemEm(event.telefone());
    } catch (Exception e) {
        log.error("Falha ao atualizar ultima_mensagem_em: telefone={}: {}", event.telefone(), e.getMessage(), e);
        return;
    }
    
    String comando = comandoExtractor.extrair(event.tipo(), event.conteudo());
    if (comando == null) {
        log.debug("Sem comando para tipo={}, ignorando dispatch ERP", event.tipo());
        return;
    }
    
    ComandoCallbackDTO payload = new ComandoCallbackDTO(
        event.telefone(), comando, event.conteudo(), cliente.getIdClienteErp(),
        mediaBase64, mediaMimeType, mediaFilename);
    try {
        erpCallbackClient.despachar(payload);
    } catch (Exception e) {
        log.error("Falha definitiva no callback ERP (apos retry+CB): wamid={}, comando={}: {}",
            event.wamid(), comando, e.getMessage(), e);
        // Sem retry adicional, sem envio de resposta — ERP pode ter executado parcialmente (ROU-03).
    }
}
```

**Por que `@TransactionalEventListener(AFTER_COMMIT)`:** Per PITFALLS C-05, evento dispara APOS commit do INSERT em mensagens_log. Se commit falhar (e.g., DB indisponivel), event nao publica → async listener nunca roda → ERP nao recebe falsa positivo. Spring's transactional event semantics garantem isso.

**Por que `@Async`:** Lib-side, listener executa em thread pool dedicado. Webhook controller ja retornou 200 antes do listener iniciar. Tempos de I/O (media + ERP callback) nao afetam SC-1 do ROADMAP (<1s no POST).

**Trade-off ack-first:** Falhas pos-ack sao perdidas silenciosamente (so log). Mitigacao: log.error estruturado com wamid+comando para correlacao operacional. Phase 6 pode adicionar metric counters Micrometer.

### D-02: Configuracao `@EnableAsync` + ThreadPoolTaskExecutor dedicado

`WhatsAppApplication.java` ganha `@EnableAsync`. Novo `config/AsyncConfig.java`:
```java
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

**Listener anota:** `@Async("whatsappTaskExecutor")` para garantir uso do pool dedicado (nao default).

**Por que pool dedicado e nao SimpleAsyncTaskExecutor:** Default cria thread por task — em pico de mensagens, OOM risk. Pool com queueCapacity 100 + CallerRunsPolicy degrada graciosamente: sob estresse extremo, listener roda inline na thread chamadora (que e o async original, nao o webhook), mantendo ack-first valido.

### D-03: Resilience4j circuit breaker + retry no `ErpCallbackClient`

Pattern espelhando `lib-consultas-client` (ja existe no monorepo). `WhatsAppApplication` reusa pom.xml com Resilience4j adicionado:
```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

`application.yml`:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      erp-callback:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 3
        register-health-indicator: true
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
```

`ErpCallbackClient`:
```java
@Service
public class ErpCallbackClient {
    private final RestClient restClient;
    private final WhatsAppProperties properties;
    
    public ErpCallbackClient(WhatsAppProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
            .baseUrl(properties.getErpCallbackUrl())
            .requestFactory(timeoutFactory(properties.getCallbackTimeout()))
            .build();
    }
    
    @CircuitBreaker(name = "erp-callback", fallbackMethod = "fallbackDespachar")
    @Retry(name = "erp-callback")
    public void despachar(ComandoCallbackDTO payload) {
        restClient.post()
            .uri("/api/modulos/whatsapp/comando")
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .toBodilessEntity();
    }
    
    private void fallbackDespachar(ComandoCallbackDTO payload, Throwable t) {
        log.error("ERP callback falhou apos retry+CB: telefone={}, comando={}: {}",
            payload.telefone(), payload.comando(), t.getMessage());
        // Nao re-throw — ack-first principle.
    }
    
    private ClientHttpRequestFactory timeoutFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        return factory;
    }
}
```

**Por que `@CircuitBreaker` + `@Retry` annotations e nao programatico:** Espelha exatamente o padrao de `lib-consultas-client/ConsultasClientImpl.java`. Aspect-driven (proxy AOP) mantém código limpo. Resilience4j Spring Boot autoconfig detecta annotations.

**Retry semantics:** retentativa apenas para transient errors (5xx, timeout, IOException) — NAO 4xx categoricos (config explicita `retry-exceptions`). Cliente perdeu callback definitivo: log error + ERP nunca executa novamente (per ROU-03 — risk de duplicate side effect no ERP).

### D-04: Media download como PRIMEIRA acao do listener (5 min URL expiry)

PITFALLS C-08: URL Meta expira em 5 minutos. Async listener pode ter delay de queue se em pico. Solucao: media download e a primeira coisa apos `@TransactionalEventListener` invocar. Apenas se mediaId presente.

`MetaMediaClient`:
```java
@Service
public class MetaMediaClient {
    private final RestClient restClient;
    private final WhatsAppProperties properties;
    
    public MetaMediaClient(WhatsAppProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
            .baseUrl("https://graph.facebook.com/v22.0")
            .build();
    }
    
    public Optional<MetaMediaResultado> baixar(String mediaId) {
        // Step 1: GET /{media_id} → URL temporaria
        MediaMetadataDTO metadata;
        try {
            metadata = restClient.get()
                .uri("/{id}", mediaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                .retrieve()
                .body(MediaMetadataDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Media expirada (5min): mediaId={}", mediaId);
            return Optional.empty();
        }
        if (metadata == null || metadata.getUrl() == null) {
            return Optional.empty();
        }
        
        // Step 2: GET URL → bytes
        byte[] bytes = restClient.get()
            .uri(metadata.getUrl())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
            .retrieve()
            .body(byte[].class);
        
        return Optional.of(new MetaMediaResultado(bytes, metadata.getMime_type(), metadata.getFilename()));
    }
}
```

`MetaMediaResultado` record `(byte[] bytes, String mimeType, String filename)`.
`MediaMetadataDTO` regular class com getters: `String url`, `String mime_type`, `String filename`, `String sha256`, `Integer file_size`, `String id`.

**404 graceful:** PITFALLS C-08 — URL Meta pode estar expirada quando listener finalmente roda. log.warn + retorna empty; mensagem ainda esta persistida (Phase 2), so nao tem bytes. ERP recebe callback sem `mediaBase64`.

**Sem Resilience4j:** primeira acao apos ack precisa ser rapida; circuit breaker adicionaria latencia. Falha = mensagem persistida sem bytes; nao critico.

**Por que Bearer header e nao access_token query param:** PITFALLS C-14. Header nunca aparece em URL/log; query param vaza em logs.

### D-05: Comando extraction simples baseado em tipo

`ComandoExtractor`:
```java
@Service
public class ComandoExtractor {
    public String extrair(String tipo, String conteudo) {
        return switch (tipo) {
            case TipoMensagem.TEXT -> primeiraPalavra(conteudo);
            case TipoMensagem.INTERACTIVE_BUTTON, TipoMensagem.INTERACTIVE_LIST -> idDeInteractive(conteudo);
            case TipoMensagem.DOCUMENT, TipoMensagem.IMAGE, TipoMensagem.AUDIO, TipoMensagem.VIDEO -> tipo;
            default -> null;  // desconhecido: skip dispatch
        };
    }
    
    private String primeiraPalavra(String conteudo) {
        if (conteudo == null || conteudo.isBlank()) return null;
        return conteudo.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
    }
    
    private String idDeInteractive(String conteudo) {
        // Phase 2 parser formata interactive como "id|title"
        if (conteudo == null) return null;
        int sep = conteudo.indexOf('|');
        return sep > 0 ? conteudo.substring(0, sep).toLowerCase(Locale.ROOT) : null;
    }
}
```

**Por que simples e nao NLP:** PROJECT.md "Out of Scope": AI/LLM/NLP para interpretar comandos. Keywords simples sao suficientes pelo design. Lib-whatsapp-client (Phase 5) adiciona registry com prefix matching ("aprovar 1234" → handler "aprovar").

### D-06: ComandoCallbackDTO com mediaBase64 (nao filesystem)

ComandoCallbackDTO record:
```java
public record ComandoCallbackDTO(
    String telefone,
    String comando,
    String payload,        // texto cru (text body, ou interactive id|title)
    Long idCliente,        // null se nao mapeado
    String mediaBase64,    // null se sem media
    String mediaMimeType,
    String mediaFilename
) {}
```

**Por que base64 e nao filesystem:** Filesystem path expoe acoplamento entre processos (ERP precisa acesso ao mesmo disco). Base64 e self-contained no payload. Trade-off: PDFs grandes (~10MB) viram ~13MB base64 — aceitavel para callback localhost.

**Por que nao multipart/form-data:** RestClient + Resilience4j combina melhor com JSON simples. Multipart adiciona complexidade de ConverterFactory.

### D-07: Refatorar MensagemService — remover sync de cliente identification + atualizar timestamp

Phase 2 fazia tudo sincrono dentro de processarWebhook. Phase 3 muda para fast-path:
- Parse → idempotency → persist (sync)
- Disparar event → return
- Listener async faz: media download → identificar → atualizar timestamp → ERP callback

Tests existentes da Phase 2 (`MensagemServiceTest`, `WebhookPersistenciaIntegrationTest`) **provavelmente quebram** quando o flow async toma conta. Estrategia:
- `MensagemServiceTest` — refatorar pra mockar `ApplicationEventPublisher` + verificar event publicado (nao chamadas a clienteZapService). Os 4 tests viram 4 novos verificando `publishEvent` (ou `verifyNoInteractions(clienteZap)` no caminho sync).
- `WebhookPersistenciaIntegrationTest` — usar `@SpringBootTest` com `@DirtiesContext` ou aguardar async via `Awaitility` com timeout (ex: 5s). Tests SC-3, SC-4, SC-5 que dependem de cliente_zap row precisam esperar listener completar antes de assertar DB. Ou usar `TaskExecutor` sync no test profile (override executor com `SyncTaskExecutor`).

**Decisao para tests:** test profile sobrescreve `whatsappTaskExecutor` com `SyncTaskExecutor` (Spring built-in) — async listener vira sincrono em test. Tests existentes continuam verificando o estado do DB sem timing flake. Producao usa pool real.

### D-08: Sem retry no callback ERP fallback (ROU-03)

Quando Resilience4j esgota retries (3 tentativas) e circuit abre OU 4xx categorico chega, fallback method NAO retenta — apenas loga error estruturado. ERP pode ter executado parcialmente (e.g., handler do ERP ja marcou orcamento como "visualizado" antes de cair); retry pode duplicar side effects.

**Trade-off:** mensagem do cliente nao recebe resposta. Cliente pode reenviar mensagem (Meta delivery `> 24h` recovery). Phase 6 tera RUNBOOK documentando como diagnosticar (logs com wamid).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Pitfalls
- `.planning/research/PITFALLS.md` §C-05 — Synchronous webhook processing causes Meta retry storms (ack-first pattern obrigatorio)
- `.planning/research/PITFALLS.md` §C-06 — wamid concurrent delivery (gate de dispatch via row-count → no Phase 3 e via boolean novo do IdempotencyService.tentarPersistir)
- `.planning/research/PITFALLS.md` §C-08 — Media download URL 5min expiry (primeira acao apos ack)
- `.planning/research/PITFALLS.md` §C-09 — Bearer token in logs (mascarar Authorization)
- `.planning/research/PITFALLS.md` §C-14 — media_id URL Bearer token leak (header, nao query param)

### Arquitetura
- `.planning/research/ARCHITECTURE.md` §"Pattern 1: Webhook-First com Resposta 200 Imediata" — async dispatch
- `.planning/research/ARCHITECTURE.md` §"Anti-Pattern 1: Enviar saída dentro da mesma transação" — alinhar
- `.planning/research/ARCHITECTURE.md` §"Component Responsibilities" — `MessageRouter`, `ErpCallbackClient`
- `.planning/research/ARCHITECTURE.md` §"Data Flow — Fluxo Inbound Completo" steps 5-9
- `.planning/PROJECT.md` §"Active" — ROU-01..05 mapeados
- `.planning/REQUIREMENTS.md` §"Roteamento" — ROU-01..05 (locked)
- `.planning/ROADMAP.md` §"Phase 3" — 5 success criteria
- `.planning/phases/02-persistencia-idempotencia/02-CONTEXT.md` — Phase 2 herdada (D-02 idempotency fallback save+catch — Phase 3 reusa)
- `.planning/phases/02-persistencia-idempotencia/02-VERIFICATION.md` — Phase 2 sound

### Padroes do codebase
- `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/ConsultasClientImpl.java` — modelo de Resilience4j `@CircuitBreaker` + `@Retry`
- `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/ConsultasClientAutoConfiguration.java` — modelo de auto-config (Phase 5 territory mas referencia)
- `api-email/src/main/java/br/com/erpkit/email/service/EmailService.java` — exemplo de `@Async` com `@Scheduled` no monorepo (similar enough)
- Phase 2 entregue: MensagemService, IdempotencyService, ClienteZapService, WebhookPayloadParser

### Convencoes
- `.planning/codebase/CONVENTIONS.md` — PT-BR, sem Lombok, fields/methods camelCase
- `application.yml` Phase 1 ja configura Resilience4j NUNCA — Phase 3 e o primeiro a habilitar

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MensagemService.processarWebhook(byte[])` (Phase 2) — entry point, Phase 3 modifica internals mas mantem signature
- `IdempotencyService.tentarPersistir` (Phase 2) — retorna `boolean novo`, ja é o gate de dispatch (SC-5 do roadmap menciona "row-count do ON CONFLICT" mas o equivalent semantico e exato: `boolean novo` ja descrito no Phase 2 fallback)
- `ClienteZapService.identificar` + `atualizarUltimaMensagemEm` (Phase 2) — listener async chama; cross-bean call ja implementado, REQUIRES_NEW continua funcionando
- `WebhookPayloadParser` (Phase 2) — sem mudanca
- `WhatsAppProperties.erpCallbackUrl` + `callbackTimeout` (Phase 1) — usado pelo ErpCallbackClient
- `WhatsAppProperties.accessToken` (Phase 1) — usado pelo MetaMediaClient (Bearer)
- `lib-consultas-client/ConsultasClientImpl.java` — copy-paste-adapt para ErpCallbackClient
- ack-first defensivo do controller (Phase 2 Wave D) — ja captura excecoes e retorna 200; mantem em Phase 3

### Established Patterns
- `@Service` constructor injection
- `@CircuitBreaker(name=...) @Retry(name=...) fallbackMethod=...` — pattern de lib-consultas-client
- `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async` (Spring standard)
- `ApplicationEventPublisher` injetado no service para publicar eventos
- Logger SLF4J sem dados sensiveis em log
- RestClient (modern Spring 6+) over RestTemplate

### Integration Points
- **MensagemService → MensagemAsyncListener:** via `MensagemPersistidaEvent`. Loosely coupled.
- **MensagemAsyncListener → ClienteZapService:** cross-bean call (REQUIRES_NEW funciona)
- **MensagemAsyncListener → MetaMediaClient:** novo cliente HTTP
- **MensagemAsyncListener → ErpCallbackClient:** novo cliente HTTP com Resilience4j
- **Phase 4 dependency:** `WhatsAppCloudClient` (Phase 4) tambem usara Resilience4j; mesma config base
- **Test profile:** override `whatsappTaskExecutor` com `SyncTaskExecutor` para tests E2E manterem assertions DB sincronas

</code_context>

<specifics>
## Specific Ideas

- **Pool dedicado** com queueCapacity=100 + CallerRunsPolicy degrada graciosamente em pico
- **`@TransactionalEventListener(AFTER_COMMIT)`** garante consistency: event so dispara apos commit do INSERT
- **Sync executor em test profile** elimina flake de Awaitility/timing
- **Media download como primeira acao** — minimiza risco de 5min expiry
- **Sem retry no callback** — proteger ERP de duplicate side effects
- **Bearer no header**, nunca query param (PITFALLS C-14)
- **Logger mascara accessToken** em error paths — se Resilience4j logar response, accessToken NAO aparece (header masking via interceptor)

</specifics>

<deferred>
## Deferred Ideas

- **WhatsAppCloudClient outbound** — Phase 4
- **WindowEnforcementService 24h** — Phase 4 (le `ultima_mensagem_em` que Phase 2/3 atualizam)
- **MediaCacheService outbound** (sha256→media_id, TTL 30d) — Phase 4 (Phase 3 baixa media de entrada, nao cacha)
- **lib-whatsapp-client** SPI/registry — Phase 5
- **Persistencia de bytes da media de entrada** (filesystem ou blob) — fora desta milestone (api-whatsapp passa bytes pro ERP, ERP decide armazenar)
- **Dead letter queue para callback ERP falhas definitivas** — Phase 6+ (RUNBOOK pode documentar como recuperar manualmente via logs com wamid)
- **Metric counters Micrometer** (callbacks succeeded/failed/circuit_open) — Phase 6
- **Persistencia de statuses Meta** — backlog opcional

</deferred>

---

*Phase: 3-Roteamento + Boundary Async*
*Context gathered: 2026-05-05*
