---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Completed 02-02-PLAN.md (Wave B — TelefoneBR.normalizar pure utility + 19 JUnit tests, 74 tests api-whatsapp aggregate verde). Wave B em curso paralelo (Plans 03 IdempotencyService + 05 WebhookPayloadParser).
last_updated: "2026-05-05T15:35:17.751Z"
last_activity: 2026-05-05
progress:
  total_phases: 6
  completed_phases: 1
  total_plans: 14
  completed_plans: 11
  percent: 79
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-05)

**Core value:** Modulos reaproveitaveis entre ERPs ERPKit, sem custo operacional recorrente com terceiros — Modulo WhatsApp: custo zero de Meta garantido por design, nao por disciplina
**Current focus:** Phase 02 — persistencia-idempotencia

## Current Position

Phase: 02 (persistencia-idempotencia) — EXECUTING
Plan: 5 of 7
Status: Ready to execute
Last activity: 2026-05-05

Progress: [██████░░░░] 60% (2/7 plans of Phase 02; Phase 1 awaiting verifier sign-off)

## Performance Metrics

**Velocity:**

- Total plans completed: 9
- Average duration: ~10 min
- Total execution time: ~89 min

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 7/7 | ~60 min | ~8 min |
| 02 | 2/7 | ~29 min | ~14 min (Wave 1 spike 26m + Wave 2 TelefoneBR 3m) |

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

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 4 (outbound + media): confirmar field names do multipart Meta `/media` upload endpoint (`messaging_product`, `type`, `file`) no momento da implementacao — Meta pode atualizar sem aviso (flag de research ARCHITECTURE.md)
- WireMock 3.8.1 confirmado seguro; 4.2.1 tem potencial conflito Jetty com Boot 3.5.9 — usar 3.8.1 ate verificacao empirica

## Session Continuity

Last session: 2026-05-05T15:35:07.856Z
Stopped at: Completed 02-02-PLAN.md (Wave B — TelefoneBR.normalizar pure utility + 19 JUnit tests, 74 tests api-whatsapp aggregate verde). Wave B em curso paralelo (Plans 03 IdempotencyService + 05 WebhookPayloadParser).
Resume file: None
