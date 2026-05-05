# Phase 4: Outbound + Trava 24h + WhatsAppController - Context

**Gathered:** 2026-05-05
**Status:** Ready for planning
**Mode:** User delegou as 4 gray areas para Claude apos ver o menu ("pode implementar da melhor maneira que encontrar")

<domain>
## Phase Boundary

O ERP consegue enviar 4 tipos de mensagem outbound (texto, documento, botoes, lista) para clientes WhatsApp via 5 endpoints internos do `api-whatsapp` (`POST /api/whatsapp/enviar-{texto,documento,botoes,lista}` + `GET /api/whatsapp/status`), com **custo zero garantido por arquitetura**:

1. **Trava #1 (ausencia de codigo):** `WhatsAppCloudClient` simplesmente nao implementa `enviarTemplate()` — sem flag, sem feature toggle. Busca por `template` no codigo do cliente retorna zero hits.
2. **Trava #2 (hard 409):** Antes de cada chamada Cloud API, aspect AOP intercepta e `WindowEnforcementService` consulta `clientes_zap.ultima_mensagem_em` (ja populado pela Phase 2 PER-07 com REQUIRES_NEW + DB NOW()) via query nativa fora da transacao do webhook. Se diff > 24h: `JanelaConversaFechadaException` → HTTP 409 `codigo=JANELA_24H_FECHADA` antes de qualquer byte ir para o Meta.

`MediaCacheService` reusa `media_id` por sha256 do conteudo (TTL estrito 30d) evitando reupload do mesmo PDF. Resilience4j retry 3x exponencial (1s/2s/4s) para 5xx/timeout via instance dedicada `whatsapp-cloud`; 4xx categorico (400/401/403) **nao** retenta. Outbound persiste em `mensagens_log` com `direcao=out` + `wamid` retornado **apenas apos sucesso** (OUT-09).

**Em escopo:**
- `service/WhatsAppCloudClient.java` — Spring `RestClient` com 4 metodos publicos (`enviarTexto`, `enviarDocumento`, `enviarBotoes`, `enviarLista`). Bearer header per-request (PITFALLS C-14, alinhado D-04 Phase 3). `@CircuitBreaker(name="whatsapp-cloud") @Retry(name="whatsapp-cloud") fallbackMethod=...` em cada metodo (fallback no @Retry, NAO no @CircuitBreaker — gotcha empiricamente descoberto em 03-04). **NAO existe metodo `enviarTemplate`** (OUT-05).
- `service/MediaCacheService.java` — `Optional<String> buscarMediaId(byte[] bytes)` calcula sha256 hex, le `media_cache.arquivo_hash` PK, retorna media_id se `expira_em > now()` (hit estrito sem sliding). `String registrarUpload(byte[] bytes, String mediaId)` insere ou atualiza com `expira_em = now() + 30d`. Race em concurrent reupload: catch `DataIntegrityViolationException` + re-fetch (mesmo pattern do `IdempotencyService` Phase 2).
- `service/WindowEnforcementService.java` — `void verificarJanela(String telefone)` faz native `SELECT ultima_mensagem_em FROM whatsapp.clientes_zap WHERE telefone = ?` (pula JPA L1 cache per OUT-06), compara com `now() - 24h`. Se nulo (cliente nunca recebeu mensagem) ou diff > 24h: lanca `JanelaConversaFechadaException`.
- `aspect/JanelaEnforcementAspect.java` — `@Around` advice em `@JanelaProtegida` annotation. Le `args[0]` como `String telefone` (convencao: telefone sempre primeiro arg em `enviar*`). Invoca `WindowEnforcementService.verificarJanela` antes do `proceedingJoinPoint.proceed()`. Order `Ordered.HIGHEST_PRECEDENCE` para rodar **fora** dos retries Resilience4j (1 check por chamada, nao por tentativa).
- `aspect/JanelaProtegida.java` — annotation marker `@interface JanelaProtegida { }` (sem atributos — telefone por convencao posicional).
- `controller/WhatsAppController.java` — 5 endpoints `@RestController @RequestMapping("/api/whatsapp")`:
  - `POST /enviar-texto` — body `EnviarTextoRequest{telefone, texto}` (`@Valid`), retorna `EnvioResponse{wamid}` (200) ou `ErrorResponse{codigo, mensagem, metaErrorCode?}` (4xx/5xx)
  - `POST /enviar-documento` — body **JSON** `EnviarDocumentoRequest{telefone, mediaBase64, mimeType, filename, caption?}` (`@Valid` + `@NotBlank` + `@Size`), Jackson decode base64. Cache lookup por sha256(bytes). Hit → reuso media_id. Miss → upload Meta + cache + send.
  - `POST /enviar-botoes` — body `EnviarBotoesRequest{telefone, texto, botoes:List<BotaoDto>{id, title}}` (`@Valid` + `@Size(max=3)` em botoes; falha early 400 se >3)
  - `POST /enviar-lista` — body `EnviarListaRequest{telefone, texto, secoes:List<SecaoDto>{titulo, itens:List<ItemDto>{id, title, description?}}}` (`@Valid`); validator custom soma itens em todas secoes, falha 400 se total > 10
  - `GET /status` — retorna `StatusResponse{status:String, circuitBreakerState:String, phoneNumberId:String}` (minimo v1; expansao Phase 6 se necessario)
