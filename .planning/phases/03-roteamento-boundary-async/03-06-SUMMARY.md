---
phase: 03-roteamento-boundary-async
plan: 06
subsystem: api-whatsapp
tags:
  - api-whatsapp
  - integration-test
  - e2e
  - wiremock
  - sync-task-executor
  - phase-gate
  - phase-3-closeout
  - risk-a1-resolved
  - risk-a6-resolved
dependency-graph:
  requires:
    - "03-05"  # MensagemAsyncListener + MensagemService fast-path com @Transactional
  provides:
    - "AsyncTestConfig (SyncTaskExecutor) para tests E2E manterem assertions DB sincronas"
    - "WebhookPersistenciaIntegrationTest reativado (13 tests Phase 2 voltam verdes)"
    - "WebhookAsyncIntegrationTest (5 tests cobrindo SC-2/3/4/5 + bonus)"
    - "WebhookAsyncTimingIntegrationTest (1 test cobrindo SC-1 timing com pool real)"
    - "Phase 3 closeout — todos os 5 ROADMAP success criteria com automated tests verdes"
    - "Risk A1 (HIGH) RESOLVED empiricamente via SC-2 (callback ERP recebe payload)"
    - "Risk A6 (HIGH) RESOLVED empiricamente via SC-3 (3 tentativas Resilience4j)"
  affects:
    - "Phase 4 (outbound) — gate empirico Phase 3 verde"
tech-stack:
  added:
    - "org.awaitility:awaitility (test scope, versao via Spring Boot BOM)"
  patterns:
    - "@TestConfiguration com SyncTaskExecutor + @ConditionalOnMissingBean no bean de prod — test bean tem precedencia sem precisar bean override flag"
    - "@Transactional(propagation = NOT_SUPPORTED) no listener AFTER_COMMIT — suspende residual TransactionSynchronization que ficaria ativo em SyncTaskExecutor inline e impediria @Transactional REQUIRES_NEW de ver seu proprio save"
    - "WireMock dynamic port + @DynamicPropertySource em 2 servers (ERP + Meta) — pattern reusable Phase 4+"
    - "@BeforeEach com jdbc.update DELETE FROM mensagens_log + clientes_zap — H2 in-memory tem DB_CLOSE_DELAY=-1 e mesma URL entre SpringContexts"
    - "@Async + classe SEPARADA SEM AsyncTestConfig para validar timing com pool real (vs @Nested config local)"
    - "Awaitility await().atMost.untilAsserted para validar listener async eventualmente roda em pool real"
key-files:
  created:
    - "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/config/AsyncTestConfig.java"
    - "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookAsyncIntegrationTest.java"
    - "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookAsyncTimingIntegrationTest.java"
  modified:
    - "api-whatsapp/pom.xml (adicionado org.awaitility:awaitility test scope)"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/AsyncConfig.java (@ConditionalOnMissingBean no whatsappTaskExecutor + return type TaskExecutor)"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MensagemAsyncListener.java (@Transactional(propagation = NOT_SUPPORTED) na method aoMensagemPersistida)"
    - "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookPersistenciaIntegrationTest.java (removido @Disabled + @Import(AsyncTestConfig) + WireMock stub para ERP)"
    - ".planning/ROADMAP.md (Phase 3 marcada [x] + 6/6 plans completos + tabela Progress atualizada)"
