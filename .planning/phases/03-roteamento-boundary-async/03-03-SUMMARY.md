---
phase: 03-roteamento-boundary-async
plan: 03
subsystem: api
tags: [api-whatsapp, meta-graph-api, rest-client, 2-step-download, bearer-header, wiremock, http-client]

requires:
  - phase: 03-roteamento-boundary-async/01
    provides: WhatsAppProperties.metaApiBaseUrl + WireMock 3.10.0 standalone classpath + spring.http.client connect-timeout/read-timeout
  - phase: 03-roteamento-boundary-async/02
    provides: MediaMetadataDTO Jackson POJO (getMimeType camelCase) + MetaMediaResultado record (bytes/mimeType/filename)
provides:
  - "MetaMediaClient @Service: 2-step Graph API media download. baixar(String mediaId) -> Optional<MetaMediaResultado>. RestClient construido em constructor via WhatsAppProperties.getMetaApiBaseUrl. Bearer SOMENTE em header Authorization (PITFALLS C-14). 404 graceful em qualquer step retorna Optional.empty + log.warn (PITFALLS C-08). Sem Resilience4j (5xx escapam para o listener Wave 5 tratar)."
  - "MetaMediaClientTest @SpringBootTest: 6 tests integracao com WireMock 3.10.0 standalone + WireMockServer.dynamicPort + @DynamicPropertySource sobrescrevendo metaApiBaseUrl. Cobertura: happy path 2-step / 404 step 1 / 404 step 2 / 5xx sem retry / bearer header em ambas requests + nunca em query (C-14 regression test) / metadata sem URL (Rule 2 defesa em profundidade)."
  - "Pattern WireMock setup com @DynamicPropertySource estabelecido para reuso em Wave 4 (ErpCallbackClient) e Wave 6 (E2E)."
  - "Validacao empirica formal de WireMock 3.10.0 standalone + Boot 3.5.9 — bloqueador documentado em 03-01-SUMMARY resolvido."
affects:
  - phase 03-roteamento-boundary-async/05 (MensagemAsyncListener consumira metaMediaClient.baixar(mediaId) como PRIMEIRA acao apos ack — D-04 + ROU-05)
  - phase 03-roteamento-boundary-async/06 (E2E tests reutilizarao WireMock + @DynamicPropertySource pattern para stubar Meta + ERP juntos)

tech-stack:
  added: []  # zero novos deps Maven — RestClient e Spring Web ja, WireMock 3.10.0 ja em Wave 1
  patterns:
    - "Pattern '2-step download Graph API': step 1 GET /{id} retorna metadata com URL absoluta temporaria (lookaside.fbsbx.com), step 2 GET URL absoluta retorna bytes. RestClient constroi com baseUrl mas aceita URI absoluto no segundo .uri() — segue automaticamente. Bearer header repetido em ambos por causa C-14 (CDN do Meta tambem precisa)."
    - "Pattern 'Optional graceful para 404 em qualquer step': URL Meta tem TTL 5min (PITFALLS C-08); async queue pode atrasar. baixar() captura HttpClientErrorException.NotFound em ambos os try-catch + valida metadata.url null + valida bytes vazio = 4 vias para Optional.empty. Listener Wave 5 trata como mensagem-sem-bytes."
    - "Pattern 'sem Resilience4j para primeira acao apos ack': janela 5min e curta — circuit breaker + retry adicionariam latencia desnecessaria. 5xx escapam como HttpServerErrorException; listener tem try/catch generico em volta. 4xx categoricos (incluindo 404) ja sao tratados via Optional.empty."
    - "Pattern 'WireMock standalone @SpringBootTest + @DynamicPropertySource': WireMockServer.dynamicPort em @BeforeAll/AfterAll + registry.add('app.modulos.whatsapp.metaApiBaseUrl', () -> wm.baseUrl()) sobrescreve property antes do Spring context inicializar — RestClient nasce ja apontado para o mock. Reusable em Wave 4 (ErpCallbackClient) e Wave 6 (E2E com Meta + ERP simultaneamente)."
    - "Pattern 'verify(0, ...)' para short-circuit assertions: WireMock count == 0 em URL nao chamada confirma que controle de fluxo curto-circuitou onde deveria (ex: 404 step 1 -> step 2 nao chamado). Mais explicito que assertion negativa textual."
    - "Pattern 'getAllServeEvents().forEach' para regression test C-14: percorre todos os requests recebidos pelo WireMock e assert .doesNotContain('access_token=') em cada URL. Mais robusto que verify-with-query-matcher (que tem semantica complexa)."