- `exception/JanelaConversaFechadaException.java` — `extends ModuloException` com `HttpStatus.CONFLICT` (409); `codigo=JANELA_24H_FECHADA` no body via `ErrorResponse`. **GlobalExceptionHandler em lib-shared ja mapeia `ModuloException`** — se nao mapeia `codigo`, adicionar campo no `ErrorResponse` (lib-shared) — verificar primeiro ao planejar.
- `exception/MetaApiException.java` — `extends ModuloException`, carrega `metaErrorCode` (Integer extraido do response body Meta). Mapeada para HTTP code conforme tipo (ver Decisao D-02).
- DTOs: `EnviarTextoRequest`, `EnviarDocumentoRequest`, `EnviarBotoesRequest` + `BotaoDto`, `EnviarListaRequest` + `SecaoDto` + `ItemDto`, `EnvioResponse`, `StatusResponse`.
- Repository: estender `MensagemLogRepository` com `salvarSaida(String wamid, String telefone, String tipo, String conteudo, String mediaId)` ou simplesmente reusar `repository.save(MensagemLog)` da Phase 2 com `direcao=Direcao.out`.
- Repository: `MediaCacheRepository` — adicionar `findByArquivoHashAndExpiraEmAfter(String hash, Instant now)` ou similar.
- `application.yml`: bloco `resilience4j.circuitbreaker.instances.whatsapp-cloud.*` + `resilience4j.retry.instances.whatsapp-cloud.*` (espelha `erp-callback` da Phase 3, sliding-window=10, threshold=50, wait-open=60s, retry max=3, exponential 2.0, retry-exceptions explicito incluindo `ResourceAccessException` per descoberta empirica 03-04).
- Tests: `WhatsAppCloudClientTest` (WireMock 3.10.0 — pattern validado em 03-03/03-04, todos os 4 envios + 4xx + 5xx + retry counter assertion + circuit-open), `MediaCacheServiceTest` (hit/miss/expirado/race), `WindowEnforcementServiceTest` (janela aberta/fechada/cliente novo), `JanelaEnforcementAspectTest` (interceptor invocado/excecao propagada/order vs Resilience4j), `WhatsAppControllerTest` (`@WebMvcTest` validation 400 vs 200 vs 409 vs 502).

**Fora de escopo (Phase 5+):**
- `lib-whatsapp-client/` (auto-config, SPI `WhatsAppCommandHandler`, `WhatsAppCommandRegistry`, ObjectProvider graceful fallback) — **Phase 5**
- README.md / RUNBOOK.md / SpringDoc OpenAPI exhaustivo — **Phase 6** (Phase 4 garante so build verde + endpoints documentados via Spring conventions)
- E2E real com Meta API (verificacao Business + numero de teste) — **milestone seguinte** (D7)
- Engate ERP-MUDAS (`ModulosController` proxy + handlers `OrcamentoCommandHandler` etc.) — outro repo, outro GSD project
- Cliente piloto MUDAS — milestone seguinte
- Listener proativo de eventos do ERP (D3 reativo puro) — proibido por design
- Reenvio automatico fora janela 24h (D5) — proibido por design

</domain>

<decisions>
## Implementation Decisions

### D-01: Contrato `enviar-documento` — JSON+base64 (NAO multipart/form-data)

ERP envia bytes do PDF/arquivo como base64 dentro de JSON regular:
```java
public record EnviarDocumentoRequest(
    @NotBlank String telefone,
    @NotBlank String mediaBase64,
    @NotBlank String mimeType,
    @NotBlank String filename,
    String caption
) {}
```

Controller decodifica via `Base64.getDecoder().decode(req.mediaBase64())` → `byte[] bytes`. Pipeline: `sha256(bytes)` → `mediaCacheService.buscarMediaId(bytes)` → hit ou miss → upload `POST /{phoneNumberId}/media` (multipart **interno** entre `WhatsAppCloudClient` e Meta — usuario do api-whatsapp nao ve isso) → `mediaCacheService.registrarUpload(...)` → `enviarDocumento(telefone, mediaId, caption)` → persiste outbound.