decisions:
  - "@ConditionalOnMissingBean(name='whatsappTaskExecutor') no AsyncConfig de PROD em vez de spring.main.allow-bean-definition-overriding=true no test profile — solucao mais cirurgica (production code unaffected, test bean ganha precedencia naturalmente sem flag)"
  - "@Transactional(propagation = NOT_SUPPORTED) no listener — descoberta empirica via TRACE logging (sem ele, identificar() save nao gera INSERT em SyncTaskExecutor inline porque residual TransactionSynchronization da Tx outer faz @Transactional REQUIRED do identificar virar JOIN no-op em uma Tx ja committed)"
  - "Cleanup @BeforeEach via JdbcTemplate DELETE em WebhookAsyncIntegrationTest e WebhookAsyncTimingIntegrationTest — diferente do WebhookPersistenciaIntegrationTest (que usa wamids unicos), aqui sc2/sc3/sc5 reusam text-portugues.json com mesmo wamid; alem disso, H2 in-memory compartilhado entre SpringContexts (mesma URL + DB_CLOSE_DELAY=-1) requer cleanup explicito para isolacao entre classes"
  - "WebhookAsyncTimingIntegrationTest classe SEPARADA (vs @Nested) — @Import(AsyncTestConfig.class) e annotation de classe; ter SC-1 com pool real e SC-2/3/4/5 com SyncTaskExecutor em mesma classe exigiria @Nested com config separada (mais complexo); 2 classes separadas = 2 SpringContexts cached separadamente (mais simples e robusto)"
  - "WireMock.post() fully qualified em vez de import static — conflito com MockMvcRequestBuilders.post() (mesmo nome, signaturas diferentes); fully qualified resolve sem precisar reorganizar imports"
metrics:
  duration: "~35min (3 tasks: AsyncTestConfig + reabilitacao + 2 tests novos + ROADMAP closeout)"
  completed: "2026-05-05T22:35:00Z"
  tasks: 3
  files: 8
requirements_satisfied:
  - "ROU-01"  # publishEvent + listener async dispatch — verificado E2E via SC-2 (callback ERP recebido)
  - "ROU-02"  # POST callback payload format {telefone, comando, payload, idCliente, mediaBase64?} — SC-2 valida JSON
  - "ROU-03"  # CB + retry 3x — SC-3 confirma 3 tentativas Resilience4j em 5xx persistente
  - "ROU-04"  # Timeout + log error sem trava webhook — SC-1 (ack <1s) + SC-3 (5xx nao propaga)
  - "ROU-05"  # Media download primeira acao + base64 no callback — SC-4 confirma timing + base64 == "AQIDBAU="
---

# Phase 3 Plan 06: Integration tests E2E + Phase 3 Closeout Summary

`AsyncTestConfig` novo (`@TestConfiguration` + `@Bean("whatsappTaskExecutor")` `SyncTaskExecutor`) substitui o pool dedicado em testes selecionados; `WebhookPersistenciaIntegrationTest` reabilitado (13 tests Phase 2 voltam verdes); `WebhookAsyncIntegrationTest` novo (5 tests cobrindo ROADMAP SC-2/3/4/5) + `WebhookAsyncTimingIntegrationTest` novo (1 test cobrindo SC-1 com pool real). `AsyncConfig` ganhou `@ConditionalOnMissingBean` para test bean ter precedencia sem `allow-bean-definition-overriding`. `MensagemAsyncListener` ganhou `@Transactional(NOT_SUPPORTED)` para suspender residual TransactionSynchronization (descoberta empirica via TRACE — bug subtil que so aparece em SyncTaskExecutor inline). Reator BUILD SUCCESS 152 tests verdes na api-whatsapp. ROADMAP Phase 3 marcada [x] (6/6 plans). Risk A1 + A6 RESOLVED empiricamente via E2E.

## Files

### Created

- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/config/AsyncTestConfig.java` — `@TestConfiguration` com `@Bean(name="whatsappTaskExecutor") @Primary` retornando `new SyncTaskExecutor()`. Usado via `@Import(AsyncTestConfig.class)` em testes que verificam estado DB apos webhook (listener async vira sincrono inline na thread do MockMvc).
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookAsyncIntegrationTest.java` — `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Import(AsyncTestConfig.class)` com 5 tests cobrindo SC-2/3/4/5 + bonus. 2 WireMockServer estaticos (ERP + Meta), `@DynamicPropertySource` sobrescreve `erp-callback-url` e `metaApiBaseUrl`. Cleanup `@BeforeEach` (DELETE mensagens_log + clientes_zap, resetAll WireMocks, reset CB).
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookAsyncTimingIntegrationTest.java` — `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")` SEM `@Import(AsyncTestConfig.class)` — usa pool real para validar empiricamente que ack 200 retorna em <1s mesmo com ERP delay 10s. Awaitility valida que listener async eventualmente roda.

