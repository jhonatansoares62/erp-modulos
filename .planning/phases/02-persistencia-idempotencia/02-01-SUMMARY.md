---
phase: 02-persistencia-idempotencia
plan: 01
subsystem: api-whatsapp
tags:
  - api-whatsapp
  - jpa
  - entities
  - repositories
  - spike
  - idempotency
  - h2
  - hibernate-validate
  - schema-mapping

dependency_graph:
  requires:
    - "Phase 1 V1-V4 migrations deployadas em schema whatsapp"
    - "WhatsAppApplication boot funcional com Flyway + Hibernate auto-config"
    - "lib-shared ApiKeyFilter (String, Set) construtor (Phase 1 PLAN 06)"
  provides:
    - "@Entity ClienteZap mapeando whatsapp.clientes_zap"
    - "@Entity MensagemLog mapeando whatsapp.mensagens_log com @Enumerated Direcao + columnDefinition TEXT"
    - "@Entity MediaCache mapeando whatsapp.media_cache (PK String CHAR(64) arquivoHash)"
    - "Enum Direcao { in, out } em lowercase"
    - "TipoMensagem String constants (7) — TEXT, INTERACTIVE_BUTTON, INTERACTIVE_LIST, DOCUMENT, IMAGE, AUDIO, DESCONHECIDO"
    - "ClienteZapRepository, MensagemLogRepository, MediaCacheRepository (esqueletos + helpers)"
    - "Decisao tecnica: ON CONFLICT NAO suportado em H2 v2.3.232 → Plan 03 usa fallback save() + catch DataIntegrityViolationException"
  affects:
    - "Plan 02 (TelefoneBR utility) — pode rodar paralelo agora (Wave 2)"
    - "Plan 03 (IdempotencyService) — usa MensagemLogRepository + sabe que precisa fallback save+catch"
    - "Plan 04 (ClienteZapService) — usa ClienteZapRepository + entity ClienteZap"
    - "Plan 05 (WebhookPayloadParser) — usa Direcao + TipoMensagem constants"
    - "Plan 06 (MensagemService orquestrador) — usa todas as 3 entities + repos"
    - "Phase 4 (MediaCacheService) — usa MediaCache entity + repository"

tech_stack:
  added:
    - "Hibernate property hibernate.boot.allow_jdbc_metadata_access (config nao-default em test)"
    - "H2 JDBC param DATABASE_TO_LOWER=TRUE (substituiu DATABASE_TO_UPPER=false)"
  patterns:
    - "@Entity com @Table(schema = \"whatsapp\", name = \"...\")"
    - "@Enumerated(EnumType.STRING) com enum em lowercase (CHECK constraint matching)"
    - "@Column(insertable=false, updatable=false) para campos com DEFAULT NOW() do banco"
    - "columnDefinition explicito (CHAR/TEXT) quando JPA default nao bate com schema PG"
    - "Spike test que documenta comportamento esperado em vez de verificar caminho feliz"

key_files:
  created:
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/ClienteZap.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/MensagemLog.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/MediaCache.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/Direcao.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/util/TipoMensagem.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/repository/ClienteZapRepository.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/repository/MensagemLogRepository.java"
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/repository/MediaCacheRepository.java"
    - "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/spike/OnConflictSpikeTest.java"
  modified:
    - "api-whatsapp/src/main/resources/application.yml"
    - "api-whatsapp/src/test/resources/application-test.yml"

decisions:
  - "ON CONFLICT NAO suportado em H2 v2.3.232 PG-mode — Plan 03 usa fallback save() + catch DataIntegrityViolationException (RESEARCH §2.4)"
  - "hibernate.default_schema=whatsapp REMOVIDO (V1 pre-existia config) — causava lookup whatsapp.information_schema.sequences nao-existente quando ddl-auto: validate roda contra entities"
  - "JDBC URL test mudou DATABASE_TO_UPPER=false → DATABASE_TO_LOWER=TRUE — H2 system schema INFORMATION_SCHEMA acessivel via lookup lowercase do Hibernate SequenceInformationExtractor"
  - "MediaCache.arquivoHash com columnDefinition=\"CHAR(64)\" — sem isso JPA mapeia String para VARCHAR, schema validation falha (V3 declara CHAR(64))"
  - "MensagemLog.conteudo com columnDefinition=\"TEXT\" SEM @Lob — @Lob faria Hibernate esperar OID/CLOB; H2 PG-mode + PostgreSQL armazenam TEXT como CHARACTER VARYING"

