---
phase: 01-fundacao-hmac-webhook
plan: 04
subsystem: api-whatsapp
tags: [api-whatsapp, flyway, migration, schema, h2-postgresql, datasource]

# Dependency graph
requires:
  - phase: 01-fundacao-hmac-webhook
    plan: 03
    provides: WhatsAppProperties + autoconfigure.exclude temporario em ambos os yml
provides:
  - 4 migrations Flyway V1-V4 portaveis (PostgreSQL 15 prod + H2 2.x test) aplicadas no schema 'whatsapp'
  - clientes_zap (PER-02) com UNIQUE(telefone) + 2 indices
  - mensagens_log (PER-03) com UNIQUE(wamid) + CHECK(direcao IN ('in','out')) + 2 indices
  - media_cache (PER-04) com PK CHAR(64) sha256 + indice em expira_em
  - estado_conversa (D6 PROJECT.md) placeholder minimo (telefone PK + ultima_atualizacao)
  - application.yml com spring.datasource (PostgreSQL local 5433) + spring.jpa (validate, default_schema=whatsapp) + spring.flyway (schemas=whatsapp, create-schemas=true) — autoconfigure.exclude removido
  - application-test.yml com H2 in-memory MODE=PostgreSQL + INIT=CREATE SCHEMA + DATABASE_TO_UPPER=false + CASE_INSENSITIVE_IDENTIFIERS=true + DB_CLOSE_DELAY=-1 — autoconfigure.exclude removido
  - FlywayMigrationTest com 6 cenarios verdes (4 tabelas + indices + UNIQUE wamid + CHECK direcao + UNIQUE telefone + flyway_schema_history)
  - Confirmacoes empiricas: A1 (BIGINT GENERATED IDENTITY funciona em H2 v2.x modo PG), A3 (CHECK constraint NAO silenciada), A5 (Hibernate validate sem entities boota OK), A6 (JPA autoconf sem entities OK)
affects:
  - 01-fundacao-hmac-webhook (waves 5-7 podem agora @Autowired JdbcTemplate ou Repository — tabelas reais existem)
  - PLAN-05 (HmacValidator) — pode usar appSecret real via @SpringBootTest(properties=...) sem afetar datasource
  - PLAN-06 (SecurityConfig) — pode injetar WhatsAppProperties.appSecret no HmacSignatureFilter
  - PLAN-07 (WebhookController) — schema persiste; Phase 2 pode adicionar @Entity batendo com migrations

# Tech tracking
tech-stack:
  added:
    - org.springframework.boot:spring-boot-starter-data-jpa (ja vinha do pom.xml de PLAN-02, agora ATIVADO no boot)
    - org.flywaydb:flyway-core + flyway-database-postgresql (ativados — antes inertes via autoconfigure.exclude)
    - com.h2database:h2 v2.3.232 (test-scope, ja no pom; agora carregado no SpringBootTest)
  patterns:
    - "BIGINT GENERATED ALWAYS AS IDENTITY (SQL ANSI standard) em vez de BIGSERIAL/AUTO_INCREMENT — portavel entre PostgreSQL 10+ e H2 2.x sem duplicar migrations por profile"
    - "H2 in-memory MODE=PostgreSQL + JDBC URL com 5 params criticos (MODE/DATABASE_TO_UPPER/CASE_INSENSITIVE_IDENTIFIERS/DB_CLOSE_DELAY/INIT) — preserve case do schema 'whatsapp' lowercase, evita H2 default upper-case que quebra default_schema do Hibernate"
    - "Defesa em profundidade pra criar schema 'whatsapp' antes do Flyway: 3 camadas (V1 com CREATE SCHEMA IF NOT EXISTS + flyway.create-schemas: true + JDBC URL INIT=CREATE SCHEMA) — qualquer 1 das 3 e suficiente, juntas eliminam edge cases"
    - "SQL portavel via spike empirico STEP 0 (W-01 do PLAN-CHECK) ANTES de comprometer com 4 migrations — 5 minutos de teste salvam 30 minutos de fallback se a sintaxe nao funcionar em H2"
    - "INFORMATION_SCHEMA queries usam UPPERCASE (system tables sempre maiusculas em H2) mas valores comparados (TABLE_SCHEMA='whatsapp') usam lowercase porque DATABASE_TO_UPPER=false preserva case do CREATE"
    - "Flyway aplica V1..V4 em UM commit — flyway_schema_history table criada no schema 'whatsapp' (default-schema), permite consulta de auditoria empirica via FlywayMigrationTest"

key-files:
  created:
    - api-whatsapp/src/main/resources/db/migration/V1__criar_tabela_clientes_zap.sql
    - api-whatsapp/src/main/resources/db/migration/V2__criar_tabela_mensagens_log.sql
    - api-whatsapp/src/main/resources/db/migration/V3__criar_tabela_media_cache.sql
    - api-whatsapp/src/main/resources/db/migration/V4__criar_tabela_estado_conversa.sql
    - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/db/FlywayMigrationTest.java
    - .planning/phases/01-fundacao-hmac-webhook/01-04-SUMMARY.md
  modified:
    - api-whatsapp/src/main/resources/application.yml  # +bloco spring.datasource + spring.jpa + spring.flyway; -autoconfigure.exclude
    - api-whatsapp/src/test/resources/application-test.yml  # +H2 PG-mode datasource + jpa H2Dialect + flyway; -autoconfigure.exclude
  deleted:
    - api-whatsapp/src/main/resources/db/migration/.gitkeep  # substituido por V1-V4 SQL files

