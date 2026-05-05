---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Completed 02-07-PLAN.md (Wave E final — WebhookPersistenciaIntegrationTest com 13 tests E2E SpringBootTest+MockMvc+JdbcTemplate cobrindo 5 SC ROADMAP + 2 bonus, 112 tests api-whatsapp aggregate verde, reator 7 modulos BUILD SUCCESS, ROADMAP Phase 2 [x] Complete + Plans TBD->7 com index, commit ab60b1b). **Phase 2 100% completa — 7/7 plans + 5/5 SC + 9/9 reqs satisfeitos.** Pronto para gsd-verify-phase de Phase 2 OU inicio de Phase 3 (ROU-01..05 async boundary; planning/research necessario antes de execute-phase).
last_updated: "2026-05-05T19:18:54.757Z"
last_activity: 2026-05-05 -- Phase 03 execution started
progress:
  total_phases: 6
  completed_phases: 2
  total_plans: 20
  completed_plans: 14
  percent: 70
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-05)

**Core value:** Modulos reaproveitaveis entre ERPs ERPKit, sem custo operacional recorrente com terceiros — Modulo WhatsApp: custo zero de Meta garantido por design, nao por disciplina
**Current focus:** Phase 03 — roteamento-boundary-async

## Current Position

Phase: 03 (roteamento-boundary-async) — EXECUTING
Plan: 1 of 6
Status: Executing Phase 03
Last activity: 2026-05-05 -- Phase 03 execution started

Progress: [██████████] 100% (7/7 plans of Phase 02 + 7/7 Phase 01; Phases 1+2 awaiting verifier sign-off)

## Performance Metrics

**Velocity:**

- Total plans completed: 14
- Average duration: ~9 min
- Total execution time: ~130 min

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 7/7 | ~60 min | ~8 min |
| 02 | 7/7 | ~70 min | ~10 min (Wave 1 spike 26m + Wave 2 TelefoneBR 3m + Wave B parallel + Wave C ClienteZap 24m + Wave D MensagemService 6m30s + Wave E integration tests 9m) |

**Recent Trend:**

- Last 5 plans: 01-03 (9 min), 01-04 (12 min), 01-05 (10 min), 01-06 (7 min), 01-07 (8 min)
- Trend: scope crescente nos primeiros (Wave 4 spike + migrations), depois estavel ~7-10min por wave

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

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 4 (outbound + media): confirmar field names do multipart Meta `/media` upload endpoint (`messaging_product`, `type`, `file`) no momento da implementacao — Meta pode atualizar sem aviso (flag de research ARCHITECTURE.md)
- WireMock 3.8.1 confirmado seguro; 4.2.1 tem potencial conflito Jetty com Boot 3.5.9 — usar 3.8.1 ate verificacao empirica

## Session Continuity

Last session: 2026-05-05T18:34:07.001Z
Stopped at: Completed 02-07-PLAN.md (Wave E final — WebhookPersistenciaIntegrationTest com 13 tests E2E SpringBootTest+MockMvc+JdbcTemplate cobrindo 5 SC ROADMAP + 2 bonus, 112 tests api-whatsapp aggregate verde, reator 7 modulos BUILD SUCCESS, ROADMAP Phase 2 [x] Complete + Plans TBD->7 com index, commit ab60b1b). **Phase 2 100% completa — 7/7 plans + 5/5 SC + 9/9 reqs satisfeitos.** Pronto para gsd-verify-phase de Phase 2 OU inicio de Phase 3 (ROU-01..05 async boundary; planning/research necessario antes de execute-phase).
Resume file: None