key-files:
  created:
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MetaMediaClient.java
    - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/MetaMediaClientTest.java
    - .planning/phases/03-roteamento-boundary-async/03-03-SUMMARY.md
  modified:
    - .planning/STATE.md
    - .planning/ROADMAP.md
    - .planning/REQUIREMENTS.md

key-decisions:
  - "MetaMediaClient SEM Resilience4j (decisao consciente per CONTEXT D-04): primeira acao apos ack precisa ser RAPIDA — janela Meta de 5min para URL temporaria. Circuit breaker + retry adicionariam latencia que pode quebrar a janela em pico. Listener Wave 5 (com try/catch generico) e o ponto onde falha de download vira mensagem-sem-bytes para o ERP. ErpCallbackClient (Wave 4) ai sim tera Resilience4j (D-03) — janela de retry e diferente."
  - "Bearer accessToken via header HttpHeaders.AUTHORIZATION em CADA .header() de cada request — NAO via interceptor global no RestClient.builder(). Decisao deliberada: explicitness > magic. Test bearer_nunca_em_query_param valida regression PITFALLS C-14 percorrendo todos os getAllServeEvents() do WireMock + assertion .doesNotContain('access_token=')."
  - "6 tests vs 5 minimos do PLAN (Rule 2 — defesa em profundidade): adicionado test metadata_sem_url_retorna_empty cobrindo cenario onde Meta retorna 200 mas JSON sem campo 'url'. Custo: +1 stub WireMock; beneficio: regression test contra Meta mudar contrato sem aviso (ja documentado como Risk em 03-02-SUMMARY). Branch existia no codigo (linha 'metadata == null || metadata.getUrl() == null') mas nao tinha test. Cobertura agora completa."
  - "Test cinco_centos_step_1_propaga_sem_retry valida indiretamente A6 (Risk WhatsApp Wave 1 — AOP precisa do spring-boot-starter-aop em compile dep). Counter == 1 confirma que NAO houve retry em 5xx. Se Resilience4j fosse acidentalmente acionado neste cliente (config errada copiada de Wave 4), o test quebraria. Pattern reusable em Wave 4 para validar o COMPORTAMENTO OPOSTO (counter == 3 quando retry esta configurado)."
  - "@SpringBootTest(classes = WhatsAppApplication.class) em vez de @SpringBootTest sem qualifier — WhatsAppApplication tem scanBasePackages e @EnableConfigurationProperties; sem qualifier, Spring buscaria @SpringBootConfiguration via heuristica e potentialmente acharia config diferente. Pattern alinhado com WhatsAppPropertiesHappyPathTest (Phase 1)."
  - "WireMock 3.10.0 standalone empiricamente validado: porta dinamica via dynamicPort() funciona, .baseUrl() retorna 'http://localhost:{porta}' usavel em @DynamicPropertySource, ambos os request stubs (urlEqualTo + URL relativa /MEDIA-ID-X e absoluta {baseUrl}/cdn/...) sao matched corretamente. Conflito Jetty 12 com Boot 3.5.9 NAO se manifestou — bloqueador documentado em 03-01-SUMMARY (Concerns Wave 3) resolvido."
  - "Imports na ordem da convencao do monorepo: jakarta/java -> org.springframework -> br.com.erpkit (verificado contra HmacValidator/IdempotencyService/MensagemService Phase 2). 4-space indent. UTF-8. Sem Lombok. PT-BR em Javadoc per CLAUDE.md."

