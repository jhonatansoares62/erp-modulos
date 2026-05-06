---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: verifying
stopped_at: Phase 04 closeout — 6/6 plans + 5/5 ROADMAP SC + 11/11 OUT reqs verdes; reator BUILD SUCCESS 266 tests; pronto para gsd-verify-phase 4-outbound-trava-24h-whatsappcontroller
last_updated: "2026-05-06T12:50:29.585Z"
last_activity: 2026-05-06
progress:
  total_phases: 6
  completed_phases: 4
  total_plans: 26
  completed_plans: 26
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-05)

**Core value:** Modulos reaproveitaveis entre ERPs ERPKit, sem custo operacional recorrente com terceiros — Modulo WhatsApp: custo zero de Meta garantido por design, nao por disciplina
**Current focus:** Phase 04 — outbound-trava-24h-whatsappcontroller

## Current Position

Phase: 5
Plan: Not started
Status: Phase 04 closeout entregue — pronto para gsd-verify-phase 4-outbound-trava-24h-whatsappcontroller
Last activity: 2026-05-06

Progress: [██████████] 100% (Phase 1 7/7 + Phase 2 7/7 + Phase 3 6/6 + Phase 4 6/6; Phases 1+2+3+4 awaiting verifier sign-off)

## Performance Metrics

**Velocity:**

- Total plans completed: 32 (Phase 1 7 + Phase 2 7 + Phase 3 6 + Phase 4 6)
- Average duration: ~13 min
- Total execution time: ~325 min

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 7/7 | ~60 min | ~8 min |
| 02 | 7/7 | ~70 min | ~10 min (Wave 1 spike 26m + Wave 2 TelefoneBR 3m + Wave B parallel + Wave C ClienteZap 24m + Wave D MensagemService 6m30s + Wave E integration tests 9m) |
| 03 | 6/6 | ~109 min | ~18 min (Wave 1 infra ~12min + Wave 2 tipos puros ~8min + Wave 3 MetaMediaClient ~9min + Wave 4 ErpCallbackClient ~17min + Wave 5 MensagemAsyncListener ~28min + Wave 6 closeout ~35min) |
| 04 | 6 | - | - |

**Recent Trend:**

- Last 6 plans (Phase 4): 04-01 (~25min), 04-02 (~30min), 04-03 (~15min), 04-04 (~25min), 04-05 (~12min), 04-06 (~15min)
- Trend: Phase 4 estavel 15-30min por plan; closeout (04-06) eficiente 15min com reator empirico verde + 3 file updates + SUMMARY rico