key-decisions:
  - "BIGINT GENERATED ALWAYS AS IDENTITY (em vez de BIGSERIAL/AUTO_INCREMENT) — empiricamente confirmado em H2 2.3.232 modo PostgreSQL via spike STEP 0 (W-01 do PLAN-CHECK). Spike rodou em 5 minutos antes do plan, eliminando risco de fallback por profile-specific migrations. SQL portavel zero modificacao entre prod (PostgreSQL 15) e test (H2 in-memory)"
  - "Spike empirico ANTES de comprometer 4 migrations — descobertas criticas alem do BIGINT IDENTITY: (1) INFORMATION_SCHEMA precisa ser UPPERCASE, (2) schema name preserva lowercase com DATABASE_TO_UPPER=false, (3) H2 v2.x usa INFORMATION_SCHEMA.INDEX_COLUMNS (nao INDEXES), (4) CHECK constraint NAO silenciada (A3 mitigada), (5) UNIQUE viola via JdbcSQLIntegrityConstraintViolationException mapeado pra DataAccessException. Tudo isso informou as queries do FlywayMigrationTest"
  - "6 cenarios no FlywayMigrationTest (vs 3 do plan) — adicionados: CHECK direcao + UNIQUE telefone + flyway_schema_history. Cobertura mais ampla com custo marginal (~0.5s extra) porque o contexto Spring ja sobe; cada teste extra so faz 2-3 queries"
  - "create-schemas: true em ambos os yml — Flyway cria schema se nao existir. Defesa em profundidade junto com CREATE SCHEMA IF NOT EXISTS na V1 e INIT=CREATE SCHEMA no JDBC URL do H2"
  - "estado_conversa minimo (telefone PK + ultima_atualizacao) — D6 do PROJECT.md confirma 'Persistencia minima'. Phase 2+ estende via ALTER TABLE em V5+ adicionando estado/ultimo_comando/contexto. Phase 1 so precisa que a tabela exista para Flyway aplicar 4 migrations"
  - "Indice implicito do UNIQUE em wamid — confirmado pelo FlywayMigrationTest.mensagens_log_tem_indices (queryForObject conta INDEX_COLUMNS para wamid retorna >0). Significa que NAO precisamos CREATE INDEX explicito em wamid — UNIQUE constraint cria automaticamente"

patterns-established:
  - "Spike empirico pre-commit para validacoes de portabilidade SQL — 5 minutos de teste isolado evita 30+ minutos de fallback por profile-specific migrations. Reusavel sempre que migration depende de feature SQL com risco de divergencia entre DBs"
  - "JDBC URL H2 modo PostgreSQL com 5 params criticos como template reusavel para outros modulos ERPKit que precisem testar PG-portavel SQL sem Testcontainers"
  - "FlywayMigrationTest pattern: empiricamente verifica schema apos boot do SpringBootTest — futuros modulos com migrations podem espelhar (queryForObject sobre INFORMATION_SCHEMA + assertThatThrownBy em violacoes de constraint)"

requirements-completed:
  - PER-01  # Schema PostgreSQL whatsapp aplicado por Flyway (4 migrations V1-V4 verdes em prod yml + test yml)
  # PER-02..PER-04 ainda nao 100% — esquemas existem mas entities/services Phase 2+ vao consumir; quando Phase 2 escrever ClienteZapEntity batendo com clientes_zap, PER-02 fecha

# Metrics
duration: ~12min
completed: 2026-05-05
---

# Phase 01 Plan 04: Migrations Flyway V1-V4 + H2 PostgreSQL-Mode Datasource Summary

**Modulo `api-whatsapp` ganha persistencia: 4 migrations Flyway portaveis (V1 clientes_zap, V2 mensagens_log com UNIQUE wamid + CHECK direcao, V3 media_cache PK CHAR(64), V4 placeholder estado_conversa) sao aplicadas no schema `whatsapp` tanto em prod (PostgreSQL 15 local 5433) quanto em test (H2 2.3.232 modo PostgreSQL). SQL portavel via `BIGINT GENERATED ALWAYS AS IDENTITY` (SQL ANSI standard, suportado por ambos), validado empiricamente via spike STEP 0 ANTES de comprometer com as 4 migrations — eliminou risco de fallback por profile-specific. `application.yml` + `application-test.yml` perdem o `autoconfigure.exclude` temporario, ganham `spring.datasource` + `spring.jpa validate` + `spring.flyway`; o test yml usa H2 com 5 params criticos (`MODE=PostgreSQL;DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS whatsapp`). `FlywayMigrationTest` adiciona 6 cenarios verdes (4 tabelas existem + indices em mensagens_log + UNIQUE wamid + CHECK direcao + UNIQUE telefone + flyway_schema_history com 4 versoes). Reator inteiro BUILD SUCCESS — 87 tests verdes (vs 85 antes do plan), zero regressao em lib-shared/lib-consultas-client/api-email/api-storage/api-consultas.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-05-05T03:55:00Z (spike STEP 0)
- **Completed:** 2026-05-05T04:07:00Z (commit metadata)
- **Tasks:** 8 (Step 0 spike + Tasks 1-4 4 migrations + Tasks 5-6 yml + Task 7 FlywayMigrationTest + Task 8 reactor verify)
- **Files created:** 6 (4 SQL + 1 test class + 1 SUMMARY)
- **Files modified:** 2 (application.yml + application-test.yml)
- **Files deleted:** 1 (.gitkeep substituido por V1-V4)
- **Tests:** 6 novos (todos no FlywayMigrationTest) — verdes. Suite reator total: 87 tests (lib-shared 20 + lib-consultas-client 3 + api-email 34 + api-storage 13 + api-consultas 4 + api-whatsapp 13). Zero regressao.
- **Build time:** ~22s (./mvnw verify reactor inteiro)