patterns-established:
  - "Pattern 'WireMock 3.10.0 + @DynamicPropertySource para integration test de cliente HTTP': padrao 8 linhas de boilerplate (BeforeAll start dynamicPort, AfterAll stop, DynamicPropertySource registry.add com baseUrl, BeforeEach resetAll). Reusable identicamente em Wave 4 ErpCallbackClient + Wave 6 E2E (com 2 instances WireMockServer simultaneamente: 1 para Meta, 1 para ERP)."
  - "Pattern 'verify(0, getRequestedFor) para short-circuit': WireMock count == 0 em URL que NAO deveria ser chamada confirma controle de fluxo. Em 404 step 1, garante que step 2 nao foi tentado (curto-circuita corretamente em Optional.empty)."
  - "Pattern 'getAllServeEvents.forEach' para regression test sobre TODAS as requests: percorre lista de requests + assertion sobre cada URL. Mais flexivel que verify-with-matcher para checks negativos compostos (ex: nao deve conter 'access_token=' em NENHUMA das requests)."
  - "Pattern 'cliente HTTP defensivo de cabo a rabo': captura HttpClientErrorException.NotFound em cada step + valida null/empty em cada DTO/byte[] retornado. 4 vias para Optional.empty contra falha real de uplink + falha de contrato Meta + URL invalida + bytes vazios. Custo zero em runtime feliz."
  - "Pattern 'Bearer header per-request explicito vs interceptor global': cada .header(HttpHeaders.AUTHORIZATION, 'Bearer ' + token) em cada call e mais verboso que interceptor mas (a) mais facil de auditar visualmente que C-14 esta cumprido, (b) facilita override per-request se necessario, (c) zero risco de interceptor mal configurado vazar token via query param."

requirements-completed:
  - ROU-05  # Download de media entrante e a PRIMEIRA acao apos ack — URL Meta expira em 5min; bytes guardados em memoria pra entregar pro ERP no callback. Listener Wave 5 chamara metaMediaClient.baixar(mediaId) como primeira acao do @TransactionalEventListener(AFTER_COMMIT) — Wave 3 entrega o cliente isolado com pattern de defesa contra 404+timeout+contrato.

duration: ~9min
completed: 2026-05-05
---

# Phase 03 Plan 03: MetaMediaClient + WireMock Tests Summary

**Wave 3 da Phase 3 — cliente HTTP 2-step para download de media de entrada do Meta Graph API: MetaMediaClient @Service novo (Bearer SEMPRE em header per PITFALLS C-14, 404 graceful via Optional.empty em qualquer step per PITFALLS C-08, sem Resilience4j por design D-04) + MetaMediaClientTest com 6 tests WireMock 3.10.0 standalone (happy path 2-step / 404 step 1 / 404 step 2 / 5xx sem retry / bearer header presente + nunca em query / metadata sem URL). Reator BUILD SUCCESS, 132 tests verdes (126 prev + 6 novos), zero regressao em Phase 1+2 ou Wave 1+2. ROU-05 satisfeito (cliente isolado pronto para Wave 5 listener consumir como primeira acao apos ack). Pattern WireMock + @DynamicPropertySource estabelecido para reuso em Wave 4 e Wave 6.**

## Performance

- **Duration:** ~9 min
- **Started:** 2026-05-05T20:59:54Z
- **Completed:** 2026-05-05T21:08:33Z
- **Tasks:** 3 (Task 1 MetaMediaClient + Task 2 MetaMediaClientTest 6 tests + Task 3 build/SUMMARY/commits)
- **Files changed:** 5 (2 created src+test + 3 modified .planning/STATE.md + .planning/ROADMAP.md + .planning/REQUIREMENTS.md, exclui SUMMARY)

## Accomplishments

