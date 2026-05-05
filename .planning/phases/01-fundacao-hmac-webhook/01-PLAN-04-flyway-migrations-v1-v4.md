---
phase: 01-fundacao-hmac-webhook
plan: 04
type: execute
wave: 4
depends_on:
  - "01-03"
files_modified:
  - api-whatsapp/src/main/resources/db/migration/V1__criar_tabela_clientes_zap.sql  # NEW
  - api-whatsapp/src/main/resources/db/migration/V2__criar_tabela_mensagens_log.sql  # NEW
  - api-whatsapp/src/main/resources/db/migration/V3__criar_tabela_media_cache.sql  # NEW
  - api-whatsapp/src/main/resources/db/migration/V4__criar_tabela_estado_conversa.sql  # NEW
  - api-whatsapp/src/main/resources/application.yml  # MODIFY: remover autoconfigure.exclude, adicionar datasource/jpa/flyway
  - api-whatsapp/src/test/resources/application-test.yml  # MODIFY: remover autoconfigure.exclude, adicionar H2 datasource
  - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/db/FlywayMigrationTest.java  # NEW
autonomous: true
requirements:
  - PER-01  # Schema PostgreSQL whatsapp aplicado por Flyway
tags:
  - api-whatsapp
  - flyway
  - migration
  - schema
  - h2-postgresql

must_haves:
  truths:
    - "4 migrations Flyway V1-V4 criam o schema 'whatsapp' + 4 tabelas (clientes_zap, mensagens_log, media_cache, estado_conversa)"
    - "SQL e portavel: roda em PostgreSQL 15 (producao) e em H2 modo PostgreSQL (test) sem alteracao"
    - "BIGINT GENERATED ALWAYS AS IDENTITY usado em vez de BIGSERIAL para portabilidade"
    - "V1 cria 'CREATE SCHEMA IF NOT EXISTS whatsapp' antes do CREATE TABLE — idempotente"
    - "Indices criados em telefone (clientes_zap, mensagens_log) e criado_em (mensagens_log) e expira_em (media_cache)"
    - "wamid em mensagens_log tem UNIQUE constraint (idempotency gate de Phase 2)"
    - "direcao em mensagens_log tem CHECK constraint (in/out)"
    - "application.yml e application-test.yml NAO mais excluem DataSource/JPA/Flyway autoconfig"
    - "application-test.yml usa H2 com MODE=PostgreSQL + INIT=CREATE SCHEMA + outros params para emular Postgres"
    - "FlywayMigrationTest verifica que todas as 4 tabelas existem em schema whatsapp"
    - "mvnw verify -pl api-whatsapp BUILD SUCCESS com migrations aplicadas em H2"
  artifacts:
    - path: "api-whatsapp/src/main/resources/db/migration/V1__criar_tabela_clientes_zap.sql"
      provides: "Schema whatsapp + tabela clientes_zap com UNIQUE(telefone)"
      contains: "CREATE SCHEMA IF NOT EXISTS whatsapp"
    - path: "api-whatsapp/src/main/resources/db/migration/V2__criar_tabela_mensagens_log.sql"
      provides: "Tabela mensagens_log com UNIQUE(wamid) + CHECK(direcao)"
      contains: "wamid VARCHAR(255) NOT NULL UNIQUE"
    - path: "api-whatsapp/src/main/resources/db/migration/V3__criar_tabela_media_cache.sql"
      provides: "Tabela media_cache (sha256 hex PK + TTL)"
      contains: "arquivo_hash    CHAR(64) PRIMARY KEY"
    - path: "api-whatsapp/src/main/resources/db/migration/V4__criar_tabela_estado_conversa.sql"
      provides: "Tabela placeholder estado_conversa (so telefone PK + ultima_atualizacao)"
      contains: "telefone            VARCHAR(20) PRIMARY KEY"
    - path: "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/db/FlywayMigrationTest.java"
      provides: "Tests verificando que as 4 tabelas existem em schema whatsapp + UNIQUE wamid"
      contains: "todas_as_4_tabelas_existem_em_schema_whatsapp"
  key_links:
    - from: "Flyway boot"
      to: "schema whatsapp + 4 tables"
      via: "spring.flyway.schemas + spring.flyway.default-schema"
      pattern: "schemas: whatsapp"
    - from: "application-test.yml"
      to: "H2 PostgreSQL-compat database"
      via: "JDBC URL com MODE=PostgreSQL + INIT"
      pattern: "MODE=PostgreSQL.*INIT=CREATE SCHEMA"
---