metrics:
  duration_seconds: 1600
  duration_human: "26m"
  tasks_completed: 9
  files_created: 9
  files_modified: 2
  tests_added: 2  # OnConflictSpikeTest
  total_reactor_tests: 106
  api_whatsapp_tests: 55  # 53 baseline + 2 spike
  build_time_full_reactor: "~30s"
  completed_date: "2026-05-05"
---

# Phase 2 Plan 01: STEP 0 Spike + 3 Entities + 3 Repos Esqueleto Summary

JPA contract layer da Phase 2 fechado: spike empirico mostrou que H2 v2.3.232 PG-mode NAO aceita `ON CONFLICT (col) DO NOTHING` (Plan 03 ira usar fallback save+catch); 3 @Entity batem com schema deployado V1-V4 apos 2 ajustes de columnDefinition; 3 repositories esqueleto (Plans 03/04 adicionam queries customizadas) compilam e carregam no contexto Spring; reator inteiro 106 tests verdes em ~30s — zero regressao em 6 modulos.

## ALERTA — Spike Falhou (Esperado em Confidence MEDIUM-HIGH; Fallback Acionado)

**Resultado empirico do gate Wave 1:**

H2 v2.3.232 (Spring Boot 3.5.9 BOM) com `MODE=PostgreSQL` **NAO ACEITA** a sintaxe Postgres-native `INSERT ... ON CONFLICT (col) DO NOTHING`. Erro literal observado:

```
JdbcSQLSyntaxErrorException [42000-232]: Syntax error in SQL statement
"INSERT INTO whatsapp.mensagens_log (wamid, telefone, direcao)
 VALUES (?, ?, ?) [*]ON CONFLICT (wamid) DO NOTHING"
```

O parser H2 indica `[*]` exatamente antes de `ON CONFLICT`.

**Decisao consequente para Plan 03 (gravada empiricamente):**

Aplicar o **fallback documentado em RESEARCH §2.4** — usar `save()` envolvido em `try/catch DataIntegrityViolationException`. UNIQUE constraint do banco e o gate atomico real (Spring traduz a violacao para a excecao Spring portavel via `SQLExceptionTranslator`). Equivalencia funcional: ambos retornam `boolean novo` para o caller; o contrato `IdempotencyService.tentarPersistir` permanece identico ao planejado.

**Caminho do fallback validado positivamente** no segundo teste do spike: INSERT de wamid duplicado dispara `DataAccessException` (subclasse `DataIntegrityViolationException`); o registro original e preservado (UNIQUE constraint dispara antes de qualquer UPDATE). Plan 03 pode comprometer com o design.

**Spike fica permanentemente como regression test** — se uma versao futura de H2 adicionar suporte a `ON CONFLICT`, o test 1 do spike comecara a falhar (sinal pra revisitar a escolha do fallback e potencialmente migrar para a sintaxe direta, mais barata que try/catch).

## Tasks Executadas

| # | Task | Status | Commit |
|---|------|--------|--------|
| 0 | STEP 0 — Spike OnConflictSpikeTest (gate da phase) | DONE — gate FALHOU como previsto, fallback acionado | 1d2b4c6 |
| 1 | Criar enum `Direcao { in, out }` em lowercase | DONE | 1d2b4c6 |
| 2 | Criar `@Entity ClienteZap` mapeando `whatsapp.clientes_zap` | DONE | 1d2b4c6 |
| 3 | Criar `@Entity MensagemLog` com `@Enumerated(STRING)` Direcao + columnDefinition TEXT | DONE | 1d2b4c6 |
| 4 | Criar `@Entity MediaCache` com PK String columnDefinition CHAR(64) | DONE | 1d2b4c6 |
| 5 | Criar `TipoMensagem` String constants (7) | DONE | 1d2b4c6 |
| 6 | Criar `ClienteZapRepository` esqueleto | DONE | 1d2b4c6 |
| 7 | Criar `MensagemLogRepository` + `MediaCacheRepository` esqueletos com helpers derived queries | DONE | 1d2b4c6 |
| 8 | Verificar build do reator + smoke test JPA | DONE — BUILD SUCCESS, 106 tests reator | 1d2b4c6 |

