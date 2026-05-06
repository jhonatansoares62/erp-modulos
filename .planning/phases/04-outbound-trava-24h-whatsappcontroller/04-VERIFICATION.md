---
phase: 04-outbound-trava-24h-whatsappcontroller
verified: 2026-05-06T13:30:00Z
status: passed
score: 11/11 must-haves verified
overrides_applied: 0
---

# Phase 4: Outbound + Trava 24h + WhatsAppController Verification Report

**Phase Goal:** O ERP consegue enviar os 4 tipos de mensagem de saida (texto, documento, botoes, lista) com custo zero garantido por arquitetura — `enviarTemplate()` nao existe no codigo, e a trava hard de janela 24h rejeita qualquer envio fora da janela antes de chamar a Cloud API.

**Verified:** 2026-05-06T13:30:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| #   | Truth | Status | Evidence |
| --- | ----- | ------ | -------- |
| SC-1 | `WhatsAppCloudClient` expoe `enviarTexto/enviarDocumento/enviarBotoes/enviarLista` — nenhum `enviarTemplate` no codigo | VERIFIED | `WhatsAppCloudClient.java:116/134/169/196` 4 metodos publicos; grep `enviarTemplate` em `src/main/java` retorna apenas 2 hits — ambos em Javadoc/comentarios negando existencia (linhas 44, 110). Reflection test `metodos_publicos_nao_inclui_template` em `WhatsAppCloudClientTest.java` falha se metodo template for adicionado |
| SC-2 | `POST /api/whatsapp/enviar-*` com `ultima_mensagem_em` > 24h retorna 409 com codigo `JANELA_24H_FECHADA` antes de qualquer chamada a Cloud API | VERIFIED | `JanelaEnforcementAspect.java:44` `@Order(HIGHEST_PRECEDENCE)` aspect intercepta `@JanelaProtegida`. `WindowEnforcementService.java:71` lanca `JanelaConversaFechadaException` (HTTP 409, codigo `JANELA_24H_FECHADA`). 4 metodos publicos do `WhatsAppCloudClient` carregam `@JanelaProtegida` (linhas 113/131/166/193). Test `aspect_invoca_apenas_uma_vez_em_3_retries` empiricamente prova counter==1 em 3 retries — aspect roda OUTERMOST do retry loop. Controller test `janela_fechada_retorna_409_codigo_janela` confirma fluxo end-to-end |
| SC-3 | `enviarDocumento` com mesmo PDF 2x faz upload apenas na 1a vez — `MediaCacheService` retorna `media_id` cacheado por sha256; entrada expirada dispara reupload | VERIFIED | `MediaCacheService.java:57-61` `buscarMediaId(byte[])` calcula sha256 hex via `HexFormat.of().formatHex` + `findByArquivoHashAndExpiraEmAfter(hash, Instant.now())` (TTL estrito sem sliding). `WhatsAppCloudClient.java:137-143` cache-check → upload-if-miss → register pipeline. Test `enviarDocumento_cache_miss_faz_upload_e_envia` + `enviarDocumento_cache_hit_pula_upload` em `WhatsAppCloudClientTest`. Race protection via `try/catch DataIntegrityViolationException` (Phase 2 pattern reusado) |
| SC-4 | Erro Meta 4xx (400/401/403) NAO retentado, logado com `meta_error_code`; 5xx + timeout retentam exponencial 3x; `Authorization: Bearer` nunca em logs ou query | VERIFIED | `application.yml:160-194` resilience4j `whatsapp-cloud` — `retry-exceptions` whitelist explicita: HttpServerErrorException + ResourceAccessException + SocketTimeoutException + IOException (HttpClientErrorException AUSENTE). Tests provam: `quatrocentos_no_retry_lanca_meta_api_exception_4xx` (counter==1), `cinquecentos_recupera_apos_retries_counter_3` (counter==3), `timeout_retentou_e_lancou_timeout`. Bearer per-request: `WhatsAppCloudClient.java:249/271` `.header(AUTHORIZATION, "Bearer " + token)` em CADA chamada — grep `access_token` em `src/main/java` = 0 hits. Test `bearer_nunca_em_query_param` empirico via WireMock |
| SC-5 | Mensagem outbound persistida em `mensagens_log` com `direcao=out` + wamid; envio OK retorna 200 com wamid | VERIFIED | `WhatsAppCloudClient.java:126/161/188/225` — 4 metodos persistem `new MensagemLog(wamid, telefone, Direcao.out, ...)` apos sucesso da chamada Meta. `MetaApiException.java` + `JanelaConversaFechadaException.java` extends ModuloException implements `CodigoCarrier`. `EnvioResponse.java` record retorna wamid. Controller `WhatsAppController.java:64-89` retorna `ResponseEntity.ok(...)` |