<objective>
Criar as 4 migrations Flyway no schema `whatsapp` (V1 clientes_zap, V2 mensagens_log, V3 media_cache, V4 estado_conversa placeholder), expandir `application.yml` com `spring.datasource` + `spring.jpa` + `spring.flyway` e remover o `autoconfigure.exclude` temporario, expandir `application-test.yml` com H2 em modo PostgreSQL (JDBC URL com `MODE=PostgreSQL;DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS whatsapp`), e adicionar `FlywayMigrationTest` que verifica que as 4 tabelas existem no schema correto e que `wamid` tem UNIQUE constraint.

Purpose: Decisao D-06 do CONTEXT.md + requirement PER-01 + ROADMAP success criterion 5. Schema portavel garantido por SQL ANSI (`BIGINT GENERATED ALWAYS AS IDENTITY` em vez de `BIGSERIAL`). Esta phase sai com Flyway aplicando V1-V4 limpas em `mvnw verify` (H2) — base persistente pronta para Phases 2-4 escreverem entities/repositorios.

Output:
- 4 arquivos `.sql` em `api-whatsapp/src/main/resources/db/migration/`
- `application.yml` com datasource Postgres + JPA validate + Flyway no schema whatsapp (sem mais `autoconfigure.exclude`)
- `application-test.yml` com H2 PostgreSQL-mode + Flyway no schema whatsapp
- `FlywayMigrationTest.java` com 3 cenarios verdes
- `mvnw verify -pl api-whatsapp` BUILD SUCCESS
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/STATE.md
@.planning/phases/01-fundacao-hmac-webhook/01-CONTEXT.md
@.planning/phases/01-fundacao-hmac-webhook/01-RESEARCH.md
@.planning/phases/01-fundacao-hmac-webhook/01-03-SUMMARY.md
@.planning/research/PITFALLS.md
@api-email/src/main/resources/db/migration/V1__criar_tabela_emails.sql
@api-email/src/main/resources/application.yml
@api-whatsapp/src/main/resources/application.yml
@api-whatsapp/src/test/resources/application-test.yml

<interfaces>
<!-- DDL completo a copiar de RESEARCH.md §10 (linhas 947-1017) -->

V1__criar_tabela_clientes_zap.sql:
```sql
CREATE SCHEMA IF NOT EXISTS whatsapp;

CREATE TABLE whatsapp.clientes_zap (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_cliente_erp      BIGINT,
    telefone            VARCHAR(20) NOT NULL UNIQUE,
    ultima_mensagem_em  TIMESTAMP,
    criado_em           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clientes_zap_telefone ON whatsapp.clientes_zap(telefone);
CREATE INDEX idx_clientes_zap_id_cliente_erp ON whatsapp.clientes_zap(id_cliente_erp);
```

V2__criar_tabela_mensagens_log.sql:
```sql
CREATE TABLE whatsapp.mensagens_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    wamid       VARCHAR(255) NOT NULL UNIQUE,
    telefone    VARCHAR(20) NOT NULL,
    direcao     VARCHAR(3) NOT NULL,
    tipo        VARCHAR(50),
    conteudo    TEXT,
    media_id    VARCHAR(255),
    criado_em   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_mensagens_log_direcao CHECK (direcao IN ('in', 'out'))
);

CREATE INDEX idx_mensagens_log_telefone ON whatsapp.mensagens_log(telefone);
CREATE INDEX idx_mensagens_log_criado_em ON whatsapp.mensagens_log(criado_em);
```

V3__criar_tabela_media_cache.sql:
```sql
CREATE TABLE whatsapp.media_cache (
    arquivo_hash    CHAR(64) PRIMARY KEY,
    media_id        VARCHAR(255) NOT NULL,
    criado_em       TIMESTAMP NOT NULL DEFAULT NOW(),
    expira_em       TIMESTAMP NOT NULL
);

CREATE INDEX idx_media_cache_expira_em ON whatsapp.media_cache(expira_em);
```

V4__criar_tabela_estado_conversa.sql:
```sql
CREATE TABLE whatsapp.estado_conversa (
    telefone            VARCHAR(20) PRIMARY KEY,
    ultima_atualizacao  TIMESTAMP NOT NULL DEFAULT NOW()
);
```