**Por que JSON+base64 e nao multipart entre ERP e api-whatsapp:**
- Alinhamento com `ComandoCallbackDTO.mediaBase64` (Phase 3 D-06): ERP ja vai ter padrao `mediaBase64` no callback inbound; consistencia com outbound reduz cognitive load.
- RestClient + Resilience4j + JSON funcionam de fabrica; multipart exigiria `HttpMessageConverter` adicional e tem trade-offs com retry (body byte[] reusable em retry).
- Validation Jakarta `@NotBlank` + `@Size(max=...)` funciona naturalmente em campo String.
- Custo (33% inflation: PDF 10MB → base64 ~13MB) e localhost loopback — irrelevante.
- Test pattern simples: `MockMvc.perform(post().contentType(JSON).content(om.writeValueAsString(...)))` vs multipart hassle.

**Multipart Meta-side e detalhe interno:** `WhatsAppCloudClient.uploadMedia(byte[], mimeType, filename)` faz multipart para `graph.facebook.com/v22.0/{phoneNumberId}/media` internamente. ERP nunca ve multipart.

**Trade-off aceito:** PDFs muito grandes (>15MB) podem exceder limites de payload JSON; mitigacao: configurar Spring Boot `spring.servlet.multipart.max-request-size` mais alto (default 10MB e suficiente para v1; documentar em Phase 6 RUNBOOK se cliente piloto enviar PDFs grandes).

### D-02: Mapeamento erro Meta → ERP traduzido com `codigo` field (alinhado ModuloException pattern)

`MetaApiException` carrega `metaErrorCode` (Integer) e `tipo` enum (`{CATEGORIA_4XX, INDISPONIVEL_5XX, TIMEOUT, CIRCUIT_OPEN}`). Mapping table:

| Cenario | Resilience4j behavior | HTTP retornado ao ERP | `codigo` no body |
|---------|----------------------|----------------------|------------------|
| Janela > 24h | (aspect rejeita antes) | **409** | `JANELA_24H_FECHADA` (locked) |
| Validation request (>3 botoes, >10 itens, telefone vazio, base64 invalido) | n/a | **400** | `VALIDATION_ERROR` (Spring default + GlobalExceptionHandler.handleValidation) |
| Meta 400/401/403 (categorico) | NAO retenta | **422 Unprocessable Entity** | `META_ERROR` + `metaErrorCode` |
| Meta 5xx apos 3 retries | Esgota | **502 Bad Gateway** | `META_INDISPONIVEL` + `metaErrorCode` (ultimo) |
| Timeout apos 3 retries | Esgota | **504 Gateway Timeout** | `META_TIMEOUT` |
| Circuit aberto (`CallNotPermittedException`) | Curto-circuita | **503 Service Unavailable** | `CIRCUIT_OPEN` |

Implementacao: `WhatsAppCloudClient` lanca `MetaApiException(tipo, metaErrorCode, mensagem)`. `GlobalExceptionHandler` no `lib-shared` ja captura `ModuloException` e mapeia status code; estender para incluir `codigo` no `ErrorResponse` se ainda nao tiver. Verificar `ErrorResponse` shape antes — pode precisar adicionar campo `codigo` em lib-shared (mudanca compativel para outros modulos).

`fallbackMethod` no `@Retry` (NAO no `@CircuitBreaker` — gotcha 03-04 D-04 RESOLVED): converte `Throwable` em `MetaApiException` com tipo apropriado.

**Por que traduzir e nao pass-through:** ERP precisa diferenciar "meu request esta errado" (bug do ERP, nao retry) vs "Meta caiu" (transient, retry depois) vs "circuit aberto" (sistema rejeitando proactivamente). Pass-through 4xx Meta direto ao ERP (ex: 401 do Meta → 401 do api-whatsapp) confunde — 401 do api-whatsapp deveria significar API key do ERP errada, nao token Meta expirado. Traducao para 422 desambigua semantica HTTP.

**Por que `metaErrorCode` no body:** Meta retorna codigos numericos especificos (131026 invalid phone, 131009 token expired, etc.). Operador de RUNBOOK precisa do codigo para decidir acao. Logar + exposicao no body do erro permite ERP escalar suporte com info correta.

### D-03: Aspect via annotation marker `@JanelaProtegida` + telefone posicional (NAO pointcut por nome)