### Observable Truths (PLAN must_haves — Plans 04-01..04-06)

| #   | Truth (PLAN) | Status | Evidence |
| --- | ------------ | ------ | -------- |
| 04-01-T1 | `ErrorResponse` em lib-shared expoe campos opcionais `codigo` (String) + `metaErrorCode` (Integer) compativeis com api-email/api-storage/api-consultas | VERIFIED | `ErrorResponse.java:32/39` campos `private String codigo` + `private Integer metaErrorCode` com getters/setters. `@JsonInclude(NON_NULL)` na linha 17 garante backward-compat (campos nullable omitidos). 23/23 lib-shared tests passing |
| 04-01-T2 | `GlobalExceptionHandler` propaga codigo+metaErrorCode via `instanceof CodigoCarrier` SEM importar exceptions de api-whatsapp | VERIFIED | `GlobalExceptionHandler.java:27-30` `if (ex instanceof CodigoCarrier carrier) { error.setCodigo(carrier.getCodigo()); error.setMetaErrorCode(carrier.getMetaErrorCode()); }`. Imports verificados — apenas `lib-shared/dto/ErrorResponse` + `lib-shared/exception/{ModuloException,CodigoCarrier}`, zero imports de api-whatsapp |
| 04-01-T3 | `application.yml` + `application-test.yml` de api-whatsapp expoem instance Resilience4j `whatsapp-cloud` (CB sliding-window=10/threshold=50/wait-open=60s + Retry max=3/wait=1s/multiplier=2.0/retry-exceptions completo) | VERIFIED | `application.yml:152-157` + `application.yml:184-194` exatamente como especificado; `retry-exceptions` whitelist inclui `ResourceAccessException` (PITFALLS C-15 preventiva). Test profile espelhado em `application-test.yml` com wait-duration 50ms |
| 04-01-T4 | `MultipartUploadSpikeTest` valida empiricamente em WireMock que upload Spring RestClient envia 3 fields (`messaging_product`, `type`, `file`) com boundary auto e Bearer NUNCA em query param | VERIFIED | `MultipartUploadSpikeTest.java` 1 test passing (surefire 0.027s); WireMock stub validates `aMultipart()` matchers para os 3 fields obrigatorios (PITFALLS C-15) |
| 04-02-T1 | `WindowEnforcementService.verificarJanela(telefone)` consulta via native @Query (skip JPA L1 cache, committed read fresco) — diff > 24h ou cliente sem registro lanca `JanelaConversaFechadaException` | VERIFIED | `WindowEnforcementService.java:71-89`; `ClienteZapRepository.java:82-83` `nativeQuery = true` + `Optional<Instant> buscarUltimaMensagemEm(...)`. Tests `WindowEnforcementServiceTest`: 3 tests cobrindo 23h passa, 25h lanca, cliente inexistente lanca |
| 04-02-T2 | `JanelaProtegida` annotation + `JanelaEnforcementAspect` com `@Order(HIGHEST_PRECEDENCE)` interceptam `@annotation`, leem args[0] como String, fail-fast se nao for String | VERIFIED | `JanelaProtegida.java` `@Target(METHOD) @Retention(RUNTIME)`. `JanelaEnforcementAspect.java:44` `@Order(Ordered.HIGHEST_PRECEDENCE)` + linhas 56-59 fail-fast IllegalStateException se args[0] nao for String. Test `aspect_lanca_se_args_nao_string` valida fail-fast |
| 04-02-T3 | `JanelaConversaFechadaException` extends ModuloException(409) implements CodigoCarrier com codigo=`JANELA_24H_FECHADA` | VERIFIED | `JanelaConversaFechadaException.java:30-33` extends ModuloException implements CodigoCarrier; constante `CODIGO = "JANELA_24H_FECHADA"`; constructor passa `HttpStatus.CONFLICT` |
| 04-02-T4 | Aspect roda EXATAMENTE 1x em scenario 3 retries (counter==1 prova HIGHEST_PRECEDENCE) | VERIFIED | `JanelaEnforcementAspectTest.aspect_invoca_apenas_uma_vez_em_3_retries` linhas 113-115: WireMock 500/500/200 + Mockito spy `verify(windowSpy, times(1)).verificarJanela(...)`. 3 chamadas HTTP confirmam retry, 1 verificacao confirma OUTERMOST |
| 04-03-T1 | `MediaCacheService.buscarMediaId(byte[])` calcula sha256 + filtra por `expiraEmAfter(now)` — TTL estrito 30d sem sliding | VERIFIED | `MediaCacheService.java:42` `Duration.ofDays(30)`; linhas 57-61 `findByArquivoHashAndExpiraEmAfter(hash, Instant.now())` (filtra expirados no banco). Hit NAO estende `expira_em` (D-04). Test `MediaCacheServiceTest` 4 tests passing |
| 04-03-T2 | `registrarUpload` faz delete-on-conflict + save com TTL 30d; race silenciado via try/catch DataIntegrityViolationException | VERIFIED | `MediaCacheService.java:75-90` upsert pattern com try/catch DataIntegrityViolationException + log.debug (Phase 2 IdempotencyService pattern reusado) |
| 04-04-T1 | `WhatsAppCloudClient` expoe EXATAMENTE 4 metodos publicos: enviarTexto/Documento/Botoes/Lista — NAO existe enviarTemplate | VERIFIED | `WhatsAppCloudClient.java:116/134/169/196` 4 metodos publicos. Grep `enviarTemplate` em src/main/java: apenas 2 hits em Javadoc/comentarios negando. Reflection test em `WhatsAppCloudClientTest.metodos_publicos_nao_inclui_template` blinda regressao |
| 04-04-T2 | Cada metodo publico anotado com `@JanelaProtegida + @CircuitBreaker + @Retry(fallbackMethod=...)` — fallback no @Retry | VERIFIED | `WhatsAppCloudClient.java:113-115/131-133/166-168/193-195` triple annotation em cada metodo. fallbackMethod NO @Retry (gotcha 03-04 RESOLVED) |
| 04-04-T3 | Bearer per-request explicito em CADA chamada (4 envios + uploadMedia) | VERIFIED | `WhatsAppCloudClient.java:249` (uploadMedia) + linha 271 (postMessages) `.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())`. Grep `access_token` em src/main/java = 0 hits |
| 04-04-T4 | `enviarDocumento` orquestra cache-check → upload-if-miss → register → send → persist | VERIFIED | `WhatsAppCloudClient.java:134-164` exatamente esta sequencia: linhas 137-143 cache check + upload-if-miss; 152-159 send; 161 mensagemLogRepository.save(...Direcao.out...) |
| 04-04-T5 | `fallbackMethod` converte Throwable em MetaApiException via classificar — 4xx/5xx/timeout/CB | VERIFIED | `WhatsAppCloudClient.java:332-352` classificar(): HttpClientErrorException → CATEGORIA_4XX (422), HttpServerErrorException → INDISPONIVEL_5XX (502), ResourceAccessException → TIMEOUT (504), CallNotPermittedException → CIRCUIT_OPEN (503) |
| 04-04-T6 | `MetaApiException` extends ModuloException implements CodigoCarrier — getCodigo()/getMetaErrorCode() | VERIFIED | `MetaApiException.java:25-72` enum Tipo {CATEGORIA_4XX/INDISPONIVEL_5XX/TIMEOUT/CIRCUIT_OPEN} mapeia HTTP+codigo. Override `getMetaErrorCode()` retorna campo extraido do response Meta |
| 04-04-T7 | WhatsAppCloudClientTest com WireMock cobre 4 envios + 4xx/5xx/timeout/circuit + bearer leak gate + reflection no template + multipart | VERIFIED | `WhatsAppCloudClientTest.java` 13 tests (PLAN min=11) — todos os cenarios cobertos. Surefire: `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.133 s` |
| 04-05-T1 | `WhatsAppController` expoe 5 endpoints sob `/api/whatsapp` protegidos por ApiKeyFilter | VERIFIED | `WhatsAppController.java:48` `@RequestMapping("/api/whatsapp")` + 4 POST + 1 GET (linhas 63/68/81/86/91). ApiKeyFilter aplicado via Phase 1 SecurityConfig herdado |
| 04-05-T2 | `EnviarBotoesRequest` com `@Size(max=3)` em `List<BotaoDto>` botoes — early 400 antes de Cloud Client | VERIFIED | `EnviarBotoesRequest.java:29` `@Size(max = 3, message = "Maximo 3 botoes (Cloud API limit)")`. Test `enviar_botoes_validation_400_4_botoes` em WhatsAppControllerTest |
| 04-05-T3 | `EnviarListaRequest` com `@AssertTrue isTotalItensValido()` cross-secao — 400 se total > 10 | VERIFIED | `EnviarListaRequest.java:41-50` `@AssertTrue` + `@JsonIgnore` evita inclusao no response. Test `enviar_lista_validation_400_total_11_itens` |
| 04-05-T4 | `EnviarDocumentoRequest` com mediaBase64 max 18MB + Pattern em mimeType + decode try/catch → 400 | VERIFIED | `EnviarDocumentoRequest.java:27` `@Size(max = 18_000_000)`; linha 31 mimeType `@Pattern(regexp = "^[a-zA-Z]+/[a-zA-Z0-9.+\\-]+$")`. `WhatsAppController.java:71-76` try/catch IllegalArgumentException → ModuloException(400). Test `enviar_documento_base64_invalido_400` |
| 04-05-T5 | GET /status retorna StatusResponse{status, circuitBreakerState, phoneNumberId} via cbRegistry.find("whatsapp-cloud") | VERIFIED | `WhatsAppController.java:91-97` `cbRegistry.find("whatsapp-cloud").map(cb -> cb.getState().name()).orElse("UNKNOWN")` + `new StatusResponse("UP", state, properties.getPhoneNumberId())`. Test `status_endpoint_retorna_state_cb` |
| 04-05-T6 | `WhatsAppControllerTest` @WebMvcTest cobre 11+ scenarios: 5 happy + 4 validation + 409 + 422/502/504/503 + status | VERIFIED | `WhatsAppControllerTest.java` 13 tests (PLAN min=11). Surefire: `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.480 s` |
| 04-06-T1 | Reator inteiro `./mvnw verify` BUILD SUCCESS — 7 modulos verdes, zero regressao | VERIFIED | Surefire-reports timestamps confirmam ultima execucao 2026-05-06T02:40:12 (apos source 02:29). Modulos: api-email 34 + api-storage 13 + api-consultas 4 + lib-shared 23 + lib-consultas-client 3 + api-whatsapp 189 = **266 tests**, 0 failures. Compile pos-verification BUILD SUCCESS |
| 04-06-T2 | Grep gates SC-1 + SC-4c executados: 0 hits para `enviarTemplate`/`"template"` em codigo executavel; 0 hits para `access_token=` em src/main | VERIFIED | `enviarTemplate`: 2 hits — ambos em Javadoc/comentarios negativos. `access_token`: 0 hits em src/main/java. `@JanelaProtegida`: 4 substantive uses (4 envios). `fallbackMethod`: presente apenas em `@Retry` (nao em `@CircuitBreaker`) |
| 04-06-T3 | ROADMAP/REQUIREMENTS/STATE.md atualizados com Phase 4 [x] complete + 11 OUT reqs Complete | VERIFIED | ROADMAP.md `[x] Phase 4: Outbound + Trava 24h + WhatsAppController`; REQUIREMENTS.md tabela traceability OUT-01..11 todos `Phase 4 | Complete`; STATE.md frontmatter `completed_phases: 4`, `total_plans: 26, completed_plans: 26` |