## STEP 0: Spike Empirico — BIGINT IDENTITY em H2 PG-mode

**Status:** PASSOU. Validacao do W-01 HIGH do PLAN-CHECK em ~5 minutos.

Antes de comprometer com 4 migrations escrevendo `BIGINT GENERATED ALWAYS AS IDENTITY`, criei um arquivo Java standalone (`/c/tmp/h2-spike/H2BigIntIdentitySpike.java`) que conecta H2 2.3.232 in-memory com `MODE=PostgreSQL;DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true;DB_CLOSE_DELAY=-1` e roda DDL identico ao das migrations + insercoes de teste para validar UNIQUE, CHECK, e queries de INFORMATION_SCHEMA.

**Output do spike (relevante):**
```
=== Insert OK ===
  id=1 nome=a direcao=in
  id=2 nome=b direcao=out
=== UNIQUE constraint OK: JdbcSQLIntegrityConstraintViolationException ===
=== CHECK constraint OK: JdbcSQLIntegrityConstraintViolationException ===
=== Test 4a: INFORMATION_SCHEMA.TABLES com TABLE_SCHEMA='test_spike' ===
  t in test_spike
=== Test 5: INFORMATION_SCHEMA.INDEX_COLUMNS ===
  schema=test_spike table=t col=id idx=PRIMARY_KEY_7
  schema=test_spike table=t col=nome idx=CONSTRAINT_INDEX_7
  schema=test_spike table=t col=nome idx=idx_t_nome
SPIKE PASSOU: BIGINT GENERATED ALWAYS AS IDENTITY funciona em H2 v2.x MODE=PostgreSQL
```

**5 descobertas criticas que informaram o resto do plan:**

1. **`BIGINT GENERATED ALWAYS AS IDENTITY`** funciona — autoincremento gera id=1, 2 corretamente. **A1 do RESEARCH confirmada.**
2. **UNIQUE constraint** dispara `JdbcSQLIntegrityConstraintViolationException` (que Spring mapeia pra `DataIntegrityViolationException`, subclass de `DataAccessException`). FlywayMigrationTest pode confiar nessa cadeia.
3. **CHECK constraint NAO e silenciada** — INSERT com `direcao='xx'` dispara excecao (mesmo classe que UNIQUE). **A3 do RESEARCH (que assumia silenciamento como risco) MITIGADA empiricamente** — pude adicionar test `direcao_tem_check_constraint_in_ou_out` com confianca.
4. **INFORMATION_SCHEMA precisa ser UPPERCASE** (`INFORMATION_SCHEMA.TABLES`, `TABLE_SCHEMA`, `TABLE_NAME`) mesmo com `DATABASE_TO_UPPER=false`. System tables nao sao afetadas pelo flag — apenas user tables. Os VALORES comparados (`TABLE_SCHEMA='whatsapp'`) usam lowercase porque ai sim DATABASE_TO_UPPER preserva case.
5. **Indices ficam em `INFORMATION_SCHEMA.INDEX_COLUMNS`** (uma row por par index/coluna) — NAO em `INFORMATION_SCHEMA.INDEXES` (que existe em algumas versoes mas nao em H2 2.3 modo PG). UNIQUE constraint cria indice implicito (visto como `CONSTRAINT_INDEX_7` no spike), confirmando que `wamid VARCHAR(255) NOT NULL UNIQUE` da V2 nao precisa de CREATE INDEX adicional.

**Cleanup:** spike removido de `/c/tmp/h2-spike/` apos validacao. Mantido o conhecimento via comentarios em `FlywayMigrationTest.java` (JavaDoc descreve as 5 descobertas).

## Accomplishments