```java
// br.com.erpkit.whatsapp.aspect.JanelaProtegida
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JanelaProtegida { }

// br.com.erpkit.whatsapp.aspect.JanelaEnforcementAspect
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)  // fora dos retries Resilience4j
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
                "Metodo @JanelaProtegida deve ter telefone como primeiro argumento String");
        }
        windowService.verificarJanela(telefone);  // lanca JanelaConversaFechadaException se > 24h
        return pjp.proceed();
    }
}

// uso em WhatsAppCloudClient
@JanelaProtegida
@CircuitBreaker(name = "whatsapp-cloud", fallbackMethod = "...")
@Retry(name = "whatsapp-cloud")
public WamidResponse enviarTexto(String telefone, String texto) { ... }
```

**Por que annotation marker e nao pointcut por nome (`execution(* WhatsAppCloudClient.enviar*(..))`):**
- **Alinhamento com Resilience4j pattern do monorepo** (annotation-driven `@CircuitBreaker(name=...)` `@Retry(name=...)`): consistencia visual. Leitor ve 3 anotacoes na mesma linha e entende que ha 3 cross-cutting concerns aplicados.
- **Hard to forget when adding new methods:** se Phase 4+ adicionar `enviarReacao(...)` (hipotetico, nao escopo aqui), aspect por convencao posicional `enviar*` funcionaria silenciosamente — bom ou ruim. Annotation forca declaracao explicita: ou anota e entra no enforcement, ou nao anota e burla. Travar via teste: gate de grep `find . -name "*.java" | grep -L '@JanelaProtegida' | xargs grep -l 'graph.facebook'` no Phase 6 garante regression.
- **Telefone posicional (args[0] como String):** convencao forte e simples. Todos os 4 metodos publicos do `WhatsAppCloudClient` ja teriam `telefone` como primeiro argumento (REQUIREMENTS OUT-01..04). Aspect valida tipo no runtime + lanca `IllegalStateException` cedo se metodo violar convencao — fail-fast em test, nao em prod.
- **Nao usa annotation com atributo `telefoneArgIndex` ou `@TelefoneParam`:** complexidade desnecessaria para v1. Convencao posicional cobre 100% dos casos do escopo. Phase 4+ pode revisitar se aparecer metodo com signature diferente.

**Order HIGHEST_PRECEDENCE crucial:** garante que o aspect roda 1 vez por chamada (antes do Retry). Se rodasse dentro do retry loop, janela seria checada 3x — desperdiço + potencial inconsistencia em race entre retry 1 e retry 3 (24h boundary atravessado durante backoff). Ordem correta:
```
JanelaEnforcement (HIGHEST_PRECEDENCE) → Retry (LOWEST_PRECEDENCE-3) → CircuitBreaker (LOWEST_PRECEDENCE-2) → metodo real
```

Validacao empirica em test: contar `windowService.verificarJanela` invocations em test que dispara 3 retries — esperar 1 invocation (nao 3).

### D-04: MediaCacheService TTL estrito + Status endpoint minimal

**MediaCacheService:**
- `Optional<String> buscarMediaId(byte[] bytes)`: calcula sha256, query `findByArquivoHashAndExpiraEmAfter(hash, Instant.now())` (filtra expirados no banco, evita carregar Java side). Retorna media_id se hit; empty se miss ou expirado.
- `void registrarUpload(byte[] bytes, String mediaId)`: insere ou faz UPSERT — calcula sha256, tenta `repository.save(new MediaCache(hash, mediaId, now+30d))`. Se PK conflict (race com outro thread fazendo upload do mesmo arquivo): catch `DataIntegrityViolationException` + log debug + return (outro thread ja registrou; proxima leitura ve o registro). Pattern alinhado com `IdempotencyService` Phase 2 / `ClienteZapService.identificar` Phase 2.
- **TTL estrito:** hit = `expira_em > now()`. Sem sliding (nao estende `expira_em` em hit). Reupload natural quando expira → renova TTL para `now + 30d`.

**Por que TTL estrito e nao sliding:**
- **Previsibilidade:** sliding TTL pode manter media_id em cache indefinidamente se reusado regularmente; banco cresce monotonicamente. Estrito garante turnover de 30 em 30 dias — tabela bounded.
- **Meta TTL real:** Meta documenta media_id valido por ate 30 dias apos upload. Sliding nao prolonga validade real do media_id no Meta — apenas mascara expirar do nosso lado, levando a 4xx Meta surpresa (media_id invalid) que nao retentariamos.
- **Operacionalmente:** se cliente envia o mesmo PDF de orcamento toda semana, hit ate semana 4, miss na semana 5 → reupload + cache renova → hit ate semana 8. Custo: 1 upload extra a cada 4 semanas. Beneficio: cache nunca tem entries staler que TTL Meta real.

**Status endpoint minimal:**
```java
public record StatusResponse(
    String status,                  // "UP" | "DOWN"
    String circuitBreakerState,     // "CLOSED" | "OPEN" | "HALF_OPEN"
    String phoneNumberId            // sanity check ERPKit pode comparar com env var configurada
) {}
```