- **`service/MetaMediaClient.java` novo** — Cliente HTTP @Service com 2 etapas:
  - **Constructor**: recebe `WhatsAppProperties`, constroi `RestClient.builder().baseUrl(properties.getMetaApiBaseUrl()).build()` — base URL configuravel via Wave 1 (default `https://graph.facebook.com/v22.0`, override em test via `@DynamicPropertySource`).
  - **`baixar(String mediaId) -> Optional<MetaMediaResultado>`**:
    - Step 1: `restClient.get().uri("/{id}", mediaId).header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken()).retrieve().body(MediaMetadataDTO.class)`. Catch `HttpClientErrorException.NotFound` -> log.warn + Optional.empty (PITFALLS C-08).
    - Defesa: `metadata == null || metadata.getUrl() == null` -> Optional.empty + log.warn.
    - Step 2: `restClient.get().uri(metadata.getUrl()).header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken()).retrieve().body(byte[].class)`. URL absoluta (lookaside.fbsbx.com) — RestClient segue. Catch `HttpClientErrorException.NotFound` -> Optional.empty.
    - Defesa: `bytes == null || bytes.length == 0` -> Optional.empty + log.warn.
    - Sucesso: `Optional.of(new MetaMediaResultado(bytes, metadata.getMimeType(), metadata.getFilename()))`.
  - **Sem Resilience4j**: 5xx escapa como `HttpServerErrorException`; listener Wave 5 tem try/catch generico que loga + segue sem media.
  - **Javadoc PT-BR completo**: documenta D-04, PITFALLS C-08 + C-14, decisao "sem Resilience4j", defesa em profundidade.

- **`test/.../MetaMediaClientTest.java` novo** — 6 tests `@SpringBootTest(classes = WhatsAppApplication.class) @ActiveProfiles("test")`:
  - **Setup**: `WireMockServer wireMock` static iniciado em `@BeforeAll` com `WireMockConfiguration.options().dynamicPort()`, parado em `@AfterAll`. `@DynamicPropertySource` sobrescreve `app.modulos.whatsapp.metaApiBaseUrl` com `wireMock.baseUrl()`. `@BeforeEach wireMock.resetAll()` isola tests.
  - **happy_path_2_step_retorna_resultado_completo**: stub step 1 com JSON `{url: {baseUrl}/cdn/MEDIA-ID-001/data, mime_type: application/pdf, filename: fatura.pdf, ...}` + stub step 2 com bytes `[1,2,3,4,5]`. Assert Optional preenchido com bytes/mimeType/filename corretos. **Assert ambas requests com `Authorization: Bearer test-access-token` via `wireMock.verify(getRequestedFor.withHeader)`.**
  - **quatrocentos_quatro_step_1_retorna_empty**: WireMock 404 em `/MEDIA-ID-EXPIRED`. Optional.empty. **`verify(0, getRequestedFor(urlMatching("/cdn/.*")))` confirma step 2 NAO chamado** (short-circuit).
  - **quatrocentos_quatro_step_2_retorna_empty**: step 1 retorna 200 com URL valida; step 2 retorna 404. Optional.empty.
  - **cinco_centos_step_1_propaga_sem_retry**: stub 500. `assertThatThrownBy(...).isInstanceOf(HttpServerErrorException.class)` confirma propagacao. **`verify(1, getRequestedFor)` confirma counter == 1 (sem retry — sem Resilience4j neste cliente, valida A6 indiretamente).**
  - **bearer_nunca_em_query_param**: stub happy path. Apos baixar, **`wireMock.getAllServeEvents().forEach(event -> assertThat(event.getRequest().getUrl()).doesNotContain("access_token="))`** — regression test PITFALLS C-14 que percorre TODAS as requests recebidas (nao apenas as conhecidas).
  - **metadata_sem_url_retorna_empty**: stub step 1 com JSON sem campo "url" (`{mime_type: image/png, id: ...}`). Optional.empty + step 2 nao chamado. Rule 2 — defesa em profundidade contra Meta mudar contrato.

- **Reator `mvnw verify -pl api-whatsapp -am`:** **BUILD SUCCESS**, **132 tests verdes (126 Phase 1+2+Wave 1+2 + 6 novos), 0 falhas, 0 erros, zero regressao**. MetaMediaClientTest executa em ~7s no run isolado e ~2.6s no run integrado (Spring context cache reutilizado).

## Decisions Made

- **D1 — Sem Resilience4j em MetaMediaClient (per CONTEXT D-04):** primeira acao apos ack precisa ser RAPIDA — URL Meta tem TTL de 5min (PITFALLS C-08). Circuit breaker + retry adicionariam latencia que poderia quebrar a janela em pico. Falha de download = mensagem persistida sem bytes (callback ERP recebe payload sem `mediaBase64`); nao critico. ErpCallbackClient (Wave 4) sim tera Resilience4j (D-03) — semantica diferente: ERP esta sob nosso controle e janela de retry tolera latencia.