### Modified

- `api-whatsapp/pom.xml` — adicionado `org.awaitility:awaitility` em scope test (versao gerenciada via Spring Boot BOM).
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/AsyncConfig.java` — adicionado `@ConditionalOnMissingBean(name="whatsappTaskExecutor")` no metodo bean. Test config registra primeiro (via `@Import`); production bean nao registra. Sem flag de override no application-test.yml. Type changed from `ThreadPoolTaskExecutor` para interface `TaskExecutor` (mais polimorfico, alinhado com SyncTaskExecutor do test).
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MensagemAsyncListener.java` — adicionado `@Transactional(propagation = Propagation.NOT_SUPPORTED)` na method `aoMensagemPersistida`. Suspende residual TransactionSynchronization da Tx outer (do `processarWebhook` apos commit AFTER_COMMIT phase) — sem isso, em SyncTaskExecutor inline o `@Transactional REQUIRED` do `clienteZapService.identificar()` faz JOIN em uma Tx ja committed e o `repository.save()` nao gera INSERT (descoberta via Hibernate SQL TRACE).
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookPersistenciaIntegrationTest.java` — REMOVIDO `@Disabled` da classe; ADICIONADO `@Import(AsyncTestConfig.class)` + `WireMockServer wireMockErp` static + `@BeforeAll/AfterAll` start/stop + stub default 200 + `@DynamicPropertySource` para `erp-callback-url`. Os 13 tests originais Phase 2 sem mudanca individual.
- `.planning/ROADMAP.md` — Phase 3 marcada `[x]` no overview; checkbox `[x]` em 03-06-PLAN.md no detail; tabela Progress atualizada para `6/6 | Complete (awaiting verifier) | 2026-05-05`.

## Test Results

**19 tests novos/reabilitados verdes:**

| Test class                              | Tests | Time   | Estado                                          |
| --------------------------------------- | ----- | ------ | ----------------------------------------------- |
| `WebhookPersistenciaIntegrationTest`    | 13    | 2.25s  | REABILITADO (Phase 2, era @Disabled em Wave 5)  |
| `WebhookAsyncIntegrationTest`           | 5     | 2.35s  | NEW: SC-2/3/4/5 + bonus (text sem media)        |
| `WebhookAsyncTimingIntegrationTest`     | 1     | 1.65s  | NEW: SC-1 ack <1s com ERP delay 10s             |

**Tests listados:**

`WebhookAsyncIntegrationTest` (5):
- `sc2_callback_payload_correto_apos_webhook_text` — capturar request via `findAll(postRequestedFor)`, parsear via Jackson, asserts em `telefone="554784178525"` (DDD 47 strip 9), `comando` nao-vazio, `payload="Olá, gostaria de um orçamento"`, `idCliente=null`, `mediaBase64=null`
- `sc3_callback_5xx_persistente_webhook_ack_200_e_3_tentativas` — stub 500 sempre; mockMvc retorna 200; assert WireMock count == 3 (Resilience4j max-attempts)
- `sc4_media_download_primeira_acao_e_base64_no_callback` — stubs Meta (metadata + bytes [1,2,3,4,5]) + ERP 200; assert metaTime <= erpTime via getLoggedDate; assert payload.mediaBase64 == "AQIDBAU=" + mimeType "application/pdf" + filename "comprovante.pdf"
- `sc5_dois_webhooks_identicos_resultam_em_1_callback_erp` — 2 POSTs identicos; assert WireMock ERP count == 1 (idempotency gate via boolean novo)
- `bonus_text_sem_media_skip_metaMediaClient` — text fixture; assert WireMock Meta count == 0; assert WireMock ERP count == 1

`WebhookAsyncTimingIntegrationTest` (1):
- `sc1_ack_first_under_1s_mesmo_com_erp_delay_10s` — ERP delay 10s; assert elapsed < 1000ms; Awaitility valida listener async eventualmente fez request

## Build Status

- `./mvnw verify -pl api-whatsapp -am`: **BUILD SUCCESS** 152 tests run, 0 failures, 0 errors, 0 skipped (vs Wave 5: 146 run + 13 skipped)
- `./mvnw verify` (reator completo 7 modulos): **BUILD SUCCESS** — api-email, api-storage, api-consultas, lib-shared, lib-consultas-client, api-whatsapp todos verdes (zero regressao)

## Commits

- (this commit): feat(api-whatsapp): integration tests E2E Phase 3 + AsyncTestConfig (Phase 3 Wave 6)
- (next commit): docs(03): Phase 3 closeout — SUMMARY plan 06 + ROADMAP marcando completa

## Risk Status

### Risk A1 (HIGH): @TransactionalEventListener(AFTER_COMMIT) silently skipped — **RESOLVED EMPIRICAMENTE**

**Validacao final via SC-2 + SC-4:** Tests passaram, ERP recebeu callback com payload correto + media base64. Se o `@Transactional` em `MensagemService.processarWebhook` nao estivesse funcionando, listener nunca dispararia e WireMock nao receberia nada — `findAll(postRequestedFor)` retornaria empty list e assertion falharia. Risk RESOLVED tanto por design (Wave 5) quanto empiricamente (Wave 6).

**Bonus discovery:** `@Transactional(NOT_SUPPORTED)` no listener foi necessario para o SyncTaskExecutor funcionar corretamente. Em prod (pool real), a thread async tem proprio TransactionSynchronization scope limpo, entao o issue nao apareceria. Mas adicionar NOT_SUPPORTED nao prejudica prod (apenas afirma "este metodo nao precisa de Tx outer") e torna o listener mais robusto.

### Risk A6 (HIGH): Resilience4j AOP no-op silencioso — **RESOLVED EMPIRICAMENTE**

**Validacao final via SC-3:** WireMock recebeu exatamente 3 POSTs apos um POST do webhook com ERP retornando 500 sempre. Counter == 3 prova que `@Retry(name="erp-callback")` esta interceptado pelo `RetryAspect` do Spring AOP (max-attempts=3). Sem `spring-boot-starter-aop` no classpath, counter == 1. Risk ja foi resolvido em Wave 4 via `ErpCallbackClientTest.cinquecentos_recupera_counter_3`; Wave 6 confirma E2E via webhook real.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] BeanDefinitionOverrideException ao importar AsyncTestConfig**

- **Found during:** Task 1 (run inicial WebhookPersistenciaIntegrationTest)
- **Issue:** Spring Boot 3.5+ desabilita `BeanDefinitionOverride` por default. AsyncTestConfig com mesmo nome de bean (`whatsappTaskExecutor`) que AsyncConfig (prod) lanca `BeanDefinitionOverrideException` no boot do test context.
- **Fix:** Adicionado `@ConditionalOnMissingBean(name="whatsappTaskExecutor")` no metodo bean de `AsyncConfig` (prod). Solucao mais cirurgica que `spring.main.allow-bean-definition-overriding=true` no test profile (production code unaffected).
- **Files modified:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/AsyncConfig.java`