Sem `lastMessageMeta`, sem `totalMidiaCacheada`, sem `recentErrorCount`. Phase 6 expande se necessidade aparecer (PITFALLS C-12 sugere validar `subscribed_apps` via Graph — Phase 6 territory).

**Por que minimal:**
- v1 = "operador da ERPKit consegue diagnosticar circuit aberto e tem confianca que phoneNumberId esta certo".
- Mais campos = mais surface a manter sincronizada + mais teste. YAGNI.

### Claude's Discretion

User delegou todas as 4 areas para Claude apos ver o menu (mensagem literal: "pode implementar da melhor maneira que encontrar"). Decisoes acima sao defaults recomendados — todas reversiveis se a implementacao mostrar atrito. Pontos especialmente abertos a revisao se fricao surgir:

- **D-01 JSON+base64:** se PDFs em piloto MUDAS forem >15MB, reverter para multipart `MultipartFile` no controller (mantendo `MediaCacheService` byte-oriented internamente).
- **D-02 traducao 422:** se ERP tiver dificuldade distinguindo nossos codes, reduzir granularidade (tudo Meta-side falho vira 502).
- **D-03 annotation:** se aspect mostrar bug em Spring AOP self-call ou tipo args[0] muddle, fallback para pointcut explicito por package + nome.
- **D-04 status minimal:** primeiro feedback do operador piloto MUDAS pode pedir mais — Phase 6 expande baseado em real-world.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Pitfalls (criticos para Phase 4)
- `.planning/research/PITFALLS.md` §C-01 — TOCTOU race com janela 24h (Phase 4 LE `ultima_mensagem_em` que Phase 2 PER-07 ESCREVE com REQUIRES_NEW + DB NOW(); query nativa pula JPA L1 cache per OUT-06)
- `.planning/research/PITFALLS.md` §C-09 — Bearer token in logs (mascarar Authorization header em RestClient interceptor; CFG-03 ja garante toString mascarado)
- `.planning/research/PITFALLS.md` §C-12 — Shadow delivery (subscribed_apps validation) — Phase 6 territory, mencionado para nao esquecer
- `.planning/research/PITFALLS.md` §C-14 — media_id URL Bearer token leak (header per-request, nunca query param — empiricamente validado em 03-03 via getAllServeEvents.forEach gate)
- `.planning/research/PITFALLS.md` §C-15 (se existir) — Meta multipart upload field names (`messaging_product`, `type`, `file`) — STATE.md Blocker confirmar empiricamente em primeira wave Phase 4

### Arquitetura e contratos
- `.planning/research/ARCHITECTURE.md` §"Component Responsibilities" — `WhatsAppCloudClient`, `WindowEnforcementService`, `MediaCacheService`
- `.planning/research/ARCHITECTURE.md` §"Anti-Pattern 1: Enviar saida dentro da mesma transacao" — relevante so se outbound for chamado de listener (NAO e — ERP chama via REST)
- `.planning/research/ARCHITECTURE.md` §"Trava 24h enforcement" — pattern aspect AOP
- `.planning/PROJECT.md` §"Active" — WHATS-07..14, WHATS-17 mapeados a Phase 4
- `.planning/PROJECT.md` §"Key Decisions" D3, D4, D5, D9, D10 — locks de design (reativo puro, sem template, hard 24h, 4 tipos saida, media cache 30d)
- `.planning/REQUIREMENTS.md` §"Outbound" — OUT-01..11 (locked, 11 reqs)
- `.planning/ROADMAP.md` §"Phase 4" — 5 success criteria
- `.planning/phases/03-roteamento-boundary-async/03-CONTEXT.md` — Phase 3 herdada (D-03 Resilience4j pattern, D-04 Bearer header, D-08 sem retry no fallback, gotcha fallbackMethod no @Retry)
- `.planning/phases/03-roteamento-boundary-async/03-VERIFICATION.md` (quando existir) — Phase 3 sound (Risk A1 + A6 RESOLVED empiricamente)
- `.planning/phases/02-persistencia-idempotencia/02-CONTEXT.md` — Phase 2 herdada (D-02 idempotency fallback save+catch — Phase 4 reusa para MediaCache race; D-04 REQUIRES_NEW + NOW() — Phase 4 LE)
- `.planning/phases/01-fundacao-hmac-webhook/01-CONTEXT.md` — Phase 1 herdada (D-03 fail-fast properties, D-05 logging strategy)