application-test.yml H2 JDBC URL critico (RESEARCH §9 linha 894):
```
jdbc:h2:mem:testdb_whatsapp;MODE=PostgreSQL;DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS whatsapp
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Criar V1__criar_tabela_clientes_zap.sql</name>
  <files>api-whatsapp/src/main/resources/db/migration/V1__criar_tabela_clientes_zap.sql</files>
  <action>
    Criar `V1__criar_tabela_clientes_zap.sql` com o conteudo da secao 10 do `01-RESEARCH.md` (linhas 947-963):

    ```sql
    -- V1: schema + tabela de clientes WhatsApp
    -- IDEMPOTENT: instalador Inno Setup ja cria o schema em prod; em test/dev a migration tambem cria.
    CREATE SCHEMA IF NOT EXISTS whatsapp;

    CREATE TABLE whatsapp.clientes_zap (
        id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        id_cliente_erp      BIGINT,
        telefone            VARCHAR(20) NOT NULL UNIQUE,
        ultima_mensagem_em  TIMESTAMP,
        criado_em           TIMESTAMP NOT NULL DEFAULT NOW()
    );

    CREATE INDEX idx_clientes_zap_telefone ON whatsapp.clientes_zap(telefone);
    CREATE INDEX idx_clientes_zap_id_cliente_erp ON whatsapp.clientes_zap(id_cliente_erp);
    ```

    **Pontos criticos:**
    - `BIGINT GENERATED ALWAYS AS IDENTITY` (NAO `BIGSERIAL`) — D-06 + RESEARCH §10 Notas + Assumption A1. Funciona em Postgres 10+ E H2 2.x.
    - `CREATE SCHEMA IF NOT EXISTS whatsapp` antes do CREATE TABLE — idempotente, importante para H2 onde INIT do JDBC URL ja criou o schema (no-op safe).
    - Encoding UTF-8 (RESEARCH §13 Risco 4 — projeto.build.sourceEncoding ja UTF-8 no pom raiz).
  </action>
  <verify>
    <automated>test -f api-whatsapp/src/main/resources/db/migration/V1__criar_tabela_clientes_zap.sql && grep -c "CREATE SCHEMA IF NOT EXISTS whatsapp" api-whatsapp/src/main/resources/db/migration/V1__criar_tabela_clientes_zap.sql</automated>
  </verify>
  <done>
    - Arquivo existe
    - Contem CREATE SCHEMA + CREATE TABLE clientes_zap + 2 indices
    - Usa BIGINT GENERATED ALWAYS AS IDENTITY (verificavel via grep)
  </done>
</task>

<task type="auto">
  <name>Task 2: Criar V2__criar_tabela_mensagens_log.sql</name>
  <files>api-whatsapp/src/main/resources/db/migration/V2__criar_tabela_mensagens_log.sql</files>
  <action>
    Criar `V2__criar_tabela_mensagens_log.sql` com o conteudo da secao 10 do `01-RESEARCH.md` (linhas 970-986):

    ```sql
    -- V2: log de mensagens recebidas e enviadas (UNIQUE wamid garante idempotencia)
    CREATE TABLE whatsapp.mensagens_log (
        id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        wamid       VARCHAR(255) NOT NULL UNIQUE,
        telefone    VARCHAR(20) NOT NULL,
        direcao     VARCHAR(3) NOT NULL,
        tipo        VARCHAR(50),
        conteudo    TEXT,
        media_id    VARCHAR(255),
        criado_em   TIMESTAMP NOT NULL DEFAULT NOW(),
        CONSTRAINT chk_mensagens_log_direcao CHECK (direcao IN ('in', 'out'))
    );

    CREATE INDEX idx_mensagens_log_telefone ON whatsapp.mensagens_log(telefone);
    CREATE INDEX idx_mensagens_log_criado_em ON whatsapp.mensagens_log(criado_em);
    -- wamid ja tem UNIQUE index implicito da constraint
    ```

    **Pontos criticos:**
    - `wamid VARCHAR(255) NOT NULL UNIQUE` — UNIQUE e o gate atomico de idempotencia para Phase 2 (PER-03 + WEB-06).
    - `CHECK (direcao IN ('in', 'out'))` — H2 modo PG suporta; assumption A3 — pode ser silenciosamente ignorada em alguns DBs. PER-03 confirma o constraint no design.
    - Indices em `telefone` e `criado_em` — performance traps documentados em PITFALLS (P95 grows linearly without index).
  </action>
  <verify>
    <automated>test -f api-whatsapp/src/main/resources/db/migration/V2__criar_tabela_mensagens_log.sql && grep -c "wamid VARCHAR(255) NOT NULL UNIQUE" api-whatsapp/src/main/resources/db/migration/V2__criar_tabela_mensagens_log.sql</automated>
  </verify>
  <done>
    - Arquivo existe e contem UNIQUE wamid + CHECK direcao + 2 indices
  </done>
</task>

<task type="auto">
  <name>Task 3: Criar V3__criar_tabela_media_cache.sql</name>
  <files>api-whatsapp/src/main/resources/db/migration/V3__criar_tabela_media_cache.sql</files>
  <action>
    Criar `V3__criar_tabela_media_cache.sql` com o conteudo da secao 10 do `01-RESEARCH.md` (linhas 994-1003):

    ```sql
    -- V3: cache de media_id por sha256 do arquivo (TTL 30 dias, gerenciado pela aplicacao)
    CREATE TABLE whatsapp.media_cache (
        arquivo_hash    CHAR(64) PRIMARY KEY,
        media_id        VARCHAR(255) NOT NULL,
        criado_em       TIMESTAMP NOT NULL DEFAULT NOW(),
        expira_em       TIMESTAMP NOT NULL
    );

    CREATE INDEX idx_media_cache_expira_em ON whatsapp.media_cache(expira_em);
    ```

    **Pontos criticos:**
    - `arquivo_hash CHAR(64) PRIMARY KEY` — sha256 hex e exatamente 64 caracteres; CHAR(64) economiza vs VARCHAR.
    - `expira_em TIMESTAMP NOT NULL` — sem default. Aplicacao calcula `now() + 30 dias` e popula explicitamente (Phase 4).
    - Indice em `expira_em` — futuro batch de limpeza pode escanear por TTL expirado eficientemente.
  </action>
  <verify>
    <automated>test -f api-whatsapp/src/main/resources/db/migration/V3__criar_tabela_media_cache.sql && grep "CHAR(64) PRIMARY KEY" api-whatsapp/src/main/resources/db/migration/V3__criar_tabela_media_cache.sql</automated>
  </verify>
  <done>
    - Arquivo existe com PK CHAR(64), expira_em NOT NULL, indice em expira_em
  </done>
</task>

<task type="auto">
  <name>Task 4: Criar V4__criar_tabela_estado_conversa.sql (placeholder minimo)</name>
  <files>api-whatsapp/src/main/resources/db/migration/V4__criar_tabela_estado_conversa.sql</files>
  <action>
    Criar `V4__criar_tabela_estado_conversa.sql` com o conteudo da secao 10 do `01-RESEARCH.md` (linhas 1010-1016). PER D6 do PROJECT.md ("Persistencia minima de estado de conversa — so ultima_mensagem_em").

    ```sql
    -- V4: placeholder para estado de conversa (Phase 2+ pode estender com colunas adicionais).
    -- Phase 1 cria so a estrutura minima — suficiente pra Hibernate validate nao reclamar
    -- e suficiente pra futuras phases adicionarem colunas via ALTER TABLE em V5+.
    CREATE TABLE whatsapp.estado_conversa (
        telefone            VARCHAR(20) PRIMARY KEY,
        ultima_atualizacao  TIMESTAMP NOT NULL DEFAULT NOW()
    );
    ```

    **Nota:** este placeholder esta consciente em relacao ao escopo. Phase 2+ pode ALTER TABLE adicionando estado VARCHAR(50), ultimo_comando VARCHAR(100), etc. Para Phase 1, a tabela existe so para validar que Flyway aplica as 4 migrations conforme ROADMAP success criterion 5.
  </action>
  <verify>
    <automated>test -f api-whatsapp/src/main/resources/db/migration/V4__criar_tabela_estado_conversa.sql && grep "telefone            VARCHAR(20) PRIMARY KEY" api-whatsapp/src/main/resources/db/migration/V4__criar_tabela_estado_conversa.sql</automated>
  </verify>
  <done>
    - Arquivo existe com PK telefone + ultima_atualizacao default NOW()
  </done>
</task>

<task type="auto">
  <name>Task 5: Atualizar application.yml — remover autoconfigure.exclude, adicionar datasource/jpa/flyway</name>
  <files>api-whatsapp/src/main/resources/application.yml</files>
  <action>
    Modificar `application.yml`:

    **REMOVER** o bloco `spring.autoconfigure.exclude` introduzido em PLAN-02 (com 3 entradas: DataSourceAutoConfiguration, HibernateJpaAutoConfiguration, FlywayAutoConfiguration). Comentar tambem o comentario "PLAN-03 only — PLAN-04 remove..." que esta inline.

    **ADICIONAR** os blocos de RESEARCH §8 (linhas 810-833):

    ```yaml
    spring:
      application:
        name: api-whatsapp

      datasource:
        url: ${WHATSAPP_DB_URL:jdbc:postgresql://localhost:5433/erp_mudas?currentSchema=whatsapp}
        username: ${WHATSAPP_DB_USERNAME:erp_mudas}
        password: ${WHATSAPP_DB_PASSWORD:erp_mudas_dev}

      jpa:
        hibernate:
          ddl-auto: validate
        open-in-view: false
        properties:
          hibernate:
            dialect: org.hibernate.dialect.PostgreSQLDialect
            default_schema: whatsapp

      flyway:
        enabled: true
        baseline-on-migrate: true
        schemas: whatsapp
        default-schema: whatsapp
        create-schemas: true
    ```

    Resultado esperado: `application.yml` tem todas as secoes de RESEARCH §8 (server, spring [datasource+jpa+flyway+application], app.modulos.whatsapp, modulo, springdoc, logging, management). O bloco autoconfigure.exclude desapareceu.

    **Pontos criticos** per RESEARCH §8 "Decisoes de yml comentadas":
    - `currentSchema=whatsapp` no JDBC URL — defesa em profundidade.
    - `flyway.create-schemas: true` — Flyway cria o schema em test/dev se necessario; em prod a V1 ja tem CREATE SCHEMA IF NOT EXISTS.
    - `default_schema: whatsapp` no Hibernate — sem isso, validate buscaria tabelas em schema `public`.
    - `ddl-auto: validate` (NAO `update`/`create`) — schema vem 100% do Flyway.
  </action>
  <verify>
    <automated>grep -c "autoconfigure" api-whatsapp/src/main/resources/application.yml && grep -c "schemas: whatsapp" api-whatsapp/src/main/resources/application.yml</automated>
  </verify>
  <done>
    - `autoconfigure.exclude` removido (grep retorna 0 ou apenas comentarios; verificar manualmente)
    - `spring.flyway.schemas: whatsapp` presente
    - `spring.datasource.url` com `?currentSchema=whatsapp` presente
    - `spring.jpa.hibernate.ddl-auto: validate` presente
  </done>
</task>

<task type="auto">
  <name>Task 6: Atualizar application-test.yml — H2 PostgreSQL-mode + remover excludes</name>
  <files>api-whatsapp/src/test/resources/application-test.yml</files>
  <action>
    Modificar `application-test.yml`:

    **REMOVER** o bloco `spring.autoconfigure.exclude` introduzido em PLAN-03.

    **ADICIONAR** datasource H2 PostgreSQL-mode + jpa + flyway, conforme RESEARCH §9 (linhas 890-929):

    ```yaml
    spring:
      datasource:
        url: jdbc:h2:mem:testdb_whatsapp;MODE=PostgreSQL;DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS whatsapp
        driver-class-name: org.h2.Driver
        username: sa
        password:

      jpa:
        hibernate:
          ddl-auto: validate
        open-in-view: false
        database-platform: org.hibernate.dialect.H2Dialect
        properties:
          hibernate:
            default_schema: whatsapp

      flyway:
        enabled: true
        baseline-on-migrate: true
        schemas: whatsapp
        default-schema: whatsapp

    # Dummy values pra Bean Validation passar
    app:
      modulos:
        whatsapp:
          phoneNumberId: test-phone-id
          accessToken: test-access-token
          appSecret: test-app-secret
          verifyToken: test-verify-token
          erpCallbackUrl: http://localhost:0/test
          callbackTimeout: 5s

    modulo:
      versao: 1.0.0-test
      api-key: test-key
    ```

    **Pontos criticos** per RESEARCH §9 "JDBC URL params explicados":
    - `MODE=PostgreSQL` — H2 emula sintaxe PostgreSQL (BIGINT GENERATED ALWAYS AS IDENTITY, NOW(), CHECK)
    - `DATABASE_TO_UPPER=false` — preserve lowercase identifiers (essencial para `clientes_zap` casar)
    - `CASE_INSENSITIVE_IDENTIFIERS=true` — defensa adicional
    - `DB_CLOSE_DELAY=-1` — mantem DB vivo entre testes do mesmo SpringContext
    - `INIT=CREATE SCHEMA IF NOT EXISTS whatsapp` — cria schema antes de Flyway tocar (mais robusto que `flyway.create-schemas`)
    - `database-platform: H2Dialect` (NAO PostgreSQLDialect em test) — Hibernate sabe que e H2 mas o SQL e portavel

    **Verificar:** quando o test rodar, o output do Flyway no log deve mostrar 4 migrations aplicadas em ordem (V1 → V4) no schema `whatsapp`.
  </action>
  <verify>
    <automated>grep -c "MODE=PostgreSQL" api-whatsapp/src/test/resources/application-test.yml && grep -c "INIT=CREATE SCHEMA" api-whatsapp/src/test/resources/application-test.yml</automated>
  </verify>
  <done>
    - autoconfigure.exclude removido
    - JDBC URL com MODE=PostgreSQL + INIT presente
    - JPA com H2Dialect + default_schema: whatsapp
    - Flyway com schemas: whatsapp
    - 5 dummy values mantidos para Bean Validation
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 7: Criar FlywayMigrationTest com 3 cenarios</name>
  <files>api-whatsapp/src/test/java/br/com/erpkit/whatsapp/db/FlywayMigrationTest.java</files>
  <behavior>
    3 testes per RESEARCH §12.5 linhas 1118-1154:

    1. `todas_as_4_tabelas_existem_em_schema_whatsapp` — para cada uma de [clientes_zap, mensagens_log, media_cache, estado_conversa], `SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='whatsapp' AND table_name=?` retorna 1
    2. `mensagens_log_tem_indices_em_telefone_e_criado_em` — verificar via information_schema.indexes (H2) que indices existem
    3. `wamid_tem_constraint_unique` — INSERT 1: sucesso. INSERT 2 com mesmo wamid: lanca DataAccessException (UNIQUE constraint violation).
  </behavior>
  <action>
    Criar `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/db/FlywayMigrationTest.java`. Copiar a estrutura da secao 12.5 do RESEARCH (linhas 1120-1154) e expandir os 3 metodos:

    ```java
    package br.com.erpkit.whatsapp.db;

    import br.com.erpkit.whatsapp.WhatsAppApplication;
    import org.junit.jupiter.api.Test;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.boot.test.context.SpringBootTest;
    import org.springframework.dao.DataAccessException;
    import org.springframework.jdbc.core.JdbcTemplate;
    import org.springframework.test.context.ActiveProfiles;

    import java.util.List;

    import static org.assertj.core.api.Assertions.assertThat;
    import static org.assertj.core.api.Assertions.assertThatThrownBy;

    @SpringBootTest(classes = WhatsAppApplication.class)
    @ActiveProfiles("test")
    class FlywayMigrationTest {

        @Autowired JdbcTemplate jdbc;

        @Test
        void todas_as_4_tabelas_existem_em_schema_whatsapp() {
            List<String> esperadas = List.of("clientes_zap", "mensagens_log", "media_cache", "estado_conversa");
            for (String t : esperadas) {
                Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'whatsapp' AND table_name = ?",
                    Integer.class, t);
                assertThat(count).as("Tabela " + t + " deve existir em schema whatsapp").isEqualTo(1);
            }
        }

        @Test
        void mensagens_log_tem_indices_em_telefone_e_criado_em() {
            // H2 expoe indices via information_schema.indexes (com 's')
            // Em PG seria pg_indexes; aqui usamos a portabilidade do H2 modo PG.
            Integer telefoneIdx = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.indexes WHERE table_schema='whatsapp' AND table_name='mensagens_log' AND column_name='telefone'",
                Integer.class);
            assertThat(telefoneIdx).as("Indice em mensagens_log.telefone deve existir").isPositive();

            Integer criadoEmIdx = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.indexes WHERE table_schema='whatsapp' AND table_name='mensagens_log' AND column_name='criado_em'",
                Integer.class);
            assertThat(criadoEmIdx).as("Indice em mensagens_log.criado_em deve existir").isPositive();
        }

        @Test
        void wamid_tem_constraint_unique() {
            jdbc.update("INSERT INTO whatsapp.mensagens_log (wamid, telefone, direcao) VALUES ('w-test-1', '5511999999999', 'in')");

            assertThatThrownBy(() ->
                jdbc.update("INSERT INTO whatsapp.mensagens_log (wamid, telefone, direcao) VALUES ('w-test-1', '5511888888888', 'in')")
            ).isInstanceOf(DataAccessException.class);
        }
    }
    ```

    **Notas de portabilidade entre PG e H2 nas queries de information_schema:**
    - PostgreSQL real tem `information_schema.tables` (mesma forma) — ok
    - H2 modo PG: indices ficam em `information_schema.indexes` (singular `INDEX_NAME`, plural tabela)
    - Se Task 7 falhar por nome de tabela/coluna em info_schema diferente entre H2 e PG, RESEARCH Risco 2 documenta — adicionar Testcontainers em Phase 6 se necessario. Para Phase 1, H2 modo PG e suficiente.

    **CHECK constraint A3 (RESEARCH §13):** Adicionar test opcional verificando que INSERT com `direcao='xx'` falha — se H2 silenciosamente aceitar, documentar mas nao bloquear o build (Phase 6 com Testcontainers fecha esse gap).
  </action>
  <verify>
    <automated>./mvnw -pl api-whatsapp test -Dtest=FlywayMigrationTest -q</automated>
  </verify>
  <done>
    - Surefire: "Tests run: 3, Failures: 0"
    - Output do Flyway no log mostra 4 migrations aplicadas (V1, V2, V3, V4)
    - Cada teste passa
  </done>
</task>

<task type="auto">
  <name>Task 8: Verificar build do reator</name>
  <files>(nenhum modificado)</files>
  <action>
    Rodar `./mvnw verify -pl api-whatsapp -q` para confirmar que api-whatsapp esta verde com Properties + Migrations + 2 test classes (Properties + Flyway).

    Se algum teste falhar:
    - "Schema 'WHATSAPP' not found" → JDBC URL nao tem `INIT=CREATE SCHEMA IF NOT EXISTS whatsapp` ou `flyway.schemas` nao corresponde
    - "Hibernate validation: table not found in schema whatsapp" → `default_schema` nao foi setado em jpa.properties.hibernate, OU migrations nao rodaram
    - "BIGINT GENERATED ALWAYS AS IDENTITY syntax error" → A1 (Assumption) falhou; trocar por `BIGINT AUTO_INCREMENT` em H2 ONLY (via profile yml) e ja usar `BIGSERIAL` em prod yml
    - "Bean Validation: phoneNumberId required" → application-test.yml perdeu os dummy values
  </action>
  <verify>
    <automated>./mvnw verify -pl api-whatsapp -q</automated>
  </verify>
  <done>
    - BUILD SUCCESS
    - Tests run total: 10 (7 do PropertiesValidationTest + 3 do FlywayMigrationTest)
    - Output Flyway: "Successfully applied 4 migrations to schema [WHATSAPP] OR [whatsapp]"
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Flyway boot → schema whatsapp | Migrations rodam com permissao do datasource user — nao deve criar nada fora do schema whatsapp |
| H2 (test) vs PostgreSQL (prod) | SQL deve produzir resultado identico nos dois — risco de divergencia silenciosa |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-04-01 | Tampering | SQL portabilidade H2 vs PostgreSQL | mitigate | `BIGINT GENERATED ALWAYS AS IDENTITY` + `MODE=PostgreSQL` H2 — RESEARCH A1 + Risco 2 documentam fallback (Testcontainers em Phase 6); FlywayMigrationTest e gate empirico |
| T-04-02 | Information Disclosure | datasource credentials em application.yml | mitigate | Defaults `erp_mudas`/`erp_mudas_dev` apenas para DEV local; produzao usa env vars `${WHATSAPP_DB_*}`. Em test usa H2 in-memory sem credenciais sensitivas. |
| T-04-03 | Denial of Service | Flyway falha se schema indisponivel | mitigate | `CREATE SCHEMA IF NOT EXISTS` na V1 + `flyway.create-schemas: true` + JDBC `INIT=CREATE SCHEMA` (H2) — 3 camadas de defesa em profundidade |
| T-04-04 | Tampering | Migration ordem (V1 antes de V2 etc) | mitigate | Flyway garante ordem por convencao de naming `V{n}__...`; FlywayMigrationTest verifica que as 4 tabelas existem (gate empirico) |
| T-04-05 | Configuration error | A6 (RESEARCH): JPA sem entity em classpath | mitigate | Spring Boot tolera EntityManagerFactory sem entities; Hibernate `validate` so checa o que existe. Empiricamente confirmado em Task 8 |
</threat_model>

<verification>
## Phase Checks

1. 4 arquivos `.sql` existem em `api-whatsapp/src/main/resources/db/migration/V{1,2,3,4}__*.sql`
2. `grep -c "BIGSERIAL" api-whatsapp/src/main/resources/db/migration/*.sql` retorna 0 (apenas BIGINT GENERATED)
3. `grep -c "BIGINT GENERATED ALWAYS AS IDENTITY" api-whatsapp/src/main/resources/db/migration/*.sql` retorna >= 2 (V1 e V2)
4. `grep -c "autoconfigure.exclude" api-whatsapp/src/main/resources/application.yml` retorna 0
5. `grep -c "autoconfigure.exclude" api-whatsapp/src/test/resources/application-test.yml` retorna 0
6. `grep -c "MODE=PostgreSQL" api-whatsapp/src/test/resources/application-test.yml` retorna 1
7. `./mvnw verify -pl api-whatsapp` BUILD SUCCESS — Tests run >= 10
8. Output do Flyway no log mostra "Successfully applied 4 migrations"
</verification>

<success_criteria>
- 4 migrations Flyway criam schema whatsapp + 4 tabelas (clientes_zap, mensagens_log, media_cache, estado_conversa)
- SQL portavel: passa em H2 modo PostgreSQL (test) e funcionaria em PostgreSQL 15 (prod)
- application.yml + application-test.yml configurados sem mais autoconfigure.exclude
- FlywayMigrationTest 3 cenarios verdes (4 tabelas + indices + UNIQUE wamid)
- mvnw verify -pl api-whatsapp BUILD SUCCESS com Tests run >= 10
- 1 commit atomico
</success_criteria>

<commit>
Mensagem (Conventional Commits PT-BR):

```
feat(api-whatsapp): adicionar migrations Flyway V1-V4 + datasource

V1 cria schema whatsapp + clientes_zap (UNIQUE telefone, indices)
V2 cria mensagens_log (UNIQUE wamid + CHECK direcao + indices) — gate idempotency Phase 2
V3 cria media_cache (sha256 PK CHAR(64) + TTL)
V4 placeholder estado_conversa (so telefone PK + ultima_atualizacao; expand em Phase 2+)

SQL portavel via BIGINT GENERATED ALWAYS AS IDENTITY (PostgreSQL 10+ e H2 2.x).
application.yml expandido com spring.datasource + jpa validate + flyway no schema whatsapp.
application-test.yml configurado com H2 modo PostgreSQL + INIT CREATE SCHEMA.
FlywayMigrationTest verifica 4 tabelas + UNIQUE wamid empiricamente.

Refs: D-06 (CONTEXT.md), PER-01 (REQUIREMENTS.md), 01-RESEARCH.md §8 §9 §10 §12.5
```

Comando:
```bash
node $HOME/.claude/get-shit-done/bin/gsd-tools.cjs commit "feat(api-whatsapp): adicionar migrations Flyway V1-V4 + datasource" --files \
  api-whatsapp/src/main/resources/db/migration/V1__criar_tabela_clientes_zap.sql \
  api-whatsapp/src/main/resources/db/migration/V2__criar_tabela_mensagens_log.sql \
  api-whatsapp/src/main/resources/db/migration/V3__criar_tabela_media_cache.sql \
  api-whatsapp/src/main/resources/db/migration/V4__criar_tabela_estado_conversa.sql \
  api-whatsapp/src/main/resources/application.yml \
  api-whatsapp/src/test/resources/application-test.yml \
  api-whatsapp/src/test/java/br/com/erpkit/whatsapp/db/FlywayMigrationTest.java
```
</commit>

<risks>
- **A1 (RESEARCH §13): `BIGINT GENERATED ALWAYS AS IDENTITY` pode nao funcionar em alguma versao de H2** — Task 8 e o gate empirico. Se falhar, fallback: usar profile-specific yml (Postgres prod com BIGSERIAL, H2 test com AUTO_INCREMENT). Custo: 4 migrations duplicadas. Preferir adotar Testcontainers em Phase 6 antes desse fallback.
- **A3 (RESEARCH §13): CHECK constraint silenciosamente ignorada em H2** — sintoma seria insert de `direcao='xx'` passar em test. Phase 1 nao testa isso explicitamente. Phase 2 (quando service layer escrever direcao) pode aceitar valor invalido em H2 mas falhar em PG. Mitigacao: enum Java + validacao na camada de service (Phase 2) reforça em vez de depender so do CHECK.
- **A5 (RESEARCH §13): Hibernate `validate` roda antes de Flyway** — Risco baixo porque `flyway.create-schemas: true` + V1 com `CREATE SCHEMA IF NOT EXISTS` + JDBC `INIT=CREATE SCHEMA` cobrem. Se falhar, sintoma: "schema 'whatsapp' does not exist" no boot test.
- **Information_schema queries diferentes em H2 vs PostgreSQL** — Task 7 pode falhar com sintaxe ligeiramente diferente. Mitigacao: usar query mais simples (so SELECT count FROM info_schema.tables) que funciona em ambos; query de indices pode quebrar — se quebrar, simplificar para so verificar a tabela existe (fallback).
</risks>

<output>
Apos completar, criar `.planning/phases/01-fundacao-hmac-webhook/01-04-SUMMARY.md` com:
- 4 SQL files criados
- application.yml expandido (datasource + jpa + flyway, sem mais autoconfigure.exclude)
- application-test.yml com H2 PostgreSQL-mode (sem mais autoconfigure.exclude)
- FlywayMigrationTest verde (3 cenarios)
- Reactor `mvnw verify -pl api-whatsapp` BUILD SUCCESS, Tests run >= 10
- Confirmacao A1 (BIGINT GENERATED IDENTITY funciona em H2 modo PG)
- Confirmacao A6 (JPA sem entities boota OK)
- Commit hash
</output>