**2. [Rule 1 - Bug] Listener async em SyncTaskExecutor nao gerava INSERT no save() do identificar()**

- **Found during:** Task 1 (run WebhookPersistenciaIntegrationTest sc4/sc5 falhavam apos AsyncTestConfig)
- **Issue:** Hibernate SQL TRACE mostrou que `clienteZapService.identificar()` chamava `repository.findByTelefone` (vazio) mas o `repository.save(novo)` no `criarNovo()` NAO gerava INSERT. UPDATE de `atualizarUltimaMensagemEm` (REQUIRES_NEW) entao retornava 0 rows. Causa raiz: em SyncTaskExecutor inline, o listener AFTER_COMMIT roda na mesma thread que ainda tem residual TransactionSynchronization da Tx outer (apos commit mas antes de cleanup completo). O `@Transactional REQUIRED` do `identificar()` detecta "Tx ativa" e JOIN, mas a Tx outer ja foi committed entao o save fica em estado pending sem commit subsequente.
- **Fix:** `@Transactional(propagation = Propagation.NOT_SUPPORTED)` no listener method `aoMensagemPersistida`. Suspende qualquer Tx ativa antes de chamar identificar/atualizar — cada um abre sua propria Tx limpa.
- **Why prod nao foi afetado:** Pool real cria nova thread para cada async task; thread nova tem TransactionSynchronization scope limpo. Issue so manifesta em SyncTaskExecutor inline (test profile). NOT_SUPPORTED e idempotente em prod (apenas garante listener nao precisa Tx outer).
- **Files modified:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MensagemAsyncListener.java`

**3. [Rule 1 - Bug] WireMock.post() vs MockMvcRequestBuilders.post() name conflict em todos os 3 tests**

- **Found during:** Task 2 (compilation falhou — `cannot find symbol method header` apos confundir `MappingBuilder` com `MockHttpServletRequestBuilder`)
- **Issue:** Imports estaticos `WireMock.post` e `MockMvcRequestBuilders.post` colidem (mesmo nome, signaturas diferentes). Java tries a primeira import e usa MappingBuilder (do WireMock), entao `.header(...)` nao existe.
- **Fix:** Removido `import static WireMock.post`; chamadas WireMock usam `WireMock.post(...)` fully qualified (3 sites em WebhookAsyncIntegrationTest + 1 em WebhookAsyncTimingIntegrationTest + 1 em WebhookPersistenciaIntegrationTest).
- **Files modified:** todos os 3 tests + reabilitado.

**4. [Rule 1 - Bug] H2 in-memory compartilhado entre SpringContexts requer cleanup explicito**

- **Found during:** Task 2 (run conjunto WebhookAsyncIntegrationTest + WebhookAsyncTimingIntegrationTest falhou — UnexpectedRollbackException + listener async nunca disparado)
- **Issue:** Tests da nova classe usam `text-portugues.json` (mesmo wamid `wamid.HBgN.text.001`) entre tests. Sem cleanup, 2o test bate UNIQUE constraint -> Tx outer rollback-only -> AFTER_COMMIT listener nao dispara -> ERP callback nunca acontece. Bonus: H2 in-memory tem `DB_CLOSE_DELAY=-1` e mesma URL entre SpringContexts; rows persistem entre classes de test diferentes.
- **Fix:** `@BeforeEach` com `jdbc.update("DELETE FROM whatsapp.mensagens_log")` + `clientes_zap` em ambas as classes novas (`WebhookAsyncIntegrationTest` + `WebhookAsyncTimingIntegrationTest`).
- **Why WebhookPersistenciaIntegrationTest nao precisou:** ele usa wamids unicos por test (.001, .002, .text.001, .btn.001, .doc.001, etc), evitando UNIQUE collision dentro do mesmo SpringContext.
- **Files modified:** `WebhookAsyncIntegrationTest.java` + `WebhookAsyncTimingIntegrationTest.java`.

### Auth Gates

Nenhum.

## Decisions Made

1. **`@ConditionalOnMissingBean` no AsyncConfig de prod (vs `allow-bean-definition-overriding=true` em test):** Solucao cirurgica — production code beneficia do guard (qualquer outro bean com nome `whatsappTaskExecutor` em qualquer profile/context teria precedencia sem flag global). Trade-off: ligeira mudanca em prod (1 annotation), zero risco de regressao (bean continua sendo criado quando ninguem mais define).

2. **`@Transactional(NOT_SUPPORTED)` no listener:** Em prod (pool real) e idempotente; em test (SyncTaskExecutor) e essencial. Decision: aplicar em prod tambem porque (a) torna listener mais robusto contra regressao em test profile, (b) explicitamente afirma "este metodo nao precisa de Tx outer" — semantica clara, (c) `clienteZapService.identificar()` e `atualizarUltimaMensagemEm()` ja gerenciam suas proprias Tx via `@Transactional`.

3. **2 classes separadas (vs `@Nested`) para SC-1 timing:** `@Import(AsyncTestConfig.class)` e annotation de classe — diferentes SpringContext caches. `@Nested` exigiria sobrescrever `@Import` em config local da nested, mais complexo. Trade-off: 2 SpringContexts startup overhead; ~7s extras no run, aceitavel pelo isolamento.

4. **Cleanup `@BeforeEach` via JdbcTemplate (vs `@Transactional` test):** `@Transactional` no test class faria rollback automatico apos cada test, mas isso quebraria o flow do `@TransactionalEventListener(AFTER_COMMIT)` (Tx do test wrap todo o flow + listener nunca dispara — comportamento conhecido do Spring). JdbcTemplate cleanup e direto e nao interfere com Tx hooks.

5. **Bonus test `bonus_text_sem_media_skip_metaMediaClient`:** Rule 2 add coverage — valida que MetaMediaClient NAO eh chamado quando mediaId == null (skip step 1 do listener). Plan especificava 4 SCs minimos; bonus regressao test contra mudanca futura no listener que pudesse chamar metaMediaClient sempre.

## Phase 3 Closeout

Phase 3 entregue 100%:
- 6/6 plans completos (03-01 a 03-06)
- 5/5 ROADMAP success criteria com automated tests verdes:
  - SC-1: `WebhookAsyncTimingIntegrationTest.sc1_ack_first_under_1s` (1 test)
  - SC-2: `WebhookAsyncIntegrationTest.sc2_callback_payload_correto` (1 test)
  - SC-3: `WebhookAsyncIntegrationTest.sc3_callback_5xx_persistente_3_tentativas` (1 test)
  - SC-4: `WebhookAsyncIntegrationTest.sc4_media_download_primeira_acao` (1 test)
  - SC-5: `WebhookAsyncIntegrationTest.sc5_dois_webhooks_identicos_1_callback` (1 test)
- 5/5 ROU requirements satisfeitos (ROU-01..05)
- Risk A1 (HIGH) RESOLVED por design (Wave 5) + empiricamente (Wave 6)
- Risk A6 (HIGH) RESOLVED empiricamente (Wave 4 via counter == 3 + Wave 6 via webhook E2E)

**Pronto para `gsd-verify-phase`** — gate empirico ativo, todos os 5 SC do ROADMAP sao automated tests no build do reator.

## TDD Gate Compliance

Plan tipo `execute` (nao `tdd`) — gates RED/GREEN/REFACTOR nao aplicaveis. 19 tests escritos/reabilitados junto com fix da implementacao.

## Self-Check: PASSED

**Files verified:**
- FOUND: api-whatsapp/src/test/java/br/com/erpkit/whatsapp/config/AsyncTestConfig.java (new)
- FOUND: api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookAsyncIntegrationTest.java (new)
- FOUND: api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookAsyncTimingIntegrationTest.java (new)
- FOUND: api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookPersistenciaIntegrationTest.java (modified — @Disabled removido + @Import AsyncTestConfig + WireMock stub)
- FOUND: api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/AsyncConfig.java (modified — @ConditionalOnMissingBean)
- FOUND: api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MensagemAsyncListener.java (modified — @Transactional(NOT_SUPPORTED))
- FOUND: api-whatsapp/pom.xml (modified — awaitility added)
- FOUND: .planning/ROADMAP.md (modified — Phase 3 closeout)

**Build verified:**
- `./mvnw verify -pl api-whatsapp -am`: BUILD SUCCESS, 152 tests verdes
- `./mvnw verify` (reator 7 modulos): BUILD SUCCESS

**Anti-pattern grep verified:**
- `grep -r "this\.despachar\|this\.aoMensagemPersistida" api-whatsapp/src/main/java/`: ZERO matches (sem self-call AOP killer)