- **D2 — Bearer header per-request explicito vs interceptor global:** Cada `.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())` em cada chamada. Mais verboso que `RestClient.builder().requestInterceptor(...)` global, mas (a) mais facil de auditar visualmente que C-14 esta cumprido, (b) facilita override per-request, (c) zero risco de interceptor mal configurado escrever token em query param. Test `bearer_nunca_em_query_param` enforce regression.

- **D3 — 6 tests vs 5 minimos do PLAN (Rule 2 — defesa em profundidade):** Adicionado `metadata_sem_url_retorna_empty` cobrindo branch `metadata.getUrl() == null` (linha 78 do MetaMediaClient.java). Branch existia no codigo desde a primeira versao mas nao tinha test — risco silencioso de regressao se alguem reescrevesse a validacao. Custo: +1 stub WireMock + ~5 linhas; beneficio: regression test explicito contra Meta mudar contrato. Risk documentado em 03-02-SUMMARY (`Risk: Jackson MediaMetadataDTO pode quebrar se Meta mudar formato`) agora endereçado.

- **D4 — `@SpringBootTest(classes = WhatsAppApplication.class)` em vez de `@SpringBootTest` sem qualifier:** `WhatsAppApplication` tem `scanBasePackages` + `@EnableConfigurationProperties(WhatsAppProperties.class)`. Sem qualifier, Spring busca `@SpringBootConfiguration` via heuristica e potentialmente aceita config diferente da producao. Pattern alinhado com `WhatsAppPropertiesHappyPathTest` (Phase 1) e `WebhookPersistenciaIntegrationTest` (Phase 2).

- **D5 — `verify(0, getRequestedFor)` para short-circuit assertions:** count == 0 em URL nao chamada confirma controle de fluxo curto-circuitou. Em `quatrocentos_quatro_step_1`, garante que step 2 nao foi tentado (curto-circuita corretamente em Optional.empty). Mais explicito que checar log mensagens ou estado interno.

- **D6 — `getAllServeEvents().forEach` para regression test C-14:** percorre TODAS as requests recebidas pelo WireMock + assertion `.doesNotContain("access_token=")` em cada URL. Mais robusto que `wireMock.verify(0, getRequestedFor(urlMatching(".*[?&]access_token=.*")))` — que dependeria de matcher regex correto. forEach valida em LISTA, captura qualquer query param novo que pudesse vazar.

- **D7 — `getMimeType()` (camelCase Java) NAO `getMime_type()`:** Per concern explicito de 03-02-SUMMARY: `MediaMetadataDTO` usa `@JsonProperty("mime_type")` mapeando snake_case JSON do Meta para camelCase Java getter. `MetaMediaClient` chama `metadata.getMimeType()` corretamente (verificado via build verde + happy_path test passando com `assertThat(resultado.get().mimeType()).isEqualTo("application/pdf")`).

## Risks & Mitigations

- **Risk: Meta pode mudar shape do JSON de metadata sem aviso (rename `url` -> `signed_url`, `mime_type` -> `content_type`).**
  - Mitigacao: `@JsonIgnoreProperties(ignoreUnknown=true)` em MediaMetadataDTO (Wave 2) tolera campos NOVOS mas nao renames. Test `metadata_sem_url_retorna_empty` valida que ausencia de `url` -> Optional.empty (graceful degradation, nao crash). Em prod, log.warn aparecera com mediaId, alertando operador. Mitigacao adicional: monitorar Meta release notes (PROJECT.md flag operacional) + Phase 6 RUNBOOK pode incluir secao de troubleshooting.

- **Risk: HttpClientErrorException.NotFound nao engloba TODOS os 4xx — outros 4xx (401 invalid token, 403 permission denied, 429 rate limit) escaparam como HttpClientErrorException generico.**
  - Mitigacao: Listener Wave 5 tem try/catch generico em volta da chamada. 401/403 indicam config errada (env var WHATSAPP_ACCESS_TOKEN obsoleto/sem escopo) — falha rapida + log.error e CORRETO comportamento (operador precisa atuar, retry nao resolve). 429 idealmente teria retry com backoff mas em janela 5min e arriscado — preferir falhar e notificar. Documentado.