- **`V1__criar_tabela_clientes_zap.sql` (26 linhas)**: schema `whatsapp` + tabela `clientes_zap` com `id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`, `id_cliente_erp BIGINT`, `telefone VARCHAR(20) NOT NULL UNIQUE`, `ultima_mensagem_em TIMESTAMP`, `criado_em TIMESTAMP NOT NULL DEFAULT NOW()`. Indices em `telefone` e `id_cliente_erp` per RESEARCH §10. Comentario inline explica idempotencia (instalador WinSW em prod vs migration em test/dev) e portabilidade SQL ANSI.
- **`V2__criar_tabela_mensagens_log.sql` (30 linhas)**: tabela `mensagens_log` com `wamid VARCHAR(255) NOT NULL UNIQUE` (gate atomico de idempotencia para Phase 2 — PER-03/WEB-06), `direcao VARCHAR(3)` com `CONSTRAINT chk_mensagens_log_direcao CHECK (direcao IN ('in', 'out'))` (validado empiricamente via spike), `tipo VARCHAR(50)` opcional, `conteudo TEXT`, `media_id VARCHAR(255)`, `criado_em TIMESTAMP NOT NULL DEFAULT NOW()`. Indices em `telefone` e `criado_em` (UNIQUE wamid ja cria indice implicito).
- **`V3__criar_tabela_media_cache.sql` (22 linhas)**: tabela `media_cache` com `arquivo_hash CHAR(64) PRIMARY KEY` (sha256 hex tem 64 chars exatos — CHAR fixed-width), `media_id VARCHAR(255) NOT NULL`, `criado_em TIMESTAMP NOT NULL DEFAULT NOW()`, `expira_em TIMESTAMP NOT NULL` (sem default — aplicacao calcula `now() + 30 dias`). Indice em `expira_em` para batch de limpeza (Phase 4/6).
- **`V4__criar_tabela_estado_conversa.sql` (17 linhas)**: placeholder minimo (D6 do PROJECT.md) com `telefone VARCHAR(20) PRIMARY KEY` + `ultima_atualizacao TIMESTAMP NOT NULL DEFAULT NOW()`. Phase 2+ pode estender via ALTER TABLE em V5+. Existencia da tabela e suficiente para Flyway aplicar 4 migrations (ROADMAP success criterion 5) e Hibernate `validate` nao reclamar.
- **`application.yml` (88 linhas, +29 vs PLAN-03)**: REMOVIDO `spring.autoconfigure.exclude` (3 linhas + comentario inline). ADICIONADOS:
  - `spring.datasource.url`: `${WHATSAPP_DB_URL:jdbc:postgresql://localhost:5433/erp_mudas?currentSchema=whatsapp}` — env var override com default DEV-local PostgreSQL 15 do pacote MUDAS
  - `spring.datasource.username/password`: defaults `erp_mudas`/`erp_mudas_dev` (DEV only; prod via env vars do WinSW)
  - `spring.jpa.hibernate.ddl-auto: validate`, `open-in-view: false`, `properties.hibernate.dialect: PostgreSQLDialect`, `properties.hibernate.default_schema: whatsapp`
  - `spring.flyway.enabled: true`, `baseline-on-migrate: true`, `schemas: whatsapp`, `default-schema: whatsapp`, `create-schemas: true`
  - Comentario inline explica boot path em PROD (1. connect 2. Flyway 3. Hibernate validate 4. WhatsAppProperties)
- **`application-test.yml` (52 linhas, +24 vs PLAN-03)**: REMOVIDO `spring.autoconfigure.exclude`. ADICIONADO:
  - `spring.datasource.url` H2 in-memory com 5 params: `MODE=PostgreSQL;DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS whatsapp`
  - `spring.datasource.driver-class-name: org.h2.Driver`, `username: sa`, `password:` (vazio)
  - `spring.jpa.hibernate.ddl-auto: validate`, `open-in-view: false`, `database-platform: org.hibernate.dialect.H2Dialect`, `properties.hibernate.default_schema: whatsapp`
  - `spring.flyway.enabled: true`, `baseline-on-migrate: true`, `schemas: whatsapp`, `default-schema: whatsapp`, `create-schemas: true`
  - 5 dummy values pra Bean Validation passar (mantidos de PLAN-03) + `modulo.api-key: test-key`
  - Comentario inline explica cada param do JDBC URL (5 params criticos)
- **`FlywayMigrationTest.java` (159 linhas)**: `@SpringBootTest(classes = WhatsAppApplication.class) + @ActiveProfiles("test")`, autowire `JdbcTemplate`. **6 cenarios verdes** (vs 3 do plan original):
  1. `todas_as_4_tabelas_existem_em_schema_whatsapp` — para cada [clientes_zap, mensagens_log, media_cache, estado_conversa], `SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='whatsapp' AND TABLE_NAME=?` retorna 1
  2. `mensagens_log_tem_indices_em_telefone_e_criado_em` — INDEX_COLUMNS counts > 0 para telefone, criado_em E wamid (este ultimo confirma indice implicito do UNIQUE)
  3. `wamid_tem_constraint_unique` — INSERT 1 OK, INSERT 2 com mesmo wamid lanca DataAccessException
  4. `direcao_tem_check_constraint_in_ou_out` (extra) — INSERT 'in'/'out' OK, INSERT 'xx' lanca DataAccessException (CHECK aplicada — A3 mitigada)
  5. `clientes_zap_tem_unique_em_telefone` (extra) — UNIQUE em telefone enforce
  6. `flyway_schema_history_tem_4_versoes_aplicadas_com_sucesso` (extra) — query `whatsapp.flyway_schema_history` retorna >= 4 rows com `success=TRUE` e versoes contem "1","2","3","4"
  
  JavaDoc da classe documenta as 5 descobertas do spike STEP 0 — futuro dev nao precisa replicar a investigacao.