### Padroes do codebase a espelhar
- `lib-consultas-client/src/main/java/br/com/erpkit/consultas/client/ConsultasClientImpl.java` — modelo de Resilience4j configuration (mas Phase 3+ usa annotation-driven via `resilience4j-spring-boot3`, NAO programatic — este arquivo e referencia de design, nao de implementacao literal)
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/ErpCallbackClient.java` (Phase 3) — modelo direto de `@CircuitBreaker` + `@Retry` annotation-driven com fallbackMethod no @Retry; copiar adapter para `WhatsAppCloudClient`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MetaMediaClient.java` (Phase 3) — modelo de Bearer header per-request + 2-step Graph API (download analogo a upload)
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/IdempotencyService.java` (Phase 2) — modelo de fallback save+catch DataIntegrityViolationException para race em MediaCacheService
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/ClienteZapService.java` (Phase 2) — modelo de native `@Query` + REQUIRES_NEW; WindowEnforcementService usa native `@Query` SEM REQUIRES_NEW (so leitura)
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/ErpCallbackClientTest.java` (Phase 3) — modelo WireMock 3.10.0 + `@SpringBootTest(classes=WhatsAppApplication.class)` + `@DynamicPropertySource` + `@BeforeEach cbRegistry.find().reset()` para isolar tests do circuit breaker shared singleton
- `lib-shared/src/main/java/br/com/erpkit/shared/exception/GlobalExceptionHandler.java` — verificar se `ErrorResponse` tem campo `codigo`; se nao, planner avalia adicionar (mudanca compativel com api-email/api-storage/api-consultas)
- `lib-shared/src/main/java/br/com/erpkit/shared/exception/ModuloException.java` — base para `JanelaConversaFechadaException` e `MetaApiException`
- `lib-shared/src/main/java/br/com/erpkit/shared/dto/ErrorResponse.java` — payload de erro padronizado

### Convencoes
- `.planning/codebase/CONVENTIONS.md` — PT-BR identificadores, sem Lombok, packages por camada (`controller`/`service`/`repository`/`dto`/`exception`/`aspect`/`config`)
- `.planning/codebase/STRUCTURE.md` — onde adicionar codigo novo (api-whatsapp/src/main/java/br/com/erpkit/whatsapp/aspect/ e exception/ sao novos sub-packages)

### Documento de origem
- `PLANO-WHATSAPP.md` — fonte arquitetural; D9 (4 tipos saida) e D10 (media cache TTL 30d) influenciam direto

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `WhatsAppProperties.phoneNumberId` (Phase 1) — `WhatsAppCloudClient` constroi URL `${metaApiBaseUrl}/${phoneNumberId}/messages` e `${metaApiBaseUrl}/${phoneNumberId}/media`
- `WhatsAppProperties.accessToken` (Phase 1) — Bearer header per-request em todos os 4 envios + upload media (D-04 Phase 3 reusable)
- `WhatsAppProperties.metaApiBaseUrl` (Phase 3 D-01 — campo SEM `@NotBlank`, default `https://graph.facebook.com/v22.0`, override por `@DynamicPropertySource` em WireMock tests)
- `MensagemLogRepository` (Phase 2) — reusa `repository.save(MensagemLog)` para outbound `direcao=Direcao.out`
- `MediaCacheRepository` (Phase 2 — entity + repo criados mas `MediaCacheService` ainda nao consumiu) — Phase 4 e o primeiro a popular
- `MensagemLog.Direcao.out` enum (Phase 2) — ja existente, usar
- `ModuloException` (lib-shared) — base de `JanelaConversaFechadaException` e `MetaApiException`
- `ErrorResponse` (lib-shared) — verificar shape; provavelmente precisa adicionar `codigo` String se ainda nao tem (compativel)
- `GlobalExceptionHandler` (lib-shared) — captura `ModuloException` e mapeia para HTTP code; estender se preciso para `MetaApiException` carregar `metaErrorCode` no body
- `ApiKeyFilter` (lib-shared) — endpoints `/api/whatsapp/*` exigem API key (Phase 1 SecurityConfig ja registra ApiKeyFilter pra tudo exceto `/webhook/*` e `/health`)
- Resilience4j Spring Boot 3 starter no pom (Phase 3 03-01) — Phase 4 adiciona instances `whatsapp-cloud.*`, sem nova dependencia
- `aspectjweaver:1.9.25.1` (transitive via spring-boot-starter-aop em 03-01) — habilita `@Aspect` JanelaEnforcementAspect sem dep extra
- WireMock 3.10.0 (test scope, validado em 03-03) — pattern `@SpringBootTest(classes=WhatsAppApplication.class) + @BeforeAll dynamicPort + @AfterAll stop + @DynamicPropertySource registry.add('app.modulos.whatsapp.metaApiBaseUrl', () -> "http://localhost:" + port) + @BeforeEach resetAll` reusable para `WhatsAppCloudClientTest`