## Spike Result (Detalhado)

```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

OnConflictSpikeTest.on_conflict_do_nothing_NAO_e_suportado_em_h2_pg_mode_v2_3_232  PASSED
  - assertThatThrownBy(...) verificou BadSqlGrammarException com "ON CONFLICT" na message
  - Confirma que ON CONFLICT NAO e suportado em H2 v2.3.232 PG-mode

OnConflictSpikeTest.unique_constraint_dispara_DataIntegrityViolationException_no_h2_pg_mode  PASSED
  - 1a INSERT direto: rowCount = 1
  - 2a INSERT com mesmo wamid: dispara DataAccessException (subclasse DataIntegrityViolationException)
  - SELECT confirma telefone original preservado (UNIQUE constraint dispara antes de UPDATE)
```

**ON CONFLICT path: FAILED — fallback required**
**Fallback path (save+catch DataIntegrityViolationException): VALIDATED**

## Files Criados (9 source + 1 test = 10 total)

### Entities + Enum (4 arquivos em `model/`)
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/ClienteZap.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/MensagemLog.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/MediaCache.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/Direcao.java`

### Constants (1 arquivo em `util/`)
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/util/TipoMensagem.java`

### Repositories (3 arquivos em `repository/`)
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/repository/ClienteZapRepository.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/repository/MensagemLogRepository.java`
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/repository/MediaCacheRepository.java`

### Spike Test (1 arquivo em `test/spike/`)
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/spike/OnConflictSpikeTest.java`

### Modificados (config — 2 arquivos)
- `api-whatsapp/src/main/resources/application.yml` — removido `hibernate.default_schema=whatsapp`
- `api-whatsapp/src/test/resources/application-test.yml` — removido `hibernate.default_schema=whatsapp`, `DATABASE_TO_UPPER=false` → `DATABASE_TO_LOWER=TRUE`, adicionado `hibernate.boot.allow_jdbc_metadata_access=false` (defesa em profundidade)

## Build & Test Counts

```
[INFO] Reactor Summary for ERP Kit - Modulos Plugaveis 1.1.0-SNAPSHOT:
[INFO] BUILD SUCCESS

api-whatsapp:
  Tests run: 55, Failures: 0, Errors: 0, Skipped: 0
  - 53 baseline tests Phase 1 (todos verdes — zero regressao)
  -  2 novos: OnConflictSpikeTest (spike ON CONFLICT + fallback validated)

Reator total:
  api-email:           34 tests
  api-storage:         13 tests
  api-consultas:        4 tests
  api-whatsapp:        55 tests
  lib-shared/clients:    skip-tests
  ───────────────────────────
  TOTAL:              106 tests, 0 failures, 0 errors