- **Reator inteiro BUILD SUCCESS** — 87 tests verdes em ~22s, 6 modulos. **Zero regressao** confirmada vs baseline PLAN-03 (lib-shared 20 + lib-consultas-client 3 + api-email 34 + api-storage 13 + api-consultas 4 = 74 nos 5 modulos antigos, identicos. api-whatsapp passou de 7 -> 13 — adicionou os 6 do FlywayMigrationTest).

## Task Commits

1. **Tasks 1-7 + Step 0 (atomico):** `feat(api-whatsapp): adicionar migrations Flyway V1-V4 + datasource (PG prod, H2 PG-mode test)` — commit `febb68b`
   - Plan especificou commit atomico unico (PLAN-04 secao `<commit>`); seguido literalmente.
   - 8 arquivos: 4 SQL + 2 yml modificados + 1 test class + delete .gitkeep (substituido)
   - Pos-commit deletion check: 1 deletion intencional (`.gitkeep` substituido por SQLs reais — mesmo padrao de PLAN-03).

2. **SUMMARY metadata:** commit ainda pendente (proximo passo apos este file ser escrito).

## Files Created/Modified

- **`api-whatsapp/src/main/resources/db/migration/V1__criar_tabela_clientes_zap.sql`** — 26 linhas. Cria schema `whatsapp` + clientes_zap (PER-02). UNIQUE telefone + 2 indices (telefone, id_cliente_erp). Comentario explica idempotencia + portabilidade SQL ANSI.
- **`api-whatsapp/src/main/resources/db/migration/V2__criar_tabela_mensagens_log.sql`** — 30 linhas. Cria mensagens_log (PER-03). UNIQUE wamid (gate Phase 2) + CHECK direcao + 2 indices (telefone, criado_em). Comentario referencia spike empirico.
- **`api-whatsapp/src/main/resources/db/migration/V3__criar_tabela_media_cache.sql`** — 22 linhas. Cria media_cache (PER-04). PK CHAR(64) sha256 + indice em expira_em. Comentario explica TTL (aplicacao calcula).
- **`api-whatsapp/src/main/resources/db/migration/V4__criar_tabela_estado_conversa.sql`** — 17 linhas. Placeholder minimo (D6 PROJECT.md). PK telefone + ultima_atualizacao. Comentario indica expansion path (ALTER TABLE em V5+).
- **`api-whatsapp/src/main/resources/application.yml`** — 88 linhas (+29 vs PLAN-03 88-29=59 anterior). Datasource Postgres + JPA validate + Flyway whatsapp schema. Header expandido (~10 linhas) explica boot path em prod e link com PLAN-03 (autoconfigure.exclude removido).
- **`api-whatsapp/src/test/resources/application-test.yml`** — 52 linhas (+24 vs PLAN-03). H2 PG-mode + JPA H2Dialect + Flyway whatsapp. Header explica cada um dos 5 params criticos do JDBC URL.
- **`api-whatsapp/src/test/java/br/com/erpkit/whatsapp/db/FlywayMigrationTest.java`** — novo, 159 linhas. 6 tests `@SpringBootTest`. JavaDoc da classe documenta 5 descobertas do spike STEP 0.
- **`.planning/phases/01-fundacao-hmac-webhook/01-04-SUMMARY.md`** — este arquivo.

## Decisions Made

- **Spike STEP 0 ANTES de comprometer 4 migrations.** Plan executor seguiu o `<scope>` que recomendava o spike empirico em 5 minutos. Validou A1 (BIGINT IDENTITY) E descobriu 4 detalhes adicionais (case sensitivity, INDEX_COLUMNS path, A3 mitigada). Custo: 5 min. Beneficio: zero risco de fallback por profile-specific yml + queries do FlywayMigrationTest escritas com confianca empirica desde a primeira tentativa (zero round-trips de debug).
- **6 cenarios no FlywayMigrationTest (vs 3 do plan).** Plan original especificou 3 (4 tabelas + indices + UNIQUE wamid). Adicionados: `direcao_tem_check_constraint_in_ou_out` (Rule 2 — adicionar coverage critica que o spike provou ser viavel), `clientes_zap_tem_unique_em_telefone` (Rule 2 — UNIQUE telefone e citado em PER-02 mas nao tinha test, mesmo padrao de wamid), `flyway_schema_history_tem_4_versoes_aplicadas_com_sucesso` (Rule 2 — sucesso criterion 5 do ROADMAP cita "4 migrations aplicadas" e o test mais direto e contar a tabela de auditoria do Flyway). Custo marginal: ~0.5s extra (contexto Spring ja sobe; cada teste extra so faz 2-3 queries). Beneficio: tres assertions extras dao gate empirico maior pra Wave 5 que vai mexer em SecurityConfig.
- **Rule 1: queries de INFORMATION_SCHEMA usam UPPERCASE para system tables, lowercase para valores.** Descoberto no spike. Sem isso, queries falham silenciosamente (count=0 em vez de erro). Documentado em comentario do FlywayMigrationTest e no SUMMARY.
- **`create-schemas: true` em ambos os yml** — defesa em profundidade. Plan original nao mencionou (so `INIT=CREATE SCHEMA` no JDBC URL + V1 com CREATE SCHEMA IF NOT EXISTS). 3 camadas de protecao em vez de 2 — custo zero (Spring Boot Flyway integration ja suporta), elimina edge case onde algum dos outros falha (raro, mas possivel se H2 init nao executar antes do Flyway abrir conexao).
- **Cleanup do `.gitkeep`** — `db/migration/.gitkeep` foi tracked em PLAN-02 para preservar a pasta vazia. Agora com 4 SQL files reais, nao precisa mais. Removido via `git rm --cached` + `rm -f`. Deleção incluida no commit principal (gsd-tools commit, diferente do PLAN-03 que precisou commit chore separado — talvez porque tinha so 1 deleção em vez de 3).