### Established Patterns
- **`@CircuitBreaker(name=...) @Retry(name=...) fallbackMethod` annotation-driven** com fallback method **no @Retry**, NAO no @CircuitBreaker (gotcha 03-04 D-04 RESOLVED)
- **Bearer header per-request explicito** em cada `restClient.get/.post().header(HttpHeaders.AUTHORIZATION, "Bearer " + token)` — NAO interceptor global (auditavel visualmente, alinhado D-04 Phase 3)
- **Native `@Query` para skip JPA L1 cache** quando precisa committed read fresco (Phase 2 PER-07 escreve, Phase 4 le)
- **`save+catch DataIntegrityViolationException` + re-fetch** como gate atomico portavel H2/PostgreSQL para race conditions (Phase 2 IdempotencyService + ClienteZapService.identificar — Phase 4 MediaCacheService.registrarUpload)
- **`@Aspect @Order(HIGHEST_PRECEDENCE)`** para cross-cutting que precisa rodar fora dos retries Resilience4j
- **`@SpringBootTest(classes = WhatsAppApplication.class)`** explicito (NAO sem qualifier) — alinhamento com Phase 1 e Phase 3 padroes
- **Validation Bean Validation** nos request DTOs do controller (`@Valid` + `@NotBlank` + `@Size`); GlobalExceptionHandler mapeia para 400 automaticamente

### Integration Points
- **WhatsAppController → WhatsAppCloudClient:** controller monta DTO interno (telefone+mediaId+caption ou telefone+texto), invoca cliente. Cliente lanca `JanelaConversaFechadaException` (via aspect) ou `MetaApiException` (via fallback).
- **WhatsAppCloudClient → MediaCacheService:** apenas `enviarDocumento` consulta cache antes de upload. Texto/botoes/lista nao tem media.
- **WhatsAppCloudClient → MensagemLogRepository:** persiste `direcao=out` apos sucesso Meta (com wamid).
- **JanelaEnforcementAspect → WindowEnforcementService:** aspect le args[0], passa para service. Service faz native query.
- **WindowEnforcementService → ClienteZapRepository:** native `SELECT ultima_mensagem_em FROM whatsapp.clientes_zap WHERE telefone = ?`. Zero acoplamento com fluxo de webhook (Phase 2/3).
- **Phase 4 → Phase 5:** lib-whatsapp-client (Phase 5) chama os 5 endpoints do controller via HTTP. DTOs de request precisam ser estaveis na API (Phase 5 nao deve causar quebra retroativa).
- **`mensagens_log` shared:** Phase 2 escreve `direcao=in`; Phase 4 escreve `direcao=out`. UNIQUE wamid global previne colisao (wamid in vs out tem prefixos diferentes na semantica Meta, ambos cabem).

</code_context>

<specifics>
## Specific Ideas

- **Phase 6 backlog C-12 subscribed_apps validation:** quando endpoint `/status` for expandido em Phase 6, adicionar verificacao programatica de `GET /{WABA_ID}/subscribed_apps` para detectar shadow delivery automaticamente.
- **Aspect order HIGHEST_PRECEDENCE crucial:** test deve verificar `verifyService.verificarJanela` invocations == 1 em scenario de 3 retries (counter Mockito). Sem isso, regressao silenciosa se ordem aspect cair.
- **Gate de grep para OUT-05:** Phase 6 testes incluem `mvnw verify -pl api-whatsapp -Dexec.executable=grep` ou similar para garantir que `WhatsAppCloudClient.java` NAO contem string `enviarTemplate` ou `template` em qualquer metodo publico (defesa em profundidade contra refactor acidental).
- **`@JanelaProtegida` runtime check do tipo args[0]:** lanca `IllegalStateException` se primeiro arg nao for `String` — fail-fast em test, nao em prod. Test simples que invoca aspect com signature errada.
- **MediaCache PK e sha256 hex char(64):** alinhado com migration V3 (Phase 1 Plan 04). Java side: `HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))`.
- **Bytes para sha256 no controller, nao no service:** controller decodifica base64 → bytes uma vez, passa bytes para `WhatsAppCloudClient.enviarDocumento(telefone, bytes, mimeType, filename, caption)`. Cliente delega para `mediaCacheService.buscarMediaId(bytes)` que recalcula sha256. Alternativa: passar `String sha256` ja calculado do controller para evitar recalculo no service. Decidir no Plan: se bytes nao sao usados em outro lugar do cliente, recalcular no service e ok; se sim, passar sha256 + bytes para evitar duplicar trabalho.
- **`ErrorResponse.codigo` field:** verificar antes de planejar — se `lib-shared/ErrorResponse` ja tem, perfeito; se nao, adicionar como campo opcional (nullable). Mudanca compativel com api-email/api-storage/api-consultas (campo extra ignorado no JSON).
- **`WindowEnforcementService` query retorna `Optional<Instant>` ou `null`:** decisao de Plan. Convencao de monorepo prefere `Optional`. Native `@Query` com retorno `Optional<Instant>` em Spring Data JPA funciona em Spring Boot 3.x.
- **Tests ja-validados pattern reusable:** `WhatsAppCloudClientTest` deve seguir EXATAMENTE o pattern do `ErpCallbackClientTest` (Phase 3 Plan 04) — mesma config WireMock, mesma `@BeforeEach cbRegistry.find('whatsapp-cloud').ifPresent(CircuitBreaker::reset)`, mesmo verify(N, postRequestedFor) para counter assertions.