- **Risk: 5xx propagacao escapa exception nao-gerenciada para fora do listener Wave 5 se try/catch nao for amplo o suficiente.**
  - Mitigacao: Wave 5 PLAN deve garantir try/catch generico (`catch (Exception e)`) em volta de `metaMediaClient.baixar(...)`. Test `cinco_centos_step_1_propaga_sem_retry` confirma comportamento esperado do MetaMediaClient (nao captura). O contrato e: cliente HTTP deve ser PREVISIVEL e DETERMINISTICO; tratamento de excecao e responsabilidade do orquestrador (listener) — Single Responsibility.

- **Risk: WireMock 3.10.0 standalone (Jetty 12 shadow) com Boot 3.5.9 — bloqueador documentado em 03-01-SUMMARY.**
  - **RESOLVIDO**: 6 tests passam empiricamente em ~2.6s (run integrado) e ~7s (run isolado com cold start de Spring context). Sem conflito de classpath/Jetty. Validacao formal alcancada. Pattern reusable para Wave 4 + Wave 6 sem hesitacao.

## TDD Gate Compliance

Plan type: `execute` (nao TDD por default — sem RED/GREEN/REFACTOR ciclo declarado). Tests criados em paralelo com producao (Task 2 com Task 1 ja completa). Aceitavel para infra plan onde unit/integration tests sao escritos de forma deductiva a partir da spec do 2-step + comportamento ja documentado em RESEARCH (sem behavior emergente para descobrir via TDD).

## Self-Check: PASSED

- File `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MetaMediaClient.java` criado: FOUND
- File `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/MetaMediaClientTest.java` criado: FOUND
- Commit `feat(03-03): MetaMediaClient com 2-step Graph API + WireMock tests` (hash `380e071`): FOUND in `git log`
- Build `./mvnw verify -pl api-whatsapp -am`: **BUILD SUCCESS** com 132 tests verdes (126 prev + 6 novos), zero regressao, 0 falhas, 0 erros
- Surefire report `MetaMediaClientTest`: Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.622s (run integrado) / 7.173s (run isolado cold)
- ROU-05 verificavel: `MetaMediaClient.baixar(String mediaId)` implementa 2-step download com 404 graceful via Optional.empty + bearer SOMENTE em header — pronto para listener Wave 5 chamar como primeira acao apos ack do `@TransactionalEventListener(AFTER_COMMIT)`

## Concerns para Wave 4 (PLAN 03-04 — ErpCallbackClient com Resilience4j)

- **WireMock setup pattern reusable identicamente:** copiar bloco `@BeforeAll/AfterAll/BeforeEach + @DynamicPropertySource` de `MetaMediaClientTest`. Substituir `metaApiBaseUrl` por `erpCallbackUrl` no `registry.add`. Setup ~8 linhas iguais.
- **Counter assertion espera == 3 em vez de == 1 (oposto deste plan):** Wave 4 ErpCallbackClient TEM Resilience4j `@Retry(name="erp-callback")` configurado em `application-test.yml` (max-attempts 3). Em test de 5xx em ErpCallbackClient, esperar `wireMock.verify(3, postRequestedFor)` — confirma que retry AOP esta funcionando (Risk A6 validacao formal). Contraste com este plan (counter == 1 confirma SEM retry).
- **HttpClientErrorException 4xx nao deve retentar:** `application-test.yml` linha 103-106 configura `retry-exceptions` whitelist (`HttpServerErrorException + SocketTimeoutException + IOException`) — Resilience4j default NAO retenta excecoes nao listadas. Wave 4 deve ter test 4xx confirmando counter == 1 (vs counter == 3 em 5xx). Per ROU-03 (4xx duplicaria side effect no ERP).
- **Circuit breaker abre depois de 4 dispatches falhos:** Per nota de 03-01 STATE: "Retry POR FORA de CircuitBreaker. 1 dispatch falho = 3 calls counted no CB sliding-window. 4 dispatches falhos consecutivos = 12 calls counted = circuit aberto." Wave 4 pode ter test que exercita 4 falhas seguidas + 5a chamada com `assertThatThrownBy(...).isInstanceOf(CallNotPermittedException.class)` confirmando estado OPEN.
- **Fallback method NAO retenta (D-08, ROU-03):** `private void fallbackDespachar(payload, Throwable t)` apenas loga e retorna void. Wave 4 deve ter test `fallback_NAO_dispara_request_adicional` validando que fallback nao chama `restClient` novamente (zero dispatches no WireMock apos circuit open).
- **Timeout via SimpleClientHttpRequestFactory:** Wave 4 ErpCallbackClient constroi `RestClient.builder().requestFactory(timeoutFactory(properties.getCallbackTimeout()))` — pattern de WhatsAppProperties.callbackTimeout (Duration default 5s). Test pode exercitar com WireMock `withFixedDelay(Long)` simulando timeout.