**Score:** 11/11 OUT requirements verified + 5/5 ROADMAP Success Criteria verified + 26/26 PLAN must_have truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `lib-shared/.../dto/ErrorResponse.java` | Campos opcionais codigo+metaErrorCode + @JsonInclude(NON_NULL) | VERIFIED | 108 lines, `@JsonInclude(NON_NULL)` linha 17, `private String codigo` linha 32, `private Integer metaErrorCode` linha 39 + getters/setters |
| `lib-shared/.../exception/CodigoCarrier.java` | Interface marker em lib-shared (sem dependencia api-*) | VERIFIED | 36 lines, `interface CodigoCarrier` com `String getCodigo()` + `default Integer getMetaErrorCode() { return null }` |
| `lib-shared/.../exception/GlobalExceptionHandler.java` | `instanceof CodigoCarrier` propagation sem importar api-whatsapp | VERIFIED | 50 lines, linha 27 `if (ex instanceof CodigoCarrier carrier)`, imports apenas lib-shared |
| `api-whatsapp/.../config` Resilience4j whatsapp-cloud | CB + Retry com retry-exceptions whitelist | VERIFIED | application.yml linhas 152-194 — instance dedicada espelhando erp-callback. Test profile espelhado |
| `api-whatsapp/.../aspect/JanelaProtegida.java` | @interface @Target(METHOD) @Retention(RUNTIME) | VERIFIED | 30 lines, marker correto |
| `api-whatsapp/.../aspect/JanelaEnforcementAspect.java` | @Aspect @Component @Order(HIGHEST_PRECEDENCE) | VERIFIED | 67 lines, `@Order(Ordered.HIGHEST_PRECEDENCE)` linha 44, fail-fast args[0]:String |
| `api-whatsapp/.../service/WindowEnforcementService.java` | Native @Query + TelefoneBR.normalizar + lanca JanelaConversaFechadaException | VERIFIED | 90 lines, normalizacao + native query + 24h check |
| `api-whatsapp/.../exception/JanelaConversaFechadaException.java` | extends ModuloException(409) implements CodigoCarrier | VERIFIED | 72 lines, extends + implements + CODIGO="JANELA_24H_FECHADA" |
| `api-whatsapp/.../service/MediaCacheService.java` | TTL 30d + race save+catch + sha256 hex | VERIFIED | 101 lines, `Duration.ofDays(30)` + HexFormat.of() + try/catch DataIntegrityViolationException |
| `api-whatsapp/.../exception/MetaApiException.java` | extends ModuloException + implements CodigoCarrier + enum Tipo (4 valores) | VERIFIED | 72 lines, enum Tipo {CATEGORIA_4XX/INDISPONIVEL_5XX/TIMEOUT/CIRCUIT_OPEN} mapeia status+codigo |
| `api-whatsapp/.../service/WhatsAppCloudClient.java` | 4 metodos publicos + uploadMedia privado + classificar Throwable | VERIFIED | 372 lines, 4 metodos com triple annotation, Bearer per-request, persistencia outbound, fallbacks classificam Throwable. ZERO ocorrencias de `enviarTemplate` como metodo |
| `api-whatsapp/.../dto/{Botao,Item,Secao}Dto.java` + `EnvioResponse.java` | Records DTO com Jakarta Bean Validation | VERIFIED | Todos records com @NotBlank + @Size apropriados + Cloud API limits documentados |
| `api-whatsapp/.../dto/Enviar{Texto,Documento,Botoes,Lista}Request.java` + `StatusResponse.java` | 5 DTOs request com validation forcando Cloud API limits | VERIFIED | EnviarBotoes @Size(max=3); EnviarLista @AssertTrue cross-secao <=10; EnviarDocumento @Size(max=18M) + Pattern mimeType |
| `api-whatsapp/.../controller/WhatsAppController.java` | 5 endpoints @RestController @RequestMapping("/api/whatsapp") | VERIFIED | 99 lines, 4 POST + 1 GET, base64 decode no enviar-documento, cbRegistry.find no status |
| Test files (5 novos + 1 spike) | min_tests respeitados | VERIFIED | WhatsAppCloudClientTest 13 (>=11), WhatsAppControllerTest 13 (>=11), JanelaEnforcementAspectTest 3 (>=3), WindowEnforcementServiceTest 3 (>=3), MediaCacheServiceTest 4 (>=4), MultipartUploadSpikeTest 1 |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | -- | --- | ------ | ------- |
| `GlobalExceptionHandler` | `CodigoCarrier` | `instanceof CodigoCarrier` (sem import api-whatsapp) | WIRED | linha 27, imports apenas lib-shared |
| `JanelaConversaFechadaException` + `MetaApiException` | `CodigoCarrier` (lib-shared) | `implements CodigoCarrier` | WIRED | Ambos importam `br.com.erpkit.shared.exception.CodigoCarrier` + implements + override getCodigo() |
| `JanelaEnforcementAspect` | `WindowEnforcementService` | constructor DI + @Around invoke | WIRED | constructor linhas 47-51, invocacao linha 64 `windowService.verificarJanela(telefone)` |
| `WindowEnforcementService` | `ClienteZapRepository.buscarUltimaMensagemEm` | native @Query (skip JPA L1) | WIRED | linha 73 chamada; repository linha 82 `nativeQuery = true` |
| `WhatsAppCloudClient.enviar*` | `@JanelaProtegida + @CircuitBreaker + @Retry` triple annotation | Resilience4j Spring AOP + JanelaEnforcementAspect | WIRED | 4 metodos publicos com 3 annotations cada, fallbackMethod NO @Retry |
| `enviarDocumento` | `mediaCacheService + uploadMedia + mensagemLogRepository.save` | Pipeline cache → upload → register → send → persist | WIRED | Linhas 137-164 sequencia completa |
| `WhatsAppController.enviar*` | `WhatsAppCloudClient.enviar*` | thin wrapper Spring | WIRED | Controller linhas 64-89 delegate to cloudClient.enviar* |
| `WhatsAppController.enviarDocumento` | `Base64.getDecoder().decode + cloudClient.enviarDocumento(byte[])` | controller decodifica antes de delegar (D-01) | WIRED | linhas 71-76 try/catch IllegalArgumentException → 400 |
| `GET /status` | `CircuitBreakerRegistry.find("whatsapp-cloud")` | Resilience4j CB state introspection | WIRED | linhas 92-95 `cbRegistry.find("whatsapp-cloud").map(cb -> cb.getState().name()).orElse("UNKNOWN")` |
| `application.yml` resilience4j whatsapp-cloud | api-whatsapp `@CircuitBreaker(name="whatsapp-cloud")` + `@Retry(name="whatsapp-cloud")` | nome instancia | WIRED | yml linhas 152-194 + WhatsAppCloudClient linhas 114/115/132/133 etc. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| WhatsAppCloudClient.enviarTexto wamid | `String wamid` | Meta API response.messages[0].id (extrairWamid) | Yes — WireMock test stubs real responses; production = real Meta | FLOWING |
| WhatsAppCloudClient.enviarDocumento mediaId | `String mediaId` | mediaCacheService.buscarMediaId OR uploadMedia → Meta `/media` | Yes — sha256 cache hit/miss + real upload | FLOWING |
| WindowEnforcementService.verificarJanela ultima | `Optional<Instant> ultima` | ClienteZapRepository.buscarUltimaMensagemEm (native query DB) | Yes — committed read from DB (skip JPA L1), Phase 2 PER-07 escreve via NOW() | FLOWING |
| WhatsAppController.status circuitBreakerState | `String state` | CircuitBreakerRegistry.find("whatsapp-cloud").getState() | Yes — Resilience4j runtime state | FLOWING |
| MediaCacheService.buscarMediaId mediaId | `Optional<String> mediaId` | MediaCacheRepository.findByArquivoHashAndExpiraEmAfter (DB query) | Yes — TTL filtered DB query | FLOWING |
| GlobalExceptionHandler ErrorResponse.codigo | `String codigo` | CodigoCarrier.getCodigo() em ex que implementa interface | Yes — JanelaConversaFechadaException retorna "JANELA_24H_FECHADA"; MetaApiException retorna conforme Tipo enum | FLOWING |
| MensagemLog persistence (4 envios outbound) | `MensagemLog(wamid, telefone, Direcao.out, ...)` | mensagemLogRepository.save(...) apos sucesso Cloud API | Yes — wamid real do Meta + Direcao.out hardcoded enum | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| Code compiles | `./mvnw compile -pl api-whatsapp -am` | BUILD SUCCESS in 1.117s (lib-shared + api-whatsapp) | PASS |
| Test results current (post-source-edit) | Compare timestamps surefire-reports vs source files | 02:40:12 (test) > 02:29:46 (source) — tests ran AFTER latest source | PASS |
| Total tests passing | sum surefire txt files Tests run | 266 (api-email 34 + api-storage 13 + api-consultas 4 + lib-shared 23 + lib-consultas-client 3 + api-whatsapp 189) | PASS |
| All test files 0 failures/0 errors | grep "Failures: 0, Errors: 0" todos os 29 api-whatsapp txt | All 29 reports show 0 failures, 0 errors | PASS |
| Grep gate: enviarTemplate em codigo executavel | grep `enviarTemplate` em src/main/java | 2 hits, ambos em Javadoc/comentario (negacao) — ZERO em codigo | PASS |
| Grep gate: access_token query | grep `access_token` em src/main/java | 0 hits | PASS |
| Grep gate: @JanelaProtegida substantive | grep `@JanelaProtegida` em src/main/java | 4 substantive uses (4 envios) | PASS |
| Grep gate: fallbackMethod localizado em @Retry | grep `fallbackMethod` em WhatsAppCloudClient.java | 4 ocorrencias em @Retry, 0 em @CircuitBreaker | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ----------- | ----------- | ------ | -------- |
| OUT-01 | 04-04 | enviarTexto via RestClient | SATISFIED | WhatsAppCloudClient.java:116 + test enviarTexto_happy_path |
| OUT-02 | 04-04 | enviarDocumento upload + send via media_id | SATISFIED | WhatsAppCloudClient.java:134 + cache pipeline + tests cache_miss/cache_hit |
| OUT-03 | 04-04 + 04-05 | enviarBotoes ate 3 botoes (early fail) | SATISFIED | WhatsAppCloudClient.java:169 + EnviarBotoesRequest @Size(max=3) + test 4_botoes_400 |
| OUT-04 | 04-04 + 04-05 | enviarLista ate 10 itens cross-secao | SATISFIED | WhatsAppCloudClient.java:196 + EnviarListaRequest @AssertTrue isTotalItensValido + test total_11_itens_400 |
| OUT-05 | 04-04 | NAO existe enviarTemplate (trava custo zero #1) | SATISFIED | Reflection test metodos_publicos_nao_inclui_template + grep gate em src/main/java (apenas Javadoc negando) |
| OUT-06 | 04-02 | WindowEnforcementService verificarJanela via committed read | SATISFIED | Native @Query buscarUltimaMensagemEm + lanca JanelaConversaFechadaException(409) |
| OUT-07 | 04-02 + 04-04 | Aspect AOP @JanelaProtegida garante check antes de Cloud API | SATISFIED | JanelaEnforcementAspect @Order(HIGHEST_PRECEDENCE) + 4 envios anotados + test counter==1 em 3 retries |
| OUT-08 | 04-03 + 04-04 | MediaCacheService sha256 hit/miss + TTL 30d | SATISFIED | MediaCacheService.java + 4 tests (hit/miss/expirado/race) |
| OUT-09 | 04-04 | Persiste outbound em mensagens_log direcao=out + wamid | SATISFIED | WhatsAppCloudClient linhas 126/161/188/225 mensagemLogRepository.save(...Direcao.out...) |
| OUT-10 | 04-01 + 04-04 | 4xx no retry, 5xx+timeout retry exponencial 3x, sem Bearer em logs | SATISFIED | retry-exceptions whitelist exclui HttpClientErrorException + tests 4xx counter==1 / 5xx counter==3 / timeout / Bearer leak gate |
| OUT-11 | 04-05 | 5 endpoints /api/whatsapp/* protegidos por ApiKeyFilter | SATISFIED | WhatsAppController.java 5 endpoints + ApiKeyFilter via SecurityConfig |

**Coverage:** 11/11 OUT requirements declared in PLAN frontmatters → all SATISFIED. Zero orphans (REQUIREMENTS.md Phase 4 mapping = OUT-01..11 = exactly the IDs in plans).

### Anti-Patterns Found

None. All matches for `TODO|FIXME|HACK|placeholder` in api-whatsapp/src/main/java are false positives — Portuguese words "metodo", "todos os", and tarefa-style mentions in Javadoc.

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| (none) | — | — | — | No real anti-patterns |

### Human Verification Required

None. All deliverables empirically validated:
- Code compiles
- All 266 tests pass (verified via surefire-reports)
- 4 grep gates passing
- Reflection test blinda OUT-05 contra regressao
- Aspect counter==1 in 3 retries empirically proven
- Bearer leak gate empirically validated via WireMock
- Multipart 3 fields empirically validated via WireMock spike

UI/visual concerns out of scope (modulo backend, sem UI). External Meta integration explicitly out of scope per Phase 4 boundary (RUNBOOK + cliente piloto = Phase 6).

### Gaps Summary

No gaps found. Phase 4 delivers a complete, working outbound subsystem:

1. **Trava custo zero #1 (OUT-05):** `enviarTemplate` ausente do codigo + reflection test + grep gate. Regressao impossivel sem refactor consciente do test.
2. **Trava custo zero #2 (OUT-06+OUT-07):** Aspect AOP `@JanelaProtegida + @Order(HIGHEST_PRECEDENCE)` garante check antes de qualquer chamada Cloud API, com counter==1 empiricamente validado em 3 retries.
3. **Resilience4j (OUT-10):** Instance `whatsapp-cloud` configurada (CB + Retry com retry-exceptions whitelist excluindo HttpClientErrorException). Bearer per-request elimina vazamento em query.
4. **Cache de midia (OUT-08):** sha256 + TTL estrito 30d sem sliding + race protection via try/catch DataIntegrityViolationException.
5. **REST API (OUT-11):** 5 endpoints com Jakarta Bean Validation forcando Cloud API limits (3 botoes, 10 itens, 18MB base64) ANTES de chamada externa.
6. **Propagacao de erro (D-02):** ErrorResponse + CodigoCarrier interface em lib-shared + GlobalExceptionHandler instanceof check — codigo+metaErrorCode propagados sem acoplamento ascendente.
7. **Reator BUILD SUCCESS:** 266 tests, 0 failures, 0 errors, 0 skipped — zero regressao em api-email/api-storage/api-consultas apos modificacao do ErrorResponse (mudanca compativel via @JsonInclude(NON_NULL)).

Phase 4 esta pronta para Phase 5 (lib-whatsapp-client) consumir os contratos estaveis (DTOs request/response + endpoints REST + Resilience4j patterns) sem retrabalho.

---

_Verified: 2026-05-06T13:30:00Z_
_Verifier: Claude (gsd-verifier)_