</specifics>

<deferred>
## Deferred Ideas

- **`lib-whatsapp-client` (LIB-01..08)** — **Phase 5**. Auto-config condicional, SPI WhatsAppCommandHandler, registry, ObjectProvider fallback. Todos os DTOs request/response do controller (Phase 4) precisam estar estaveis antes — Phase 5 espelha contratos.
- **README.md por modulo + RUNBOOK.md operacional + SpringDoc OpenAPI exhaustivo (QA-04..06)** — **Phase 6**. Phase 4 garante so build verde + endpoints documentados via convencoes Spring (sem README explicito).
- **Subscribed_apps validation (PITFALLS C-12) no `/status`** — **Phase 6**. Detectar shadow delivery automaticamente (Meta UI 2025 quebrou subscribe automatico).
- **Categorizacao estruturada de erros Meta por `error.code` em metricas Micrometer (DIFF-03)** — **v2 milestone**.
- **Volume metrics (mensagens in/out por dia, por tipo, por erro) Micrometer (DIFF-04)** — **v2 milestone**.
- **Mark-as-read (DIFF-01) + typing indicator (DIFF-02)** — **v2 milestone**, gratuitos no Cloud API.
- **Retencao automatica de mensagens_log >90 dias (DIFF-05)** — **v2 milestone** (LGPD compliance).
- **E2E real com WABA do Meta** — **milestone seguinte** (D7 — verificacao Meta Business pode levar dias/semanas).
- **Engate ERP-MUDAS (`ModulosController` proxy + handlers exemplares)** — **outro repo, outro GSD project**.
- **Auto-update do api-whatsapp.jar via release.sh (OPS-V2-02)** — **v2**.
- **Onboarding multi-cliente automatizado via Graph API (OPS-V2-01)** — **v2**.
- **Engate ERP-CALHAS (OPS-V2-03)** — **v2** (D2 piloto MUDAS apenas).
- **Reconciliacao de `id_cliente_erp = null` em clientes_zap** — fora desta milestone (ERP-MUDAS territory).
- **Aspect com atributo `@JanelaProtegida(telefoneArgIndex = N)`** — fallback se algum metodo `enviar*` futuro tiver telefone em posicao diferente. Por enquanto convencao posicional `args[0]` e suficiente.
- **Multipart no controller para `enviar-documento`** — fallback se PDFs piloto MUDAS forem >15MB. Por enquanto JSON+base64 (D-01).
- **Meta error codes pass-through (sem traducao)** — fallback se ERP tiver dificuldade com nossos codigos. Por enquanto translated (D-02).
- **Pointcut por nome (`execution(* enviar*(..))`) em vez de annotation** — fallback se aspect annotation tiver bug Spring AOP self-call. Por enquanto annotation marker (D-03).
- **Status endpoint richer** (`+lastMessageMeta`, `+totalMidiaCacheada`, `+recentErrorCount`) — Phase 6 expansao se feedback de operador piloto pedir.
- **Sliding TTL no MediaCache** — fallback se reupload mensal estiver causando custo Meta inesperado. Por enquanto TTL estrito (D-04).
- **Persistencia de bytes da media outbound** — fora do escopo (mediaBase64 chega do controller, e enviado, e descartado). MediaCache armazena so o `media_id` retornado pelo Meta + sha256 do conteudo, NAO os bytes.
- **Persistencia de statuses Meta (sent/delivered/read/failed) sobre mensagens outbound** — backlog opcional v2.
- **Maquina de estado complexa de conversa** (em_aprovacao, aguardando_doc) — D6 PROJECT.md proibe (estado fica nos handlers SPI Phase 5).

</deferred>

---

*Phase: 4-Outbound + Trava 24h + WhatsAppController*
*Context gathered: 2026-05-05*