## Concerns para Wave 5 (PLAN 03-05 — MensagemAsyncListener)

- **`metaMediaClient.baixar(mediaId)` deve ser PRIMEIRA acao apos `@TransactionalEventListener` invocar (per D-04):** ANTES de chamar `clienteZapService.identificar` ou `comandoExtractor.extrair`. Justificativa: URL Meta 5min TTL, async queue pode atrasar. Se identificar/extrair forem cabecudos, URL pode expirar.
- **Try/catch em volta de `baixar(...)` deve ser GENERICO (catch Exception e):** MetaMediaClient propaga 5xx + outros 4xx (401/403/429) sem captura. Listener deve `catch (Exception e) { log.warn("Falha ao baixar media: wamid={}, mediaId={}: {}", ...); }` + prosseguir sem media. Sem essa captura, listener crasha + ERP nunca recebe callback.
- **`Optional<MetaMediaResultado>` -> base64 transformation:** `media.ifPresent(m -> { mediaBase64 = Base64.getEncoder().encodeToString(m.bytes()); mediaMimeType = m.mimeType(); mediaFilename = m.filename(); })` — usar `java.util.Base64.getEncoder().encodeToString()` (NAO Apache Commons). Output vai direto pro `ComandoCallbackDTO`.
- **`mediaId == null` short-circuit:** Listener deve checar `if (event.mediaId() != null) { ... baixar ... }`. Eventos de TEXT/INTERACTIVE nao tem mediaId; pular o passo evita request desnecessario ao Graph API.
- **Pattern WireMock para test do listener:** Wave 5 test pode usar 2 WireMockServer (1 para Meta `/MEDIA-ID-X` + 1 para ERP `/api/modulos/whatsapp/comando`) com 2 `@DynamicPropertySource` registrations — ou 1 WireMock com paths distintos `/v22.0/MEDIA-ID-X` + `/api/modulos/...` e mesma `baseUrl`. Decidir em Wave 5 PLAN.

## References

- CONTEXT.md §D-04 (MetaMediaClient + DTOs) — implementado integralmente
- RESEARCH.md §"MetaMediaClient — codigo completo" + §"Pattern 3: 2-Step Media Download" + §"WireMock test patterns"
- ROADMAP §Phase 3 §SC-4 ("Media entrante tem URL Meta baixada e bytes guardados como **primeira** acao async apos o ack 200")
- REQUIREMENTS §ROU-05 — completo (cliente isolado entregue; primeira acao apos ack sera implementada em Wave 5)
- PITFALLS §C-08 (URL Meta expira em 5min) + §C-14 (Bearer token NUNCA em query param)
- 03-01-SUMMARY (Wave 1 — WhatsAppProperties.metaApiBaseUrl + WireMock 3.10.0 dep + spring.http.client timeouts)
- 03-02-SUMMARY (Wave 2 — MediaMetadataDTO Jackson POJO `getMimeType()` + MetaMediaResultado record)
- `lib-consultas-client/ConsultasClientImpl.java` — pattern de RestClient + RestClient.builder reusavel (sem Resilience4j aqui mas mesmo skeleton)