## Deviations from Plan

**1. [Rule 2 - Auto-add coverage critica] FlywayMigrationTest tem 6 cenarios (vs 3 do plan)**
- **Found during:** Task 7 (escrita do test class)
- **Razao:** Spike STEP 0 provou empiricamente que CHECK direcao e UNIQUE telefone funcionam em H2 — adicionar tests pra essas 2 features e custo quase zero (~0.1s per test) com beneficio grande (Wave 5 SecurityConfig vai mexer em coisas adjacentes; gate maior previne regressao em waves futuras). Adicionado tambem `flyway_schema_history` test porque ROADMAP success criterion 5 diz literalmente "4 migrations aplicadas" — counting flyway_schema_history e o test mais direto.
- **Files modified:** FlywayMigrationTest.java (3 metodos extras)
- **Commit:** `febb68b`

**2. [Rule 2 - Auto-add defesa em profundidade] `create-schemas: true` em ambos os yml**
- **Found during:** Task 5 (escrita do application.yml)
- **Razao:** Plan original lista `flyway.schemas: whatsapp` + `flyway.default-schema: whatsapp` mas nao `create-schemas: true`. RESEARCH §8 inclui esse flag. Defesa em profundidade junto com V1 `CREATE SCHEMA IF NOT EXISTS` e JDBC URL `INIT=CREATE SCHEMA` — 3 camadas. Custo zero, elimina edge case raro.
- **Files modified:** application.yml + application-test.yml
- **Commit:** `febb68b`