*Updated after each plan completion*
| Phase 01 P03 | 9min | 6 tasks | 5 files |
| Phase 01 P04 | 12min | 8 tasks (1 spike + 7 dev) | 9 files (4 SQL + 1 test + 2 yml + 1 SUMMARY + 1 .gitkeep delete) |
| Phase 01 P05 | 10min | 4 tasks | 5 files |
| Phase 01 P06 | 7min | 5 tasks | 8 files |
| Phase 01 P07 | 8min | 3 tasks | 2 files (1 test + 1 SUMMARY) |
| Phase 02 P01 | 26min | 9 tasks | 11 files (9 src + 2 yml mod) |
| Phase 02 P02 | 3min | 3 tasks | 2 files (1 utility + 1 test, 19 tests verdes) |
| Phase 02 P03 | 720 | 2 tasks | 2 files |
| Phase 02-persistencia-idempotencia P05 | 510 | 6 tasks | 24 files |
| Phase 02 P04 | 24min | 3 tasks | 3 files (1 repo modificado + 1 service novo + 1 test novo, 7 tests verdes) |
| Phase 02 P06 | 6min30s | 4 tasks | 4 files (1 service novo + 1 controller mod + 2 tests, 4 novos verdes, 99 reator) |
| Phase 02 P07 | ~9min | 3 tasks | 2 files (1 integration test E2E novo + ROADMAP mod; 13 tests novos verdes, 112 api-whatsapp aggregate, reator 7 modulos BUILD SUCCESS) |
| Phase 03 P01 | ~12min | 3 tasks | 7 files (2 created AsyncConfig + AsyncConfigSmokeTest; 5 mod: pom parent + api-whatsapp/pom + WhatsAppProperties + application.yml + application-test.yml; reator BUILD SUCCESS 113 tests verdes, zero regressao Phase 1+2) |
| Phase 03 P02 | ~8min | 3 tasks | 6 files (6 created: MensagemPersistidaEvent record + ComandoCallbackDTO record + MetaMediaResultado record + MediaMetadataDTO Jackson POJO + ComandoExtractor service + ComandoExtractorTest 13 tests; reator BUILD SUCCESS 126 tests verdes, zero regressao Phase 1+2 + Wave 1) |
| Phase 03 P03 | ~9min | 3 tasks | 5 files (2 created: MetaMediaClient + MetaMediaClientTest 6 tests WireMock; 3 modified: STATE.md + ROADMAP.md + REQUIREMENTS.md; reator BUILD SUCCESS 132 tests verdes (126 prev + 6 novos), zero regressao Phase 1+2 + Wave 1+2) |
| Phase 03 P04 | ~17min | 3 tasks | 5 files (2 created: ErpCallbackClient + ErpCallbackClientTest 6 tests WireMock; 3 modified: application-test.yml + application.yml + WhatsAppPropertiesHappyPathTest; reator BUILD SUCCESS 138 tests verdes (132 prev + 6 novos), zero regressao; Risk A6 RESOLVED empiricamente; 3 Rule 1 fixes documentados em SUMMARY) |
| Phase 03 P05 | ~28min | 4 tasks | 5 files (2 created: MensagemAsyncListener + MensagemAsyncListenerTest 8 tests Mockito; 3 modified: MensagemService refactor fast-path + @Transactional + ApplicationEventPublisher + MensagemServiceTest migrado para Mockito puro 4 tests + WebhookPersistenciaIntegrationTest @Disabled toda a classe Wave 6 reativa; reator BUILD SUCCESS 146 tests run, 0 failures, 13 skipped, zero regressao em outros modulos; Risk A1 RESOLVED por design via @Transactional) |
| Phase 03 P06 | ~35min | 3 tasks | 8 files (3 created: AsyncTestConfig + WebhookAsyncIntegrationTest 5 tests + WebhookAsyncTimingIntegrationTest 1 test; 5 modified: pom.xml awaitility + AsyncConfig @ConditionalOnMissingBean + MensagemAsyncListener @Transactional(NOT_SUPPORTED) + WebhookPersistenciaIntegrationTest reabilitado + ROADMAP.md Phase 3 [x] 6/6; reator BUILD SUCCESS 152 tests verdes vs 146+13skipped Wave 5; reator completo 7 modulos verde zero regressao; 5/5 ROADMAP SC + 5/5 ROU reqs verdes; Risk A1 + A6 ambos RESOLVED empiricamente; 4 Rule 1/3 bug fixes documentados; Phase 3 100% closeout pronta para gsd-verify-phase) |
| Phase 04 P01 | ~25min | 3 tasks | 7 files (3 created: CodigoCarrier + GlobalExceptionHandlerCodigoCarrierTest + MultipartUploadSpikeTest; 4 modified: ErrorResponse @JsonInclude NON_NULL + GlobalExceptionHandler instanceof CodigoCarrier + application.yml + application-test.yml com bloco whatsapp-cloud Resilience4j; 4 tests novos verdes — habilita D-02 + valida C-15 multipart 3 fields empiricamente para 04-04) |
| Phase 04 P02 | ~30min | 3 tasks | 7 files (6 created: JanelaConversaFechadaException + WindowEnforcementService + JanelaProtegida annotation + JanelaEnforcementAspect + WindowEnforcementServiceTest + JanelaEnforcementAspectTest; 1 modified: ClienteZapRepository +buscarUltimaMensagemEm native @Query; 6 tests novos verdes — D-03 trava 24h aspect HIGHEST_PRECEDENCE empiricamente PROVADO via counter==1 em 3 retries Resilience4j) |
| Phase 04 P03 | ~15min | 1 task TDD | 2 files (2 created: MediaCacheService + MediaCacheServiceTest; 4 tests novos verdes — D-04 TTL estrito 30d sem sliding + race save+catch DataIntegrityViolation 3a aplicacao consecutiva) |
| Phase 04 P04 | ~25min | 3 tasks | 7 files (7 created: MetaApiException enum Tipo + EnvioResponse + BotaoDto + ItemDto + SecaoDto + WhatsAppCloudClient com 4 metodos publicos + uploadMedia + classificar + WhatsAppCloudClientTest 13 tests; OUT-01..05 + OUT-08..10; gate dual OUT-05 reflection + grep test impossivel de regredir; gotcha 03-04 reaproveitado fallbackMethod no @Retry counter==3 valida; 1 fix post-merge Rule 1 classifier-direct test) |
| Phase 04 P05 | ~12min | 3 tasks | 7 files (7 created: 5 DTOs request/response + WhatsAppController 5 endpoints + WhatsAppControllerTest 13 tests @WebMvcTest; OUT-11 + D-01 base64 JSON regular + D-04 status minimal + Bean Validation cross-secao @AssertTrue isTotalItensValido) |
| Phase 04 P06 | ~15min | 2 tasks + checkpoint | 4 files (1 created: 04-06-SUMMARY.md consolidando Phase 4; 3 modified: ROADMAP.md Phase 4 [x] 6/6 + REQUIREMENTS.md 11 OUT Complete + STATE.md frontmatter+Decisions+Performance Metrics+Session Continuity; reator BUILD SUCCESS empirico 266 tests verdes 7 modulos; 4 grep gates passando; 5/5 ROADMAP SC + 11/11 OUT reqs com test correspondente; Phase 4 100% closeout pronta para gsd-verify-phase) |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table (D1-D10).
Recent decisions affecting current work:

- Roadmap: 6 fases derivadas dos 49 requirements, granularity standard, build order honra ARCHITECTURE.md
- P0 pitfalls C-01 (TOCTOU 24h) e C-02 (ContentCachingRequestWrapper) enderecos na Phase 1 e Phase 2 respectivamente
- CFG-01..04 agrupados na Phase 1 (configuracao fail-fast e pre-requisito de tudo)
- QA-07 (fixtures de payloads Meta) agrupado na Phase 6 com os demais testes
- [Phase ?]: WhatsAppProperties: 5 @NotBlank em PT-BR nomeando env var literal + toString mascarando 3 secrets [REDACTED] (CFG-01/CFG-03)
- [Phase ?]: Test split: WhatsAppPropertiesValidationTest (ApplicationContextRunner para 5 fail-fast + 1 toString) + WhatsAppPropertiesHappyPathTest (@SpringBootTest com WhatsAppApplication.class)
- [Phase ?]: Fail-fast tests usam hasStackTraceContaining (nao hasMessageContaining) — msg PT-BR esta na BindValidationException root cause
- [01-04]: BIGINT GENERATED ALWAYS AS IDENTITY (SQL ANSI) em vez de BIGSERIAL/AUTO_INCREMENT — empiricamente confirmado em H2 2.3.232 modo PostgreSQL via spike STEP 0 antes de comprometer 4 migrations
- [01-04]: Spike empirico STEP 0 (5 min) ANTES de migrations descobriu 5 detalhes (case sensitivity INFORMATION_SCHEMA, INDEX_COLUMNS path, A3 mitigada, UNIQUE/CHECK ambos disparam DataAccessException)
- [01-04]: 6 cenarios no FlywayMigrationTest (vs 3 do plan) — Rule 2 add coverage critica que o spike provou viavel: CHECK direcao + UNIQUE telefone + flyway_schema_history (auditoria empirica)
- [01-04]: H2 in-memory MODE=PostgreSQL com 5 params criticos — DATABASE_TO_UPPER=false + CASE_INSENSITIVE_IDENTIFIERS=true + DB_CLOSE_DELAY=-1 + INIT=CREATE SCHEMA — preserva case do schema 'whatsapp' lowercase; INFORMATION_SCHEMA queries usam UPPERCASE para system tables, lowercase para valores comparados
- [Phase ?]: PLAN-05: HmacValidator pure function + CachedBodyHttpServletRequest utility wrapper (split per CONTEXT D-01)
- [Phase ?]: PLAN-05: MessageDigest.isEqual constant-time + UTF-8 hardcoded enderecam PITFALLS C-02/C-03/C-04 por design (gates de grep enforce)
- [Phase ?]: PLAN-05: getCachedBody retorna .clone() defensivo (Rule 2) — beneficio de imutabilidade > custo de alocacao para webhook <10KB
- [Phase ?]: PLAN-06: Pacote web/ consistente com Wave 5 — HmacSignatureFilter co-localizado com CachedBodyHttpServletRequest
- [Phase ?]: PLAN-06: ApiKeyFilter ordem HIGHEST_PRECEDENCE+10 (vs ordem 1 do RESEARCH) — explicita relacao de ordem com HMAC
- [Phase ?]: PLAN-06: @ActiveProfiles('test') em WebMvcTest necessario porque WhatsAppApplication ativa @EnableConfigurationProperties
- [Phase ?]: PLAN-06: @RequestParam('hub.mode') com ponto literal funciona em Spring 3.5.9 (Assumption A4 RESEARCH resolvida)
- [01-07]: @AutoConfigureMockMvc em vez de MockMvcBuilders.webAppContextSetup manual — descoberta empirica via Rule 1 (bug fix). webAppContextSetup NAO registra automaticamente FilterRegistrationBean em Spring 3.5.x; @AutoConfigureMockMvc respeita os filters. Padrao default para integration tests Phase 2+.
- [01-07]: 10 cenarios em vez dos 7 do PLAN-07 — Rule 2 add coverage: sc4 toString masking smoke + d02 webhook publico de API key + /health bonus. Cada um cobre decisao arquitetural que poderia regredir silenciosamente sem teste explicito.
- [01-07]: Phase 1 100% completa — 7/7 plans + 5/5 ROADMAP success criteria + 9/9 REQUIREMENTS satisfeitos. Reator inteiro 127 tests verdes em 27s, zero regressao em 6 modulos.
- [02-01]: Spike OnConflictSpikeTest gate Wave 1 confirmou empiricamente que H2 v2.3.232 PG-mode NAO suporta `INSERT ... ON CONFLICT (col) DO NOTHING` (sintaxe Postgres-native). Plan 03 ira usar fallback save+catch DataIntegrityViolationException (RESEARCH §2.4) — UNIQUE constraint do banco e o gate atomico real. Spike fica como regression test permanente.
- [02-01]: 4 desvios Rule 3 (blocking issues) em config Hibernate/H2: (1) `hibernate.default_schema=whatsapp` REMOVIDO — causava lookup whatsapp.information_schema.sequences; (2) JDBC URL test mudou DATABASE_TO_UPPER=false→DATABASE_TO_LOWER=TRUE — H2 system schema acessivel via lookup lowercase do Hibernate; (3) MediaCache.arquivoHash com columnDefinition="CHAR(64)"; (4) MensagemLog.conteudo com columnDefinition="TEXT" (sem @Lob — Hibernate inferia OID/CLOB). Reator 106 tests verdes, zero regressao.
- [02-01]: TipoMensagem com 7 String constants (PLAN ditou) — STATUS, VIDEO ficaram fora; Plan 05 (parser) decide se precisa adicionar.
- [02-02]: TelefoneBR.normalizar pure utility (final class + private constructor) — 14 DDDs no Set DDDS_COM_NONO_DIGITO (SP 11-19, RJ 21/22/24, ES 27/28); demais strip 9o digito quando numero local tem 9 digitos comecando com 9. Algoritmo branch-ordered: null → sanitize → non-BR early return → DDD set lookup → strip condicional. 19 tests JUnit puros (sem Spring) executam em 0.114s. Pacote `util/` (mesmo de TipoMensagem). Plans 04/05 importam para single source of truth normalizacao.
- [02-02]: Politica deliberada — algoritmo NAO adiciona 9o digito quando vier sem em SP/RJ/ES (numero pode ser fixo); DDD inexistente (99) ainda passa pelo Set lookup (algoritmo baseado em Set, nao validacao real de DDD). Documentado em testes ddd_inexistente_99_strip_9 e em RESEARCH risks.
- [Phase ?]: Plan 02-03: IdempotencyService usa fallback save+catch DataIntegrityViolationException (UNIQUE wamid e o gate atomico portavel H2/PostgreSQL — decisao empirica do spike Wave 1)
- [Phase ?]: Plan 02-03: Test de concorrencia com ExecutorService(2) + CountDownLatch start gate validou empiricamente truthCount==1 e rows==1 (pattern replicavel para Plan 04 ClienteZapService race em telefone UNIQUE)
- [02-04]: ClienteZapService com `@Transactional(REQUIRES_NEW)` + native `UPDATE ... SET ultima_mensagem_em = NOW()` defende a trava 24h (Phase 4) contra TOCTOU race (PITFALLS C-01) — commit imediato visivel via 2a conexao do pool, NOW() do banco elimina clock skew JVM-DB. Test 6 valida via JdbcTemplate.queryForObject Timestamp.
- [02-04]: Race protection em `identificar(telefone)` reusa exatamente o pattern do `IdempotencyService` (UNIQUE constraint como gate atomico portavel H2/PostgreSQL): try `repository.save` / catch `DataIntegrityViolationException` / re-fetch via `findByTelefone`. Validado por test 5 (2 threads + CountDownLatch → COUNT=1).
- [02-04]: Cross-bean call obrigatorio para REQUIRES_NEW ativar proxy AOP — documentado em Javadoc do service. MensagemService (Plan 06) sera o caller cross-bean; self-call dentro do proprio service viraria no-op de propagation.
- [02-04]: 2 desvios Rule 1 em test data: input do test 3 precisava prefixo "55" (sem ele, normalizar trata como nao-BR e early return) e input do test 5 do RESEARCH original (`+5599888777666`) tinha local comecando com 8, nao com 9 — corrigido para `+5599988777666` para ativar strip-9. Codigo do service permanece 100% per RESEARCH §7.1.
- [02-04]: Hibernate `AssertionFailure: Entry for instance ... has a null identifier` no log da thread perdedora do race do test 5 e benigno (esperado, nao falha o test) — entity manager fica inconsistente apos DataIntegrityViolation, mas re-fetch funciona porque devolve snapshot lido. Documentado em SUMMARY como know-issue. Considerar isolar criarNovo em REQUIRES_NEW se virar problema operacional.
- [02-06]: MensagemService SEM `@Transactional` na classe — orquestrador stateless; cada chamada downstream (idempotency.tentarPersistir REQUIRED, clienteZap.identificar REQUIRED, clienteZap.atualizarUltimaMensagemEm REQUIRES_NEW) gerencia propria transacao. Cross-bean call ativa proxy AOP (test webhook_text_persiste empiricamente popula ultima_mensagem_em via REQUIRES_NEW commit imediato).
- [02-06]: Ack-first defensivo no WebhookController.POST — try/catch IOException + RuntimeException -> log.error + return 200 mesmo em erro; PITFALLS C-05 (Meta retry storm em payload quebrado) prioriza estabilidade sobre alarme. Trade-off: mascara bugs em prod. Mitigacao: log.error com stack trace + Phase 6 pode adicionar metric counter `whatsapp_webhook_errors_total`.
- [02-06]: WebhookControllerTest precisou @MockBean MensagemService — `@WebMvcTest` so carrega controller; Phase 2 ganhou nova dependencia. WebhookControllerIntegrationTest (Phase 1) usa @SpringBootTest e nao precisou mudanca (Spring carrega bean real). Pattern reusable para futuras adicoes de service ao controller.
- [02-06]: H2 NOW() retorna LOCAL como UTC (timezone-naive) — diff de 3h com Instant.now() real UTC quebrou primeira versao do test webhook_text_persiste com `.isAfter(antes)`. Fix Rule 1: validar apenas `.isNotNull()` aqui; validacao temporal precisa via `JdbcTemplate.queryForObject(Timestamp.class)` ja existe em `ClienteZapServiceTest.atualizar_em_nova_transacao_commit_imediato` (Plan 02-04). Em PostgreSQL real prod com TIMESTAMP WITH TIME ZONE, comparativo direto funcionaria.
- [02-07]: WebhookPersistenciaIntegrationTest com 13 tests E2E `@SpringBootTest(MOCK)` + MockMvc + JdbcTemplate fecha Phase 2 empiricamente — todos os 5 ROADMAP SC verdes via tests separados (sc1, sc2a-sc2f, sc3a, sc3b, sc4, sc5) + 2 bonus (multiple + JSON malformado retorna 200). Helper `computeSignature` viva assina fixtures dinamicamente com appSecret do test profile (sem hex hardcoded); helper `postFixture` DRY (carrega + assina + POST + assert 200).
- [02-07]: Comparacao temporal SC-5 via epoch seconds (long, nao Instant) — H2 NOW() timezone-naive quirk reproducido em Plan 02-06 SUMMARY. Solucao: extrair epoch seconds via `tsRaw.getTime() / 1000L` e comparar com `System.currentTimeMillis() / 1000L` — local-vs-local, neutraliza offset BRT/UTC. Em PostgreSQL real prod com TIMESTAMP WITH TIME ZONE comparativo direto Instant funcionaria.
- [02-07]: Filter por wamid+telefone em assertions de COUNT (vs deleteAll @BeforeEach) — H2 in-memory compartilhado entre tests do mesmo SpringContext; wamid UNIQUE garante isolamento da assertion. Bug Rule 1 descoberto na primeira run: sc3a `COUNT WHERE telefone = "554784178525"` retornava 3 (sc1+sc2a+bonus_multiple usam mesmo telefone). Fix: `WHERE wamid = ? AND telefone = ?`.
- [02-07]: Phase 2 100% completa — 7/7 plans + 5/5 ROADMAP SC + 9/9 reqs (WEB-05/06/07 + PER-02/03/04/05/06/07). Reator inteiro 7 modulos BUILD SUCCESS, ~183 tests verdes em ~30s, zero regressao em Phase 1. Pronto para gsd-verify-phase.
- [03-01]: spring-boot-starter-aop adicionado como dep compile EXPLICITA (nao via transitive) — sem ele, anotacoes Resilience4j @CircuitBreaker/@Retry viram no-op silencioso (Risk A6 RESEARCH §Pitfall 2). Validacao via dependency:tree confirma aspectjweaver:1.9.25.1 no classpath. api-whatsapp tinha web/jpa/validation/openapi mas NENHUM destes inclui AOP transitively.
- [03-01]: ThreadPoolTaskExecutor (corePool=2/maxPool=10/queue=100/CallerRunsPolicy) per D-02 do CONTEXT — pool dedicado degrada graciosamente em pico via CallerRunsPolicy (listener roda inline na thread chamadora) vs SimpleAsyncTaskExecutor (thread por task → OOM em pico). AbortPolicy rejeitado: descarta mensagem ja persistida → side effect perdido.
- [03-01]: Resilience4j retry-exceptions whitelist explicita 3 transient (HttpServerErrorException + SocketTimeoutException + IOException) — Resilience4j default NAO retenta excecoes nao listadas, portanto HttpClientErrorException (4xx categoricos) automaticamente NAO retenta sem precisar configurar ignoreExceptions (D-08, ROU-03). 4xx duplicaria side effect (PITFALLS C-05).
- [03-01]: spring.http.client integrado dentro do bloco 'spring:' aninhado existente em application.yml (apos flyway:) — em vez de dotted-keys ou multi-document YAML (---). Coexiste com spring.datasource/jpa/flyway sem conflito de chave duplicada. Mesma sintaxe em application-test.yml.
- [03-01]: WhatsAppProperties.metaApiBaseUrl SEM @NotBlank — diferente dos 5 secrets CFG-01..04 que precisam fail-fast pois nao tem default seguro, metaApiBaseUrl tem default valido (https://graph.facebook.com/v22.0) que funciona em prod. Override por env var WHATSAPP_META_API_BASE_URL ou via @DynamicPropertySource em test (Wave 3 WireMock).
- [03-01]: WireMock 3.10.0 (nao 4.x) — Jetty 12 standalone shadow evita conflito com Boot 3.5.9 (RESEARCH + flag de risco no STATE). Validacao empirica formal acontece em Wave 3 quando WireMockExtension for usado em integration test.
- [03-01]: Aspect order Resilience4j Spring Boot starter — Retry POR FORA de CircuitBreaker. 1 dispatch falho = 3 calls counted no CB sliding-window (3 retries). 4 dispatches falhos consecutivos = 12 calls counted = circuit aberto. Importante para Wave 4 (ErpCallbackClient @CircuitBreaker(name="erp-callback") @Retry(name="erp-callback")).
- [03-02]: ComandoExtractor switch case `TipoMensagem.VIDEO` REMOVIDO — TipoMensagem Phase 2 tem apenas 7 constants (TEXT, INTERACTIVE_BUTTON, INTERACTIVE_LIST, DOCUMENT, IMAGE, AUDIO, DESCONHECIDO), sem VIDEO. Payload literal "video" cai no default branch -> null (skip dispatch). Documentado em Javadoc + test defensivo `video_nao_existe_constant`.
- [03-02]: MediaMetadataDTO Jackson POJO regular (NAO record) — alinhamento com convencao do monorepo (api-email/api-storage/Phase 2 envelope DTOs); `@JsonProperty` em mime_type/file_size/messaging_product mapeia snake_case para camelCase; `@JsonIgnoreProperties(ignoreUnknown=true)` deixa resiliente a campos novos do Meta.
- [03-02]: ComandoCallbackDTO + MensagemPersistidaEvent + MetaMediaResultado como records — uso interno (sem deserializacao Jackson externa) ou serializacao OUTPUT-only (Jackson 2.18 + Boot 3 suportam record nativamente como wire OUTPUT, diferente do INPUT externo onde POJO e preferido).
- [03-02]: ComandoExtractor logica pura sem I/O — testavel sem Spring context (instanciacao direta `new ComandoExtractor()`). 13 tests JUnit puros executam em 0.090s. Pattern reusable para Wave 3+ services que sejam sufficientemente puros.
- [03-02]: ComandoExtractor branch `idDeInteractive` exige `sep > 0` (nao `>= 0`) — id vazio antes do '|' (ex: "|Aprovar") retorna null. Test `interactive_pipe_no_inicio` valida edge case. Parser Phase 2 sempre coloca id valido, mas defensivo aqui evita callback com comando vazio ao ERP.
- [03-03]: MetaMediaClient SEM Resilience4j (decisao consciente per CONTEXT D-04) — primeira acao apos ack precisa ser RAPIDA (URL Meta TTL 5min PITFALLS C-08); circuit breaker + retry adicionariam latencia que poderia quebrar a janela em pico. Falha de download = mensagem persistida sem bytes (callback ERP recebe payload sem mediaBase64); nao critico. ErpCallbackClient (Wave 4) sim tera Resilience4j (D-03) — semantica diferente.
- [03-03]: Bearer header per-request explicito vs interceptor global no RestClient.builder() — cada `.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)` em cada call. Mais verboso mas (a) mais facil de auditar visualmente que C-14 esta cumprido, (b) facilita override per-request, (c) zero risco de interceptor mal configurado escrever token em query param. Test `bearer_nunca_em_query_param` enforce regression via getAllServeEvents.forEach.
- [03-03]: 6 tests vs 5 minimos do PLAN (Rule 2 defesa em profundidade) — adicionado `metadata_sem_url_retorna_empty` cobrindo branch `metadata.getUrl() == null` (linha 78). Branch existia desde a primeira versao mas sem test — risco silencioso de regressao se alguem reescrevesse a validacao. Custo: +1 stub WireMock; beneficio: regression test contra Meta mudar contrato.
- [03-03]: WireMock 3.10.0 standalone + Boot 3.5.9 validado empiricamente — bloqueador documentado em 03-01-SUMMARY (Concerns Wave 3) RESOLVIDO. 6 tests passam em ~2.6s (run integrado) e ~7s (run isolado cold). Pattern @BeforeAll dynamicPort + @AfterAll stop + @DynamicPropertySource registry.add + @BeforeEach resetAll reusable identicamente em Wave 4 (ErpCallbackClient) e Wave 6 (E2E com Meta + ERP simultaneamente).
- [03-03]: `@SpringBootTest(classes = WhatsAppApplication.class)` em vez de `@SpringBootTest` sem qualifier — WhatsAppApplication tem `scanBasePackages` + `@EnableConfigurationProperties`. Sem qualifier, Spring busca via heuristica e potentialmente aceita config diferente da producao. Pattern alinhado com WhatsAppPropertiesHappyPathTest (Phase 1) e WebhookPersistenciaIntegrationTest (Phase 2).
- [03-03]: `verify(0, getRequestedFor)` para short-circuit assertions — count == 0 em URL nao chamada confirma controle de fluxo curto-circuitou. Em `quatrocentos_quatro_step_1`, garante que step 2 nao foi tentado. Mais explicito que checar log mensagens. Counter == 1 em 5xx test valida indiretamente A6 (sem retry).
- [03-03]: `getAllServeEvents().forEach` para regression test C-14 — percorre TODAS requests recebidas pelo WireMock + assertion `.doesNotContain("access_token=")` em cada URL. Mais robusto que `verify(0, getRequestedFor(urlMatching(".*[?&]access_token=.*")))` que dependeria de matcher regex correto. forEach valida em LISTA, captura qualquer query param novo que vazasse.
- [03-04]: **Risk A6 (Resilience4j AOP no-op silencioso) RESOLVED empiricamente** — test `cinquecentos_recupera_counter_3` (WireMock scenario state 500->500->200) confirmou counter == 3, provando que `@Retry` esta sendo interceptado por `RetryAspect` do Spring AOP. spring-boot-starter-aop:3.5.9 + aspectjweaver:1.9.25.1 + resilience4j-spring-boot3:2.2.0 funcionam juntos em api-whatsapp.
- [03-04]: **fallbackMethod localizado em @Retry (outer aspect), NAO em @CircuitBreaker (inner)** — gotcha critico do Resilience4j Spring AOP. Quando ambas annotations coexistem na mesma method e fallback fica no INNER (CircuitBreaker, order LOWEST_PRECEDENCE-2), o fallback inner converte excecao em retorno void de sucesso ANTES da OUTER (Retry, order LOWEST_PRECEDENCE-3) ver o erro — Retry recebe "sucesso" e nao retenta. Solucao: por fallbackMethod no @Retry. CircuitBreaker inner continua contando attempts no sliding-window. Bug descoberto empiricamente: counter==1 em vez de 3 na primeira execucao. Rule 1 fix documentado em ErpCallbackClient.java Javadoc para futuros leitores.
- [03-04]: **ResourceAccessException explicita em retry-exceptions (prod + test)** — Spring `RestClient.retrieve().toBodilessEntity()` empacota `SocketTimeoutException` em `ResourceAccessException("Could not retrieve response status code: ...")`. Sem este entry, timeouts NAO retentariam mesmo com SocketTimeoutException listado (Resilience4j compara via `instanceof`). Adicionado em ambos application.yml para paridade prod-correto. Empiricamente descoberto via test `timeout_retry_e_fallback`.
- [03-04]: callbackTimeout 500ms no test profile (vs 5s default prod) — necessario para `timeout_retry_e_fallback` em <5s. WhatsAppPropertiesHappyPathTest assertion ajustada para refletir test profile (5s -> 500ms). Default-prod imutavel via field initializer `Duration.ofSeconds(5)` em WhatsAppProperties.java.
- [03-04]: @BeforeEach `cbRegistry.find("erp-callback").ifPresent(CircuitBreaker::reset)` mitiga Risk A3 (CB shared state Singleton) — antes de cada test, CB volta a CLOSED + sliding-window zerado. Necessario porque circuit_open_apos_falhas_repetidas deixa CB em OPEN e proximos tests precisam comecar em CLOSED.
- [03-05]: **Risk A1 (HIGH) RESOLVED por design:** @Transactional em MensagemService.processarWebhook e PRE-REQUISITO arquitetural para @TransactionalEventListener(AFTER_COMMIT) funcionar. Spring docs explicito: "If no transaction is running, the listener is not invoked at all". Sem @Transactional, mensagem persiste em mensagens_log (transacao implicita do repository.save), eventPublisher.publishEvent acontece FORA de transacao ativa, listener NUNCA dispara — bug silencioso, sem stack trace. Validacao FINAL via Wave 6 smoke test (WebhookPersistenciaIntegrationTest reativado com WireMock stub para ERP, verify(atLeastOnce()).postRequestedFor garante callback recebido).
- [03-05]: MensagemAsyncListener orquestrador async com 5 steps + try/catch isolado (D-01 sequencia exata): media baixada PRIMEIRO (URL Meta TTL 5min PITFALLS C-08, log.warn em falha + prossegue) -> identificar cliente (log.error + return early) -> atualizar ultima_mensagem_em REQUIRES_NEW (log.error + return early) -> extrair comando (null = log.debug + return) -> dispatch ERP (catch defensivo log.error sem rethrow). Listener NUNCA propaga (async, ninguem para receber). Cross-bean call MensagemAsyncListener -> ClienteZapService.atualizarUltimaMensagemEm garante REQUIRES_NEW funcional via proxy AOP (A3 RESEARCH).
- [03-05]: MensagemService fast-path = parse + idempotency + publishEvent (Mockito puro suficiente). Phase 2 usava @SpringBootTest+H2 porque tinha cross-bean call REQUIRES_NEW; em Phase 3 essa logica foi para o listener, service virou stateless. Run de 4 tests caiu de ~5s para 1s. Cobertura E2E DB fica em WebhookPersistenciaIntegrationTest (Wave 6 reativa).
- [03-05]: WebhookPersistenciaIntegrationTest @Disabled em toda a classe (13 tests skipped) — tests sc4/sc5 dependiam do flow sincrono Phase 2 onde MensagemService chamava ClienteZapService.identificar/atualizarUltimaMensagemEm. Em Phase 3 essas chamadas foram movidas para o listener @Async; thread do MockMvc retorna antes do listener completar. Wave 6 substitui whatsappTaskExecutor por SyncTaskExecutor via @TestConfiguration + adiciona WireMock stub para ERP — flow inteiro sincrono novamente em test. Alternativa preferida sobre exclude individual via -Dtest=...: build verde com comando unico mvnw verify, Wave 6 remove anotacao em UMA linha.
- [03-05]: 8 tests no MensagemAsyncListenerTest (5 minimos do plan + 3 bonus Rule 2 add coverage): media_baixar_lanca_continua_sem_base64 (branch onde metaMediaClient.baixar lanca RuntimeException vs Optional.empty — listener log.warn + prossegue, NAO return early), atualizar_lanca_return_early (branch onde atualizar lanca apos identificar OK — return sem chamar comando/callback), despachar_lanca_NAO_propaga (catch generico defensivo step 5 — Resilience4j fallback ja deveria ter engolido mas codigo tem catch defesa em profundidade). Todos os 5 try/catch do listener tem regressao test.
- [03-05]: @Component em vez de @Service no listener — convencao do CONTEXT/RESEARCH para listeners (vs services). Funcionalmente identicos em Spring (ambos sao stereotype @Component), mas semantica explicita: listener e event consumer, nao caso de uso de negocio. Pattern para Phase 4+ caso outros listeners apareçam.
- [04-01]: ErrorResponse @JsonInclude(NON_NULL) garante backward compat empirica com api-email/api-storage/api-consultas — campos null `codigo` + `metaErrorCode` sao OMITIDOS do JSON, modulos Phase 1-3 que NUNCA setam continuam com wire identico. Reator inteiro verde apos modificacao confirma.
- [04-01]: CodigoCarrier interface vive em lib-shared (NAO em api-whatsapp) — preserva direcao de dependencia: GlobalExceptionHandler usa `instanceof CodigoCarrier` sem importar pacotes de api-*. Excecoes Phase 1-3 nao implementam, branch ignorado silenciosamente.
- [04-01]: Resilience4j whatsapp-cloud instance espelha erp-callback EXATAMENTE — Wave 4 Phase 3 ja provou empiricamente que essa config funciona; semantica Phase 4 outbound e identica (3 retries com backoff exp, 50% threshold, 60s wait open). ResourceAccessException CRUCIAL em retry-exceptions (gotcha 03-04 reaproveitado: Spring RestClient empacota SocketTimeoutException nele).
- [04-02]: @Order(Ordered.HIGHEST_PRECEDENCE) validado empiricamente via counter==1 em 3 retries (Pitfall 1 RESEARCH RESOLVED) — JanelaEnforcementAspect roda outermost no chain Spring AOP; sem isso, em 3 retries verificarJanela seria chamado 3x (waste + race em boundary 24h durante backoff 1s/2s/4s).
- [04-02]: Annotation marker @JanelaProtegida sem atributos (vs pointcut por nome) — forca declaracao explicita por metodo: qualquer novo `enviar*` em Phase 5+ tem que decidir conscientemente entrar/burlar enforcement. Convencao posicional args[0] = String telefone documentada em Javadoc + IllegalStateException fail-fast em runtime.
- [04-02]: Native @Query SELECT (vs derived findByTelefone) em buscarUltimaMensagemEm — pula JPA L1 cache (PITFALLS C-01); webhook Phase 2 PER-07 grava REQUIRES_NEW + NOW() do banco; trava 24h le DEPOIS, potencialmente de outra transacao. WindowEnforcementService SEM @Transactional (leitura pura, native query contorna cache).
- [04-03]: TTL estrito 30d sem sliding (D-04 reafirmado) — hit NAO estende `expira_em`. Meta documenta media_id valido por ate 30 dias; sliding TTL mascararia expiracao real do Meta levando a 4xx surpresa. Turnover natural via reupload, tabela bounded.
- [04-03]: sha256 hex via HexFormat.of().formatHex (JDK 17+ built-in) — substitui patterns legacy (BigInteger.toString(16) com leading zero bug, Apache Commons Codec dependency externa). Pattern reusable.
- [04-04]: Reflection test metodos_publicos_nao_inclui_template + grep gate dual mitigation OUT-05 — gate impossivel de regredir sem refactor consciente do test ou source. WhatsAppCloudClient EXATAMENTE 4 metodos publicos (texto/documento/botoes/lista) + 1 helper privado (uploadMedia sem @JanelaProtegida pois chamado de dentro de enviarDocumento ja protegido).
- [04-04]: Multipart spike Wave 0 (04-01) provou boundary auto + 3 fields ANTES de 04-04 implementar — ByteArrayResource override getFilename() + MultiValueMap pattern empiricamente validado contra WireMock antes do bean WhatsAppCloudClient existir. Risco PITFALLS C-15 quase-zero apos spike.
- [04-04]: fallbackMethod no @Retry (NAO no @CircuitBreaker) — gotcha 03-04 reaproveitado. Counter==3 em 5xx test valida empiricamente: se fallback estivesse no @CircuitBreaker (inner, order LOWEST_PRECEDENCE-2), CB inner converteria excecao em retorno void de sucesso ANTES da OUTER (Retry, order LOWEST_PRECEDENCE-3) ver o erro. Bug silencioso evitado.
- [04-04]: Fallback throws (NAO suprime como Phase 3 ErpCallbackClient) — outbound do controller (04-05) PRECISA propagar erro ao ERP. Divergencia consciente: ErpCallbackClient e fire-and-forget (ack-first defensivo); WhatsAppCloudClient e chamado pelo controller que retorna ao ERP.
- [04-04]: Bearer per-request explicito em CADA RestClient call (postMessages helper + uploadMedia) — NUNCA defaultHeader global. Defesa em profundidade C-09 + C-14 alinhada com Phase 3 D-04 (auditavel visualmente, facilita override per-request, zero risco de interceptor mal configurado escrever token em query param). Gate empirico getAllServeEvents.forEach + gate estatico grep.
- [04-05]: @AssertTrue isTotalItensValido cross-secao em record + @JsonIgnore impede field aparecer em JSON — validacao single-section @Size(max=10) NAO captura {2 secoes x 6 itens = 12}; @AssertTrue executando sum stream e a unica forma de bloquear early antes da Cloud API rejeitar. @JsonIgnore deixa apenas os 3 components reais no wire.
- [04-05]: D-01 base64 String (NAO byte[]) em EnviarDocumentoRequest — JSON regular entre ERP e api-whatsapp (multipart MultipartFile complica integracao do ERP). Controller decodifica via Base64.getDecoder().decode + try/catch IllegalArgumentException -> 400. @Size(max=18MB) protege contra DoS via payload absurdo; ~13MB binario apos decode (1.33 inflation) cobre PDFs tipicos de orcamento.
- [04-05]: StatusResponse minimal record(status, circuitBreakerState, phoneNumberId) per D-04 — subscribed_apps validation via Graph API ficou para Phase 6 (PITFALLS C-12 — exige token Meta + chamada externa que pode degradar /status). v1 cobre o que operador precisa.
- [04-06]: Reator inteiro mvnw verify (vs apenas api-whatsapp -pl) — garante zero regressao em api-email/api-storage/api-consultas que consomem ErrorResponse via lib-shared (modificado em 04-01). 266 tests verdes em 41s confirma backward compat empirica do @JsonInclude(NON_NULL).
- [04-06]: Phase 4 100% completa — 6/6 plans + 5/5 ROADMAP SC + 11/11 OUT reqs (OUT-01..11) com test correspondente. Custo zero arquitetural (nao por disciplina) garantido por gate dual em OUT-05 (reflection + grep) + hard 409 antes de Cloud API em todos os 4 metodos publicos via @JanelaProtegida + HIGHEST_PRECEDENCE aspect. Pronto para gsd-verify-phase.

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 4 multipart field names (`messaging_product`, `type`, `file`): **RESOLVED via spike Wave 0 04-01 + regression test 04-04** (`upload_media_envia_3_fields_obrigatorios` em WhatsAppCloudClientTest com `withRequestBodyPart aMultipart`). Mudanca futura do Meta seria detectada empiricamente pelo test no proximo run.
- WireMock 3.10.0 (Jetty 12 standalone shadow) + Boot 3.5.9: **VALIDADO empiricamente em Phase 3 03-03** — pattern reusable confirmado em 04-04 (WhatsAppCloudClientTest 13 tests verdes em 2.1s).
- Phase 5 (lib-whatsapp-client): contratos REST do controller (5 DTOs request + StatusResponse + EnvioResponse) sao records imutaveis estaveis — Phase 5 NAO deve quebrar contrato ao espelhar via lib-whatsapp-client.dto.* records.
- Phase 6 dependency: SpringDoc OpenAPI (QA-05) + subscribed_apps validation no /status (PITFALLS C-12) + Spring servlet multipart >10MB override (RUNBOOK) — todos os 3 esperados em Phase 6, nao bloqueiam Phase 4 closeout.

## Session Continuity

Last session: 2026-05-06T05:45:00.000Z
Stopped at: Phase 04 closeout — 6/6 plans + 5/5 ROADMAP SC + 11/11 OUT reqs verdes; reator BUILD SUCCESS 266 tests; pronto para gsd-verify-phase 4-outbound-trava-24h-whatsappcontroller
Resume file: .planning/phases/04-outbound-trava-24h-whatsappcontroller/04-06-SUMMARY.md