```

Build time full reator: ~30s.

## Commit Hash

`1d2b4c6` — feat(02-01): adicionar entities + repos esqueleto + spike ON CONFLICT

Apos este SUMMARY: 1 commit adicional `docs(02): adicionar SUMMARY plan 01` + atualizacao STATE.md/ROADMAP.md.

## Deviations from Plan

### Auto-fixed Issues (Rule 3 — Blocking Issues)

#### 1. [Rule 3 - Blocking] hibernate.default_schema=whatsapp causava lookup invalido de information_schema.sequences

- **Found during:** Task 9 (verify build do reator)
- **Issue:** Quando ddl-auto: validate roda contra @Entity classes (3 novas em Phase 2), Hibernate executa `SELECT * FROM information_schema.sequences` internamente via `SequenceInformationExtractorLegacyImpl`. Com `hibernate.default_schema: whatsapp` setado em application.yml + application-test.yml (Phase 1 PLAN 04), o prefixo gerava `whatsapp.information_schema.sequences` que nao existe em H2 nem em PostgreSQL. Phase 1 nao detectou porque NAO havia entities — Hibernate nao executava o pre-validation.
- **Erro literal:** `SQLGrammarException: Unable to build DatabaseInformation [Schema "information_schema" not found]; SQL statement: select * from information_schema.sequences [90079-232]`
- **Fix:** Remover `hibernate.default_schema=whatsapp` de ambos application.yml e application-test.yml. As 3 entities ja declaram `@Table(schema = "whatsapp", name = "...")` explicitamente, portanto cada query JPA prefixa corretamente sem precisar de default_schema. O `currentSchema=whatsapp` no JDBC URL de producao mantem defesa em profundidade para queries nativas sem prefixo.
- **Files modificados:** `api-whatsapp/src/main/resources/application.yml`, `api-whatsapp/src/test/resources/application-test.yml`
- **Commit:** 1d2b4c6

#### 2. [Rule 3 - Blocking] DATABASE_TO_UPPER=false em H2 quebra lookup de INFORMATION_SCHEMA system

- **Found during:** Task 9 (apos remover default_schema, novo erro emergiu)
- **Issue:** Mesmo apos remover default_schema, Hibernate continuou buscando `information_schema.sequences` em lowercase, mas H2 com `DATABASE_TO_UPPER=false` deixa o system schema INFORMATION_SCHEMA em UPPERCASE inalcançavel via lookup lowercase. CASE_INSENSITIVE_IDENTIFIERS=true nao bastava.
- **Erro literal (mesmo da issue 1, persistia apos fix #1):** `Schema "information_schema" not found`
- **Fix tentativa A (nao funcionou):** adicionar `hibernate.boot.allow_jdbc_metadata_access=false` para desabilitar pre-fetch — flag ignorada pelo Hibernate 6.6.39.
- **Fix definitivo:** mudar JDBC URL de `DATABASE_TO_UPPER=false` para `DATABASE_TO_LOWER=TRUE`. Agora H2 system schemas (INFORMATION_SCHEMA) ficam acessiveis via nome lowercase que Hibernate gera. Identifiers das nossas tabelas ja sao lowercase no SQL DDL das migrations, entao continuam funcionando.
- **Files modificados:** `api-whatsapp/src/test/resources/application-test.yml` (so test profile — producao usa PG real, nao tem o issue)
- **Commit:** 1d2b4c6

#### 3. [Rule 3 - Blocking] MediaCache.arquivoHash com VARCHAR(64) JPA default nao bate com CHAR(64) do schema

- **Found during:** Task 9 (apos issues 1 e 2 resolvidas)
- **Issue:** V3 migration declara `arquivo_hash CHAR(64) PRIMARY KEY`. JPA default mapeia `String` com `length=64` para VARCHAR. Hibernate validate detectou mismatch: `wrong column type encountered in column [arquivo_hash] in table [whatsapp.media_cache]; found [character (Types#CHAR)], but expecting [varchar(64) (Types#VARCHAR)]`.
- **Fix:** adicionar `columnDefinition = "CHAR(64)"` em `@Column` de `arquivoHash`. Agora JPA + DB concordam que o tipo e CHAR fixo de 64.
- **Files modificados:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/MediaCache.java`
- **Commit:** 1d2b4c6

#### 4. [Rule 3 - Blocking] MensagemLog.conteudo com @Lob faz Hibernate esperar OID/CLOB em vez de TEXT

- **Found during:** Task 9 (apos issue 3 resolvida)
- **Issue:** V2 migration declara `conteudo TEXT`. Em H2 PG-mode + PostgreSQL real, TEXT e CHARACTER VARYING (sem limite) — NAO e OID/large object. `@Lob` faz Hibernate inferir `Types#CLOB` que mapeia para OID em PostgreSQL dialect. Validate detectou: `wrong column type encountered in column [conteudo]; found [character varying (Types#VARCHAR)], but expecting [oid (Types#CLOB)]`.
- **Fix:** remover `@Lob` e adicionar `columnDefinition = "TEXT"` em `@Column` de `conteudo`. JPA usa o tipo declarativo do schema (TEXT), Hibernate nao infere CLOB.
- **Files modificados:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/MensagemLog.java` (removido import `@Lob` tambem)
- **Commit:** 1d2b4c6

### Authentication Gates

Nenhum.

### Architectural Decisions (Rule 4)

Nenhuma — todos os 4 desvios eram blocking issues solucionaveis com config/mapping ajustes (Rule 3).

## Threat Surface Scan

Nenhuma nova superficie de seguranca relevante introduzida. As 3 entities sao mapeamento puro de schema ja existente (V1-V4 da Phase 1). Nenhum novo endpoint, nenhuma mudanca em trust boundary, nenhum novo input do usuario. `toString()` mascara PII (telefone parcial em ClienteZap; conteudo omitido em MensagemLog). Threat register T-02-01..T-02-05 do PLAN ainda valido — T-02-01 mitigado via spike + fallback Plan 03; T-02-02 mitigado via @Enumerated STRING + lowercase enum; T-02-03 mitigado via toString masking; T-02-04 mitigado via insertable=false; T-02-05 (schema drift) mitigado via Hibernate validate (gate empirico no boot).

## Threat Flags

Nenhum.

## Self-Check: PASSED

### Files criados (todos verificados existentes via build verde):
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/ClienteZap.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/MensagemLog.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/MediaCache.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/Direcao.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/util/TipoMensagem.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/repository/ClienteZapRepository.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/repository/MensagemLogRepository.java`
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/repository/MediaCacheRepository.java`
- FOUND: `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/spike/OnConflictSpikeTest.java`

### Commit hash:
- FOUND: `1d2b4c6` — confirmado via `git log --oneline -3`

### Build verde:
- 106 tests reator, 0 failures, 0 errors, BUILD SUCCESS

## Concerns para Wave B (Plans 02-02 + 02-03 + 02-05 paralelos)

1. **Plan 03 (IdempotencyService) deve usar fallback save+catch DataIntegrityViolationException** — NAO tentar `INSERT ... ON CONFLICT` via @Modifying @Query nativeQuery, vai falhar em test (H2). RESEARCH §2.4 tem o codigo de referencia.

2. **`hibernate.boot.allow_jdbc_metadata_access=false` ficou em application-test.yml** — tecnicamente nao foi a flag que resolveu (DATABASE_TO_LOWER=TRUE foi), mas pode ser util manter como defesa em profundidade caso futuras versoes de Hibernate mudem comportamento de extractor de sequences. Documentado em comentario inline.

3. **MediaCache + MensagemLog precisaram `columnDefinition` explicito** — qualquer @Entity futuro em Phase 2+ que mapear coluna TEXT/CHAR/JSONB do PostgreSQL pode precisar do mesmo. CONVENTIONS.md poderia documentar isso como padrao para `api-whatsapp`. Plan 04 (ClienteZapService) so usa colunas VARCHAR/BIGINT/TIMESTAMP — sem risco. Mas se Phase 4 expandir estado_conversa com JSONB, vai precisar.

4. **Spike test fica permanentemente em `test/spike/`** — se H2 atualizar e adicionar suporte a `ON CONFLICT`, test 1 do spike vai falhar (cobertura ativa). Renomear para `db/OnConflictPgModeSupportTest.java` em phase futura nao e prioridade.

5. **Phase 1 unverified — Phase 2 ja construindo em cima** — STATE.md diz "Phase 1 awaiting verifier sign-off". Os 4 fixes Rule 3 deste plano modificaram config files de application.yml/application-test.yml que vieram da Phase 1. Tecnicamente nao quebrou nenhum test pre-existente (zero regressao em 106 tests), mas o verifier da Phase 1 deve confirmar. Defesa: as alteracoes sao melhorias arquiteturais validadas empiricamente — esquema agora suporta `validate` mode com 3 entities, que era pre-requisito de TODA Phase 2.

6. **TipoMensagem tem 7 constants (PLAN ditou)** — CONTEXT.md D-05 listava 8 (incluindo `STATUS`). Plan 05 (parser) precisara confirmar se `STATUS` deve entrar como 8a constant. Nao bloqueia este plan; concern pra Wave 5/6.

7. **Smoke test JPA implicito** — quando Spring boota o test context, ele faz scan de @Repository interfaces. Os 3 repositories carregaram sem `PropertyReferenceException` (helpers `findByWamid`, `findByTelefoneOrderByCriadoEmDesc`, `findByArquivoHashAndExpiraEmAfter` derived queries todos validados). Plans 03/04/06 podem confiar que o scaffold JPA funciona.