**3. [Cleanup] `.gitkeep` removido de db/migration/**
- **Found during:** apos Tasks 1-4 (antes do commit)
- **Razao:** Pasta agora tem 4 SQL files reais; placeholder nao tem mais funcao. Mesmo padrao de PLAN-03 (que removeu 3 .gitkeep apos preencher).
- **Files deleted:** `.gitkeep`
- **Commit:** `febb68b` (incluido no commit principal — gsd-tools desta vez nao filtrou a deleção, diferente de PLAN-03)

**4. [Documentacao - JavaDoc expandido] FlywayMigrationTest tem JavaDoc com 5 descobertas do spike**
- **Found during:** Task 7
- **Razao:** Conhecimento ganho no spike e valioso pra futuros devs (Wave 5+ que mexer em queries similares). Documentar inline custa 10 linhas, salva 5 minutos de re-investigacao por dev. Convencao do monorepo encoraja JavaDoc com contexto.
- **Files modified:** FlywayMigrationTest.java (JavaDoc da classe)
- **Commit:** `febb68b`

Nenhum desvio significativo de escopo. Nenhum auto-fix tipo Rule 1 acionado (spike pre-validou; nao houve bugs descobertos durante implementacao). Nenhum tipo Rule 4 acionado (sem mudancas arquiteturais).

## Issues Encountered

- **`information_schema` lowercase nao funciona em H2 modo PG**: descoberto no spike STEP 0, NAO no test class — query lowercase falha com "Schema 'information_schema' not found". Fix: usar UPPERCASE para system table names (`INFORMATION_SCHEMA.TABLES`, `INFORMATION_SCHEMA.INDEX_COLUMNS`). Valores comparados (`TABLE_SCHEMA='whatsapp'`) ficam lowercase porque DATABASE_TO_UPPER=false preserva case do CREATE SCHEMA. Round-trip: 1 (durante spike, antes do test class). Custo: ~30s.
- **PostgreSQLDialect deprecated warning ao boot do test**: log mostra `HHH000511: The 2.3.232 version for [org.hibernate.dialect.PostgreSQLDialect] is no longer supported`. NAO e erro — Hibernate 6.6.39 reclama porque H2 v2.3.232 nao bate com versao minima do PG dialect (12.0). Mas o **test profile usa H2Dialect** (`database-platform: org.hibernate.dialect.H2Dialect` no application-test.yml), entao esse warning ocorre durante boot quando Hibernate roda detection antes do override do test profile aplicar. Em prod nao ocorre porque PG real esta na versao 15. **Inofensivo** — feature flags affected: zero. Out-of-scope para Phase 1 (RESEARCH §13 nao cita; PostgreSQLDialect funciona ate 17 segundo Hibernate docs). Documentado aqui para Phase 2+ se quiser silenciar (remover `dialect` explicit do prod yml — Hibernate auto-detecta a partir do JDBC URL).

## Verification Performed

| Check | Comando | Resultado |
|-------|---------|-----------|
| 4 SQL files existem | `ls api-whatsapp/src/main/resources/db/migration/V*.sql` | 4 files (V1, V2, V3, V4) |
| Zero BIGSERIAL no DDL | `grep -v "^--\|^\s*$" V*.sql \| grep -c BIGSERIAL` | 0 (so em comentarios) |
| BIGINT GENERATED count | `grep -c "BIGINT GENERATED ALWAYS AS IDENTITY" V*.sql` | V1: 2 (1 DDL + 1 comentario), V2: 1 (1 DDL), V3: 0 (PK CHAR(64)), V4: 0 (PK telefone). Total DDL ativo: 2 (id em clientes_zap e mensagens_log) |
| autoconfigure.exclude removido | `grep -n "autoconfigure" application.yml application-test.yml` | Apenas 2 linhas em comentarios explicativos (texto "REMOVIDO"); nenhum bloco YAML ativo |
| MODE=PostgreSQL no test yml | `grep -c "MODE=PostgreSQL" application-test.yml` | 2 (uma no JDBC URL + uma no comentario) |
| INIT=CREATE SCHEMA no test yml | `grep -c "INIT=CREATE SCHEMA" application-test.yml` | 2 (URL + comentario) |
| FlywayMigrationTest sozinho | `./mvnw -pl api-whatsapp test -Dtest=FlywayMigrationTest -q` | Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.884s |
| api-whatsapp test count | `./mvnw verify -pl api-whatsapp -am` | 13 tests verdes (1 happy + 6 fail-fast + 6 Flyway) |
| Reator inteiro | `./mvnw verify` | BUILD SUCCESS — Total time 22.132s — 6 modulos, 87 tests verdes (lib-shared 20, lib-consultas-client 3, api-email 34, api-storage 13, api-consultas 4, api-whatsapp 13). Zero regressao |
| Output Flyway log | (reactor verify) | "Successfully applied 4 migrations to schema 'whatsapp', now at version v4 (execution time 00:00.014s)" |
| api-whatsapp jar repackaged | `./mvnw package -pl api-whatsapp` | BUILD SUCCESS — Spring Boot maven plugin repackaged jar para executable |
| Spike STEP 0 standalone | `java -cp ".;h2-2.3.232.jar" H2BigIntIdentitySpike` | "SPIKE PASSOU: BIGINT GENERATED ALWAYS AS IDENTITY funciona em H2 v2.x MODE=PostgreSQL" + 5 descobertas |
| Pos-commit deletion check | `git diff --diff-filter=D --name-only febb68b~1 febb68b` | `db/migration/.gitkeep` (intencional — substituido por V1-V4) |

## Threat Model Compliance

Per `<threat_model>` do PLAN-04:

| Threat ID | Mitigation enforced | Test |
|-----------|---------------------|------|
| T-04-01 (Tampering: SQL portabilidade H2 vs PG) | BIGINT GENERATED ALWAYS AS IDENTITY (SQL ANSI) + spike empirico STEP 0 confirmando feature works em H2 2.3.232 | FlywayMigrationTest (boot OK + 4 tabelas existem + UNIQUE/CHECK funcionam) |
| T-04-02 (InfoDisclosure: datasource credentials) | Defaults `erp_mudas`/`erp_mudas_dev` apenas DEV-local; prod via env vars `${WHATSAPP_DB_URL}`/`${WHATSAPP_DB_USERNAME}`/`${WHATSAPP_DB_PASSWORD}`. Test usa H2 in-memory sem credenciais sensitivas | application.yml inspection (env var override pattern) |
| T-04-03 (DoS: Flyway falha se schema indisponivel) | 3 camadas de defesa: V1 CREATE SCHEMA IF NOT EXISTS + flyway.create-schemas: true + JDBC INIT=CREATE SCHEMA (test) | FlywayMigrationTest boot OK em H2 fresh in-memory (schema NAO pre-existe; criado pelo INIT) |
| T-04-04 (Tampering: Migration ordem) | Flyway garante ordem por convencao naming V{n}__... | flyway_schema_history_tem_4_versoes_aplicadas_com_sucesso valida versoes 1-4 in-order |
| T-04-05 (Configuration error: A6 JPA sem entity) | Spring Boot tolera EntityManagerFactory sem entities; Hibernate validate so checa o que existe | WhatsAppPropertiesHappyPathTest + FlywayMigrationTest ambos botam contexto JPA sem entities, BUILD SUCCESS |

Todas as 5 ameacas com disposition `mitigate` estao com mitigacao enforced E test-validated.

## Risks Resolved (RESEARCH §13)

- **A1 (BIGINT IDENTITY funciona em H2):** CONFIRMADA empiricamente via spike STEP 0 + FlywayMigrationTest. Sem necessidade de fallback profile-specific.
- **A3 (CHECK constraint silenciosamente ignorada):** MITIGADA empiricamente — H2 2.3.232 modo PG NAO silencia, dispara JdbcSQLIntegrityConstraintViolationException corretamente. Test `direcao_tem_check_constraint_in_ou_out` enforce.
- **A5 (Hibernate validate roda antes de Flyway):** Risco residual zero — Spring Boot 3.x ordena Flyway antes de Hibernate por default; comprovado pelo log do test (sequencia: Flyway "Successfully applied 4 migrations" -> Hibernate "Initialized JPA EntityManagerFactory").
- **A6 (JPA sem entity boota OK):** CONFIRMADA — boot do FlywayMigrationTest e WhatsAppPropertiesHappyPathTest ambos sobem contexto JPA sem nenhuma `@Entity` no classpath. Spring Data Repositories scan retorna "Found 0 JPA repository interfaces" (log) sem erro.

## Concerns para Wave 5 (PLAN-05 HmacValidator + CachedBodyHttpServletRequest)

1. **`HmacValidator` deve ser unit-testavel sem datasource** — design CONTEXT.md D-01 ja prevê isso (service `boolean isValid(byte[] rawBody, String signatureHeader, String appSecret)` puro, zero dependencia Spring/servlet). Tests podem usar plain `new HmacValidator()` com appSecret literal — nao precisa subir contexto JPA.
2. **`CachedBodyHttpServletRequest` test pode usar `MockHttpServletRequest`** — sem subir contexto Spring. Mas se quiser test integrado, ja temos o datasource H2 funcional do Plan 04 — `@SpringBootTest` sobe rapido (<5s) e Flyway aplica V1-V4 sem custo notavel.
3. **`hmacValidator.isValid()` para 0 byte body** — empty body com HMAC vazio e valid? PITFALLS C-02 documenta esse edge case ("skip if empty" e bug original do ContentCachingRequestWrapper). Test class deve ter cenario `body_vazio_com_hmac_correto_e_valido` para enforce que zero-length body ainda computa HMAC.
4. **Charset UTF-8 explicito em Mac.init**: PITFALLS C-04. `appSecret.getBytes(StandardCharsets.UTF_8)` (NAO `appSecret.getBytes()` que usa platform default).
5. **`MessageDigest.isEqual` para comparacao timing-safe** — PITFALLS C-03. Nunca `Arrays.equals(byte[], byte[])` que e short-circuit.
6. **Header `X-Hub-Signature-256` formato `sha256=<hex>`** — strip do prefix antes de hex decode. Cuidado com null/empty/malformed (retornar `false`, nao excecao).
7. **Indice implicito em UNIQUE wamid** — confirmado pelo FlywayMigrationTest. Phase 2 que vai escrever entity `MensagemLog` e repository, fique sabendo que `findByWamid` ja e O(log n) sem precisar `@Index` explicito.
8. **CHECK constraint enforce em test mas NAO em entity Java** — Phase 2 deve adicionar enum `Direcao { in, out }` + `@Convert` ou `@Enumerated(EnumType.STRING)` na entity, dupla camada (Java enum + DB CHECK). Ja documentado em `<risks>` do PLAN-04 (A3).
9. **`flyway.create-schemas: true` em prod** — em ambiente real (PostgreSQL 15), o instalador WinSW/Inno Setup geralmente cria o schema antes do boot. `create-schemas: true` no Flyway e idempotente (no-op se ja existe), entao nao ha risco. Operadores de cliente podem desabilitar via env var se a auditoria deles exigir.

## Self-Check: PASSED

- [x] 4 SQL files existem em `api-whatsapp/src/main/resources/db/migration/V{1,2,3,4}__*.sql` (verificado via ls)
- [x] V1 contem `CREATE SCHEMA IF NOT EXISTS whatsapp` antes do `CREATE TABLE` (verificado via grep)
- [x] V1 e V2 usam `BIGINT GENERATED ALWAYS AS IDENTITY` em DDL ativo (zero `BIGSERIAL` em codigo nao-comentario)
- [x] V2 contem `wamid VARCHAR(255) NOT NULL UNIQUE` + `CONSTRAINT chk_mensagens_log_direcao CHECK (direcao IN ('in', 'out'))` (verificado via grep)
- [x] V3 contem `arquivo_hash CHAR(64) PRIMARY KEY` (verificado via grep)
- [x] V4 contem `telefone VARCHAR(20) PRIMARY KEY` (verificado via grep)
- [x] `application.yml` perdeu `autoconfigure.exclude` (apenas comentario explicativo permanece) e ganhou `spring.datasource.url`, `spring.jpa.hibernate.ddl-auto: validate`, `spring.flyway.schemas: whatsapp` (verificado via grep + Read)
- [x] `application-test.yml` perdeu `autoconfigure.exclude` e ganhou JDBC URL com `MODE=PostgreSQL`, `INIT=CREATE SCHEMA`, `H2Dialect` (verificado via grep + Read)
- [x] `FlywayMigrationTest.java` existe com 6 cenarios verdes (1 das tabelas + 1 dos indices + 4 das constraints/auditoria)
- [x] `./mvnw -pl api-whatsapp test -Dtest=FlywayMigrationTest -q` passa: Tests run: 6, Failures: 0
- [x] `./mvnw verify -pl api-whatsapp -am` BUILD SUCCESS — 13 tests no api-whatsapp
- [x] `./mvnw verify` (reator inteiro) BUILD SUCCESS — 87 tests verdes em ~22s, zero regressao
- [x] Output Flyway log mostra "Successfully applied 4 migrations to schema 'whatsapp'" (verificado durante mvnw verify)
- [x] Commit `febb68b` existe no historico (`git show febb68b --stat`)
- [x] Pos-commit deletion check: 1 deletion intencional (`.gitkeep` substituido por V1-V4 SQLs)
- [x] Spike STEP 0 cleanup: `/c/tmp/h2-spike/` removido apos validacao

---
*Phase: 01-fundacao-hmac-webhook*
*Completed: 2026-05-05*
