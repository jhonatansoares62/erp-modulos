# Phase 2: Persistencia + Idempotencia - Research

**Researched:** 2026-05-05
**Domain:** Spring Boot 3.5.9 JPA + native ON CONFLICT + Jackson webhook parser + REQUIRES_NEW + H2 PG-mode
**Confidence:** HIGH (Phase 1 ja deployou esquema + H2 PG-mode validado empiricamente; padroes do monorepo estabelecidos em api-email)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 — JPA Entities com `@Table(schema = "whatsapp")` + `Instant` para timestamps + sem Lombok:**
  - 3 entities: `ClienteZap`, `MensagemLog`, `MediaCache` — `@Entity` + `@Table(schema = "whatsapp", name = "...")`.
  - `Instant` (UTC-anchored) para timestamps — Phase 4 vai comparar com `Instant.now()` na trava 24h.
  - Sem Lombok — convencao do monorepo (CONVENTIONS.md), getters/setters explicitos.
  - `GenerationType.IDENTITY` em PKs auto-incrementadas (match com `BIGINT GENERATED ALWAYS AS IDENTITY` empiricamente validado em H2 PG-mode na Wave 4 da Phase 1).
  - `criadoEm`: `insertable = false, updatable = false` — `DEFAULT NOW()` do banco preenche, Hibernate nunca toca o campo no INSERT/UPDATE mas le no SELECT.

- **D-02 — Idempotency via native `INSERT ... ON CONFLICT (wamid) DO NOTHING` + row-count gate:**
  - `MensagemLogRepository.inserirSeNovo(...)` retorna `int` (1 = inseriu, 0 = duplicata).
  - Sem `SELECT findByWamid` antes — TOCTOU window eliminada (PITFALLS C-06).
  - Spike obrigatorio na Wave 1 valida que H2 v2.x modo PG aceita a sintaxe; fallback documentado neste RESEARCH (Secao 2.4).

- **D-03 — Normalizacao de telefone BR em utility puro `TelefoneBR.normalizar(String)`:**
  - DDDs SP (11-19), RJ (21, 22, 24), ES (27, 28) mantem 9o digito; demais estados strip.
  - Numeros nao-Brasil (sem prefixo `55`, ou len fora 12-13): retorna sanitizado sem alteracao.
  - Pure utility (private constructor), testavel sem Spring.

- **D-04 — `atualizarUltimaMensagemEm` em `@Transactional(REQUIRES_NEW)` usando native `UPDATE ... NOW()`:**
  - Transacao separada commita imediatamente, eliminando TOCTOU race com a trava 24h da Phase 4 (PITFALLS C-01).
  - Native UPDATE usa `NOW()` do banco — DB clock e fonte de verdade, nao `Instant.now()` da JVM.

- **D-05 — Parser do envelope Meta com Jackson + DTO hierarchy + tipo `desconhecido`:**
  - `WebhookPayloadParser.extrair(byte[])` retorna `ParsedWebhook(mensagens, statuses)`.
  - Tolerante a campos ausentes (entry vazio, messages ausente).
  - `tipo=desconhecido`, `conteudo=null`, `mediaId=null` para tipos novos do Meta — persiste sem erro (WEB-07).
  - `TipoMensagem` como String constants em vez de enum estrito — flexibilidade sem release.

- **D-06 — `WebhookController.POST` agora delega ao `MensagemService` sincrono:**
  - Phase 2 = sincrono (parse + idempotency + persistencia + atualizar timestamp inline). Phase 3 refatora para `@Async`.
  - Cast `request instanceof CachedBodyHttpServletRequest cached` + `cached.getCachedBody()`.
  - Statuses parseados mas IGNORADOS em Phase 2 (so log debug; entram em backlog/Phase 4).

- **D-07 — Identificacao de cliente — auto-create com `id_cliente_erp = null`:**
  - `ClienteZapService.identificar(String)` normaliza, busca, cria se nao existe.
  - Race em criar concorrente: catch `DataIntegrityViolationException` apos `save()` + re-fetch.

### Claude's Discretion

User delegou todas as 4 areas (D-01..D-07 sao defaults recomendados em modo `--auto`). Esta RESEARCH propoe os detalhes de implementacao concretos consistentes com cada decisao locked. Decisoes adicionais desta RESEARCH (sem aprovacao previa, mas reversiveis):

- Estrutura do parser Meta em DTOs Jackson de classe (NAO `record`) por compatibilidade ampla — A8 abaixo justifica.
- Tratamento de erro de parsing no controller: capturar e retornar 200 (ack-first defensivo, alinhado com Phase 3 async). Ver Secao 10.
- `equals/hashCode` minimos via `id` em `ClienteZap` e `MensagemLog`; nao implementar `Persistable` por ora.
- Migrations V5+ NAO sao adicionadas em Phase 2 — esquema atual de V1-V4 e suficiente.

### Deferred Ideas (OUT OF SCOPE)

- `@Async` boundary apos ack 200 — Phase 3 (ROU-01).
- `ErpCallbackClient`, `MessageRouter` — Phase 3 (ROU-02..05).
- Download de media entrante — Phase 3 (ROU-05).
- `WhatsAppCloudClient`, envio outbound — Phase 4.
- `WindowEnforcementService` 24h — Phase 4 (consome `ultima_mensagem_em` populada aqui).
- `MediaCacheService` (sha256 → media_id, TTL 30d) — Phase 4 (consume `MediaCache` entity criada aqui).
- Persistencia de statuses Meta (`sent/delivered/read/failed`) — backlog/Phase 4 quando outbound chegar.
- Reconciliation job para `id_cliente_erp = null` — fora desta milestone.
- Bidirectional phone matching — overhead minimo evitado por armazenar normalizado.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| WEB-05 | Idempotencia fast-path por `wamid` em `IdempotencyService` — se ja visto recentemente, responde 200 sem reprocessar | Secao 6 — `IdempotencyService.tentarPersistir(...)` retorna `boolean novo`; orquestrador ignora `false` silenciosamente |
| WEB-06 | Idempotencia hard-guard por `UNIQUE wamid` em `mensagens_log` — `DataIntegrityViolationException` em duplicate e silenciada | Secao 4 — `inserirSeNovo` native + Secao 6 — exception nunca emerge porque `ON CONFLICT DO NOTHING` substitui pela linha ja existente sem erro |
| WEB-07 | Parser do payload Meta entende: text, button_reply, list_reply, document, statuses; desconhecidos persistem com `tipo=desconhecido` sem erro | Secao 8 — `WebhookPayloadParser` com fixtures por tipo + caso `desconhecido` |
| PER-02 | Migration `V1__clientes_zap.sql` ja aplicada (Phase 1) — Phase 2 mapeia `@Entity ClienteZap` | Secao 3.1 — entity batte com schema deployado |
| PER-03 | Migration `V2__mensagens_log.sql` ja aplicada — Phase 2 mapeia `@Entity MensagemLog` com enum `Direcao{in,out}` casando o CHECK constraint | Secao 3.2 — `@Enumerated(STRING)` + lowercase enum constants |
| PER-04 | Migration `V3__media_cache.sql` ja aplicada — Phase 2 mapeia `@Entity MediaCache` com PK String `arquivoHash` | Secao 3.3 — `@Id String arquivoHash` (CHAR(64)) |
| PER-05 | Normalizacao telefone BR — DDDs fora SP/RJ/ES sem 9o digito; aplicada antes de gravar/buscar | Secao 5 — `TelefoneBR.normalizar(String)` + 12+ test cases |
| PER-06 | `ClienteZapService.identificar(telefone)` cria com `id_cliente_erp=null` se nao existe | Secao 7 — auto-create com try/catch DataIntegrityViolationException |
| PER-07 | Atualizacao de `ultima_mensagem_em` em transacao separada (`REQUIRES_NEW`) usando relogio do banco (`NOW()`) | Secao 7 — `@Transactional(propagation = REQUIRES_NEW)` + native UPDATE |

</phase_requirements>

---

## Summary

Phase 2 transforma o stub `POST /webhook/whatsapp` da Phase 1 em um pipeline de persistencia idempotente: 3 entidades JPA mapeiam o esquema ja deployado (V1-V4 da Phase 1), a parsing Jackson extrai as mensagens entrantes do envelope Meta, a idempotencia atomica via `INSERT ... ON CONFLICT (wamid) DO NOTHING` + row-count gate elimina a TOCTOU window de duplicatas concorrentes, a normalizacao de telefone brasileiro garante que os DDDs SC/RS/PR/MG sao registrados sem o 9o digito (sob pena de error 131026 silencioso da Cloud API em Phase 4), e a atualizacao de `ultima_mensagem_em` em `REQUIRES_NEW` com `NOW()` do banco prepara a 2a linha de defesa de custo zero da Phase 4.

A Phase 2 mantem o pipeline **sincrono** — Phase 3 vai refatorar para `@Async` aos POST 200 ms apos o ack. Manter sincrono em Phase 2 e deliberado: os 5 success criteria do ROADMAP sao observaveis via query JDBC depois do return 200, evitando race no test e mantendo blast radius pequeno.

**Primary recommendation:** Seguir a ordem das 7 Waves (Secao 1) com **Wave 1 SPIKE** que valida `ON CONFLICT (wamid) DO NOTHING` em H2 PG-mode (5 min de trabalho) **antes** de comprometer 6 services com a sintaxe. Se o spike falhar, fallback documentado (Secao 2.4) e catch `DataIntegrityViolationException` apos `save()`. Testes de cada Wave usam o profile `test` ja configurado pela Phase 1 (H2 in-memory MODE=PostgreSQL, schema `whatsapp`, V1-V4 aplicadas no boot).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Persistencia (entities) | Database / Storage (PostgreSQL/H2) | API / Backend (JPA model) | Schema vem do Flyway (Phase 1); entities apenas mapeiam |
| Idempotencia atomica | Database / Storage (UNIQUE + ON CONFLICT) | API / Backend (row-count gate) | DB e fonte de verdade; aplicacao apenas le rowcount |
| Normalizacao telefone | API / Backend (utility puro) | — | Pure function — sem dependencia de DB ou Spring |
| Parsing webhook Meta | API / Backend (service Jackson) | — | Bytes → DTOs; extracao baseada no envelope |
| `ultima_mensagem_em` UPDATE | Database / Storage (`NOW()` do banco) | API / Backend (`REQUIRES_NEW`) | DB clock = fonte de verdade; transacao separada commita imediato |
| Auto-create cliente | API / Backend (service) | Database / Storage (UNIQUE telefone) | Service tenta criar; UNIQUE constraint resolve race |
| Orquestracao do webhook | API / Backend (`MensagemService`) | — | Sincrono em Phase 2; vira `@Async` em Phase 3 |

---

## 1. Implementation Strategy Overview (7 Waves)

Phase 2 e construida em **7 waves serializadas** mantendo `mvnw verify -pl api-whatsapp` verde a cada commit. Cada wave gera 1+ commit cohesivo:

1. **Wave 1 — SPIKE + Entities + Repositories esqueleto.** O **spike** (5 min, Secao 2) valida em H2 PG-mode que `INSERT INTO whatsapp.clientes_zap (telefone) VALUES (?) ON CONFLICT (telefone) DO NOTHING` retorna 1 e que o segundo `ON CONFLICT` retorna 0 — gate atomico funcionando. Se passar, 3 entities + 3 repositories esqueleto comprometem o padrao. Test de boot do `SpringBootTest` confirma que Hibernate `validate` aceita as entities contra o schema da Phase 1.

2. **Wave 2 — `TelefoneBR` utility + tests.** Pure utility (sem Spring), 12+ test cases cobrindo SC/SP/RJ/ES/MG/RS/PR + edge cases (null, empty, formatado, ja-sem-9, nao-Brasil). Independente das outras waves — pode ate ser desenvolvida em paralelo, mas vem antes de Wave 3 porque `ClienteZapService` depende.

3. **Wave 3 — `MensagemLogRepository.inserirSeNovo` native + `IdempotencyService` + tests.** Implementa o native query com a sintaxe validada na Wave 1. `IdempotencyService.tentarPersistir(...)` retorna `boolean novo`. Tests cobrem (a) primeira insercao, (b) duplicata, (c) wamid diferentes, (d) **concorrencia** (2 threads simultaneos no mesmo wamid → exatamente 1 retorna true).

4. **Wave 4 — `ClienteZapService` + tests.** Implementa `identificar(telefone)` com auto-create + try/catch DataIntegrityViolationException, e `atualizarUltimaMensagemEm(telefone)` com `@Transactional(REQUIRES_NEW)`. Tests cobrem auto-create, idempotencia em concurrent insert, e **REQUIRES_NEW commit imediato** (verificado via 2 conexoes JDBC distintas — outer transaction nao precisa commitar).

5. **Wave 5 — DTOs + `WebhookPayloadParser` + tests com fixtures Meta reais.** DTOs Jackson tolerantes a campos ausentes. Service `WebhookPayloadParser.extrair(byte[]) -> ParsedWebhook`. Fixtures em `api-whatsapp/src/test/resources/fixtures/webhook/*.json` cobrem text, button_reply, list_reply, document, status, desconhecido, empty-entry, multiple-messages.

6. **Wave 6 — `MensagemService.processarWebhook(byte[])` orquestrador + `WebhookController.POST` atualizado.** Junta tudo: parse → para cada mensagem `idempotencyService.tentarPersistir` → se nova, `clienteZapService.identificar` + `atualizarUltimaMensagemEm`. Statuses sao parseados mas ignorados (log debug). `WebhookController.POST` faz cast `instanceof CachedBodyHttpServletRequest` e delega.

7. **Wave 7 — Integration tests E2E (5 SC observaveis).** `WebhookPersistenciaIntegrationTest` (`@SpringBootTest(MOCK)` + MockMvc + JdbcTemplate). 1 test por SC do ROADMAP + bonus por edge case interessante.

**Por que essa ordem:** Spike primeiro (5 min, alta alavancagem). Entities cedo (lock no contrato JPA). `TelefoneBR` antes de Wave 3 porque `IdempotencyService` chama o servico de telefone via `ClienteZapService` em Wave 6 — mas `MensagemLogRepository` em si nao depende. **Wave 1 → Wave 2 → Wave 3 e Wave 4 podem rodar em paralelo** apos Wave 1; Wave 5 idem; Wave 6 depende de 3+4+5; Wave 7 depende de tudo.

---

## 2. Spike: ON CONFLICT em H2 PG-mode

### 2.1 Por que o spike

Toda Phase 2 depende da sintaxe `INSERT ... ON CONFLICT (col) DO NOTHING` retornar **`int rowCount`** corretamente em H2 v2.x modo PostgreSQL. Se a sintaxe nao for aceita ou o rowcount mentir, 6 services + tests construidos em cima ficam podres. Este spike detecta o problema em 5 min antes de comprometer.

A documentacao do H2 confirma `MERGE INTO ... USING ... WHEN NOT MATCHED` mas o suporte a `ON CONFLICT` (sintaxe Postgres-native) em modo PG e a hipotese a validar — testado empiricamente por nos na Wave 4 de Phase 1 para `BIGINT GENERATED ALWAYS AS IDENTITY`. Mesmo padrao aqui.

### 2.2 Spike test (rodar em 5 min)

Criar `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/spike/OnConflictSpikeTest.java`:

```java
package br.com.erpkit.whatsapp.spike;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPIKE da Wave 1 — Phase 2: valida que H2 v2.x modo PostgreSQL aceita
 * INSERT ... ON CONFLICT (col) DO NOTHING e retorna o rowcount correto
 * (1 para insercao nova, 0 para conflito).
 *
 * <p>Se este teste passar: comprometer com `ON CONFLICT DO NOTHING` no
 * MensagemLogRepository (Wave 3) e seguro.
 *
 * <p>Se este teste falhar: aplicar fallback (Secao 2.4 do RESEARCH) — catch
 * DataIntegrityViolationException apos save() + re-fetch via findByWamid.
 *
 * <p>Mesmo padrao do spike STEP 0 da Phase 1 (BIGINT GENERATED + CHECK).
 */
@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
class OnConflictSpikeTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void on_conflict_do_nothing_em_clientes_zap_telefone() {
        // 1a insercao: deve retornar rowCount = 1
        int primeira = jdbc.update(
            "INSERT INTO whatsapp.clientes_zap (telefone) VALUES (?) " +
            "ON CONFLICT (telefone) DO NOTHING",
            "5511spike001"
        );
        assertThat(primeira).as("Primeira insercao deve afetar 1 linha").isEqualTo(1);

        // 2a insercao com mesmo telefone: deve retornar rowCount = 0 (CONFLICT, sem erro)
        int segunda = jdbc.update(
            "INSERT INTO whatsapp.clientes_zap (telefone) VALUES (?) " +
            "ON CONFLICT (telefone) DO NOTHING",
            "5511spike001"
        );
        assertThat(segunda).as("Conflict deve retornar 0 sem lancar excecao").isZero();

        // Confirmar que existe exatamente 1 linha — o cleanup do test transactional
        // do Spring fara rollback, mas dentro deste @Test o estado e observavel.
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM whatsapp.clientes_zap WHERE telefone = ?",
            Integer.class, "5511spike001"
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void on_conflict_do_nothing_em_mensagens_log_wamid() {
        // Mesmo padrao com mensagens_log.wamid (a tabela real do gate Phase 2)
        int primeira = jdbc.update(
            "INSERT INTO whatsapp.mensagens_log (wamid, telefone, direcao) " +
            "VALUES (?, ?, ?) ON CONFLICT (wamid) DO NOTHING",
            "wamid.spike.001", "5511spike002", "in"
        );
        assertThat(primeira).isEqualTo(1);

        int segunda = jdbc.update(
            "INSERT INTO whatsapp.mensagens_log (wamid, telefone, direcao) " +
            "VALUES (?, ?, ?) ON CONFLICT (wamid) DO NOTHING",
            "wamid.spike.001", "5511spike003", "in"  // telefone diferente, mesmo wamid
        );
        assertThat(segunda).isZero();

        // Confirmar 1 linha com telefone original (NAO sobrescrito — DO NOTHING preserve original)
        String telefone = jdbc.queryForObject(
            "SELECT telefone FROM whatsapp.mensagens_log WHERE wamid = ?",
            String.class, "wamid.spike.001"
        );
        assertThat(telefone).isEqualTo("5511spike002");
    }
}
```

**Como rodar:**
```bash
./mvnw test -pl api-whatsapp -Dtest=OnConflictSpikeTest
```

### 2.3 Resultado esperado

H2 v2.3.232 (Spring Boot 3.5.9 BOM, ja deployado na Phase 1) **aceita** a sintaxe `ON CONFLICT (col) DO NOTHING` quando `MODE=PostgreSQL`. Confianca: **MEDIUM-HIGH** porque (a) a sintaxe e Postgres-standard; (b) H2 v2.x adicionou suporte amplo a sintaxe PG; (c) a Wave 4 de Phase 1 ja validou `BIGINT GENERATED` + `CHECK` empiricamente. Falta validar especificamente `ON CONFLICT` — daí o spike.

Se passar: o spike test pode ficar no codigo como regression test (renomeado para `OnConflictPgModeSupportTest` em `db/`).

### 2.4 Fallback se spike falhar

Se H2 v2.x nao aceitar `ON CONFLICT`, implementar idempotencia via `save()` + `DataIntegrityViolationException`:

```java
// Fallback em MensagemLogRepository (NAO native query)
public boolean tentarPersistir(MensagemLog log) {
    try {
        save(log);
        return true;  // Inseriu nova
    } catch (DataIntegrityViolationException e) {
        return false;  // wamid duplicate — UNIQUE constraint pegou
    }
}
```

Trade-offs do fallback:
- **Custo:** uma excecao por duplicata — operacao mais cara que `ON CONFLICT DO NOTHING` mas funcional.
- **Equivalencia funcional:** ambos retornam `boolean novo` para o caller. `IdempotencyService` nao muda.
- **Mais codigo:** try/catch repetido em cada chamada. Aceitavel.
- **Tests permanecem identicos** — testam contrato `boolean novo`, nao a sintaxe SQL interna.

**Decisao da Wave 1:** comprometer com `ON CONFLICT` se o spike passar; senao, implementar fallback. **NAO** misturar — cobertura padrao em todo o codigo.

---

## 3. Entity classes — codigo completo

### 3.1 ClienteZap

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/ClienteZap.java
package br.com.erpkit.whatsapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Cliente WhatsApp — mapeia 1:1 com {@code whatsapp.clientes_zap} (V1 Phase 1).
 *
 * <p>{@code idClienteErp} e nullable: politica D-07 do CONTEXT.md cria registros
 * com {@code id_cliente_erp = null} para telefones nao mapeados ainda no ERP. Um
 * job de reconciliation (fora desta milestone) pode preencher depois.
 *
 * <p>{@code telefone} armazenado JA NORMALIZADO (D-03) — sempre UTF-8 / digitos
 * apenas, formato {@code 55<DDD><numero>}. Lookups SEMPRE via {@code TelefoneBR.normalizar}
 * antes de buscar.
 *
 * <p>{@code criadoEm} usa {@code DEFAULT NOW()} do banco — Hibernate ignora INSERT
 * (insertable=false), apenas le no SELECT (updatable=false). Garante que o relogio
 * do banco e a fonte de verdade do timestamp de criacao (PITFALLS C-01).
 */
@Entity
@Table(schema = "whatsapp", name = "clientes_zap")
public class ClienteZap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_cliente_erp")
    private Long idClienteErp;

    @Column(name = "telefone", nullable = false, unique = true, length = 20)
    private String telefone;

    @Column(name = "ultima_mensagem_em")
    private Instant ultimaMensagemEm;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private Instant criadoEm;

    public ClienteZap() {
        // JPA exige construtor padrao
    }

    /** Helper para criacao manual (used em ClienteZapService.identificar). */
    public ClienteZap(String telefone, Long idClienteErp) {
        this.telefone = telefone;
        this.idClienteErp = idClienteErp;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIdClienteErp() { return idClienteErp; }
    public void setIdClienteErp(Long idClienteErp) { this.idClienteErp = idClienteErp; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public Instant getUltimaMensagemEm() { return ultimaMensagemEm; }
    public void setUltimaMensagemEm(Instant ultimaMensagemEm) { this.ultimaMensagemEm = ultimaMensagemEm; }

    public Instant getCriadoEm() { return criadoEm; }
    // sem setter — campo gerenciado pelo banco

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClienteZap that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        // NAO expor telefone completo em toString (defesa em profundidade — telefone e PII)
        return "ClienteZap{id=" + id
             + ", idClienteErp=" + idClienteErp
             + ", telefone=" + (telefone == null ? null : telefone.substring(0, Math.min(4, telefone.length())) + "***")
             + ", ultimaMensagemEm=" + ultimaMensagemEm + "}";
    }
}
```

### 3.2 MensagemLog + enum Direcao

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/Direcao.java
package br.com.erpkit.whatsapp.model;

/**
 * Direcao da mensagem em {@code mensagens_log}. Mapeada via {@code @Enumerated(STRING)}.
 * Constants em LOWERCASE deliberadamente — Hibernate {@code STRING} mode usa {@link Enum#name()},
 * que em Java e o exato spelling do constant. {@code Direcao.in.name()} = {@code "in"}.
 *
 * <p>O CHECK constraint na V2 migration ({@code direcao IN ('in', 'out')}) bate com
 * essa convencao. Spike STEP 0 da Phase 1 confirmou que H2 PG-mode NAO silencia o CHECK.
 *
 * <p>Identificadores em lowercase violam convencao Java (UPPER_SNAKE_CASE). Trade-off
 * deliberado: simplicidade do mapping JPA vs convencao. Documentar em CONVENTIONS.md
 * se for necessario justificar.
 */
public enum Direcao {
    in,
    out
}
```

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/MensagemLog.java
package br.com.erpkit.whatsapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Log de mensagem WhatsApp (entrante ou saida). Mapeia {@code whatsapp.mensagens_log}
 * (V2 Phase 1).
 *
 * <p>{@code wamid} e {@code UNIQUE NOT NULL} — gate atomico de idempotencia. Phase 2
 * usa {@code INSERT ... ON CONFLICT (wamid) DO NOTHING} via native query no
 * {@link br.com.erpkit.whatsapp.repository.MensagemLogRepository}, nao via {@code save()}.
 *
 * <p>{@code conteudo} e {@code @Lob} mapeado para {@code TEXT} no Postgres / CLOB no H2 —
 * suporta payloads grandes (lista interactive com descricoes, mensagem text longa).
 *
 * <p>{@code direcao} usa {@code @Enumerated(STRING)} com enum {@link Direcao} em lowercase
 * — bate com CHECK constraint {@code direcao IN ('in', 'out')}.
 */
@Entity
@Table(schema = "whatsapp", name = "mensagens_log")
public class MensagemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wamid", nullable = false, unique = true, length = 255)
    private String wamid;

    @Column(name = "telefone", nullable = false, length = 20)
    private String telefone;

    @Column(name = "direcao", nullable = false, length = 3)
    @Enumerated(EnumType.STRING)
    private Direcao direcao;

    @Column(name = "tipo", length = 50)
    private String tipo;  // String em vez de enum — flexivel para "desconhecido" e tipos novos do Meta (D-05)

    @Lob
    @Column(name = "conteudo")
    private String conteudo;

    @Column(name = "media_id", length = 255)
    private String mediaId;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private Instant criadoEm;

    public MensagemLog() {
        // JPA exige construtor padrao
    }

    public MensagemLog(String wamid, String telefone, Direcao direcao, String tipo, String conteudo, String mediaId) {
        this.wamid = wamid;
        this.telefone = telefone;
        this.direcao = direcao;
        this.tipo = tipo;
        this.conteudo = conteudo;
        this.mediaId = mediaId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getWamid() { return wamid; }
    public void setWamid(String wamid) { this.wamid = wamid; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public Direcao getDirecao() { return direcao; }
    public void setDirecao(Direcao direcao) { this.direcao = direcao; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getConteudo() { return conteudo; }
    public void setConteudo(String conteudo) { this.conteudo = conteudo; }

    public String getMediaId() { return mediaId; }
    public void setMediaId(String mediaId) { this.mediaId = mediaId; }

    public Instant getCriadoEm() { return criadoEm; }
    // sem setter — campo gerenciado pelo banco

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MensagemLog that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        // NAO expor conteudo (PII) — apenas metadados
        return "MensagemLog{id=" + id
             + ", wamid=" + wamid
             + ", direcao=" + direcao
             + ", tipo=" + tipo
             + ", mediaId=" + mediaId
             + ", criadoEm=" + criadoEm + "}";
    }
}
```

### 3.3 MediaCache

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/model/MediaCache.java
package br.com.erpkit.whatsapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Cache de {@code media_id} do Meta por sha256 do arquivo. Mapeia
 * {@code whatsapp.media_cache} (V3 Phase 1).
 *
 * <p>Phase 2 cria a entity + repository, mas o servico que popula
 * ({@code MediaCacheService}) e Phase 4. Phase 2 NAO faz INSERT em
 * {@code media_cache} — entity + repository ficam disponiveis para Phase 4 consumir
 * coesivamente.
 *
 * <p>{@code arquivoHash} e {@code CHAR(64)} (sha256 hex digest) e e a propria PK —
 * sem auto-increment.
 */
@Entity
@Table(schema = "whatsapp", name = "media_cache")
public class MediaCache {

    @Id
    @Column(name = "arquivo_hash", length = 64)
    private String arquivoHash;

    @Column(name = "media_id", nullable = false, length = 255)
    private String mediaId;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    public MediaCache() {
        // JPA exige construtor padrao
    }

    public MediaCache(String arquivoHash, String mediaId, Instant expiraEm) {
        this.arquivoHash = arquivoHash;
        this.mediaId = mediaId;
        this.expiraEm = expiraEm;
    }

    public String getArquivoHash() { return arquivoHash; }
    public void setArquivoHash(String arquivoHash) { this.arquivoHash = arquivoHash; }

    public String getMediaId() { return mediaId; }
    public void setMediaId(String mediaId) { this.mediaId = mediaId; }

    public Instant getCriadoEm() { return criadoEm; }
    // sem setter

    public Instant getExpiraEm() { return expiraEm; }
    public void setExpiraEm(Instant expiraEm) { this.expiraEm = expiraEm; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MediaCache that)) return false;
        return Objects.equals(arquivoHash, that.arquivoHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(arquivoHash);
    }

    @Override
    public String toString() {
        return "MediaCache{arquivoHash=" + arquivoHash
             + ", mediaId=" + mediaId
             + ", expiraEm=" + expiraEm + "}";
    }
}
```

---

## 4. Repository interfaces — codigo completo

### 4.1 ClienteZapRepository

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/repository/ClienteZapRepository.java
package br.com.erpkit.whatsapp.repository;

import br.com.erpkit.whatsapp.model.ClienteZap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClienteZapRepository extends JpaRepository<ClienteZap, Long> {

    /**
     * Busca por telefone JA NORMALIZADO (D-03 + caller deve passar
     * {@code TelefoneBR.normalizar(telefone)}). UNIQUE constraint garante 0 ou 1 row.
     */
    Optional<ClienteZap> findByTelefone(String telefone);

    /**
     * Atualiza {@code ultima_mensagem_em} usando o relogio do BANCO ({@code NOW()}),
     * NAO {@code Instant.now()} da JVM (PITFALLS C-01). Native query porque JPQL nao
     * suporta {@code NOW()} portavelmente.
     *
     * <p>Chamada deve estar em {@code @Transactional(REQUIRES_NEW)} — ver
     * {@link br.com.erpkit.whatsapp.service.ClienteZapService#atualizarUltimaMensagemEm(String)}.
     *
     * @return numero de linhas afetadas (0 se telefone nao existe, 1 se atualizou)
     */
    @Modifying
    @Query(value =
        "UPDATE whatsapp.clientes_zap " +
        "SET ultima_mensagem_em = NOW() " +
        "WHERE telefone = :telefone",
        nativeQuery = true)
    int atualizarUltimaMensagemEm(@Param("telefone") String telefone);
}
```

### 4.2 MensagemLogRepository

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/repository/MensagemLogRepository.java
package br.com.erpkit.whatsapp.repository;

import br.com.erpkit.whatsapp.model.MensagemLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MensagemLogRepository extends JpaRepository<MensagemLog, Long> {

    /**
     * Insere uma nova mensagem com idempotencia atomica via {@code ON CONFLICT (wamid)
     * DO NOTHING} (PITFALLS C-06 + D-02 do CONTEXT.md). Substituidas TOCTOU race com
     * SELECT-then-INSERT.
     *
     * <p><b>Sintaxe Postgres-native:</b> validada na Wave 1 SPIKE em H2 v2.x modo
     * PostgreSQL. Se o spike falhar, fallback (Secao 2.4 do RESEARCH) usa
     * {@code save()} + catch DataIntegrityViolationException.
     *
     * <p>{@code direcao} passada como String em vez de enum — native query JPA
     * traduz parameter de String diretamente para o tipo da coluna ({@code VARCHAR(3)}).
     * O caller passa {@code direcao.name()} (ex: {@code "in"}).
     *
     * @return 1 se inseriu, 0 se conflito (wamid duplicate ja existia)
     */
    @Modifying
    @Query(value =
        "INSERT INTO whatsapp.mensagens_log " +
        "    (wamid, telefone, direcao, tipo, conteudo, media_id) " +
        "VALUES (:wamid, :telefone, :direcao, :tipo, :conteudo, :mediaId) " +
        "ON CONFLICT (wamid) DO NOTHING",
        nativeQuery = true)
    int inserirSeNovo(@Param("wamid") String wamid,
                      @Param("telefone") String telefone,
                      @Param("direcao") String direcao,
                      @Param("tipo") String tipo,
                      @Param("conteudo") String conteudo,
                      @Param("mediaId") String mediaId);

    /** Helper para tests + futuras consultas (Phase 4 historico). */
    Optional<MensagemLog> findByWamid(String wamid);

    /** Helper para tests + listagem cronologica futura. */
    Page<MensagemLog> findByTelefoneOrderByCriadoEmDesc(String telefone, Pageable pageable);
}
```

### 4.3 MediaCacheRepository

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/repository/MediaCacheRepository.java
package br.com.erpkit.whatsapp.repository;

import br.com.erpkit.whatsapp.model.MediaCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface MediaCacheRepository extends JpaRepository<MediaCache, String> {

    /**
     * Busca {@code MediaCache} por hash do arquivo, somente entradas nao expiradas
     * ({@code expira_em > now}). Phase 4 ({@code MediaCacheService}) consome.
     *
     * <p>Phase 2 NAO usa este metodo — declarado apenas para Phase 4 ja achar a
     * superficie pronta. Test de Phase 2 valida que o repository carrega no contexto
     * Spring (smoke test) mas nao exercita logica.
     */
    Optional<MediaCache> findByArquivoHashAndExpiraEmAfter(String arquivoHash, Instant agora);
}
```

---

## 5. TelefoneBR utility — codigo completo + tests

### 5.1 Codigo

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/util/TelefoneBR.java
package br.com.erpkit.whatsapp.util;

import java.util.Set;

/**
 * Normalizacao de telefone brasileiro para o formato armazenado em {@code clientes_zap}
 * (D-03 do CONTEXT.md + PITFALLS C-13).
 *
 * <p><b>Regra ANATEL 2010 (resumida):</b>
 * <ul>
 *   <li>Numeros moveis adicionaram um 9o digito (numero local de 8 → 9 digitos).</li>
 *   <li>WhatsApp registrou os numeros ANTES dessa mudanca SEM o 9o digito para a maioria
 *       dos DDDs FORA de SP (11-19), RJ (21, 22, 24) e ES (27, 28).</li>
 *   <li>Enviar texto para um numero "errado" (com 9 num DDD que nao guardou 9, ou
 *       sem 9 num DDD que guardou) retorna error 131026 silenciosamente — mensagem
 *       NAO entregue, sem retry, sem feedback util do Meta.</li>
 * </ul>
 *
 * <p><b>Algoritmo:</b>
 * <ol>
 *   <li>Strip todos os nao-digitos (parenteses, espacos, hifens, plus).</li>
 *   <li>Se nao comeca com {@code "55"} ou tem comprimento fora 12-13: retorna sanitizado
 *       sem alteracao (numero nao-Brasil — preserve como veio).</li>
 *   <li>Extrai DDD = caracteres 2-3 (apos "55").</li>
 *   <li>Se DDD em {@link #DDDS_COM_NONO_DIGITO}: retorna sem mudanca (mantem 9o digito).</li>
 *   <li>Se DDD fora desse Set e o numero local tem 9 digitos comecando com 9: strip
 *       o 9 — resultado tem 8 digitos locais (formato pre-2010).</li>
 *   <li>Caso contrario (numero local ja tem 8 digitos, ou nao comeca com 9):
 *       retorna sanitizado sem mudanca.</li>
 * </ol>
 *
 * <p><b>Politica:</b> normalizar SEMPRE no INSERT em {@code clientes_zap} E em qualquer
 * lookup ({@code findByTelefone}). UNIQUE constraint funciona naturalmente porque ambos
 * caminhos passam pelo mesmo normalizador.
 *
 * <p><b>Pure utility</b> (private constructor) — testavel sem Spring.
 */
public final class TelefoneBR {

    /**
     * DDDs que mantiveram o 9o digito no WhatsApp:
     * <ul>
     *   <li>SP: 11, 12, 13, 14, 15, 16, 17, 18, 19</li>
     *   <li>RJ: 21, 22, 24</li>
     *   <li>ES: 27, 28</li>
     * </ul>
     * Todos os outros DDDs brasileiros precisam strip do 9 antes de chamar a Cloud API.
     */
    private static final Set<String> DDDS_COM_NONO_DIGITO = Set.of(
        "11", "12", "13", "14", "15", "16", "17", "18", "19",  // SP
        "21", "22", "24",                                       // RJ
        "27", "28"                                              // ES
    );

    private TelefoneBR() {
        // Pure utility — sem instancias
    }

    /**
     * Normaliza o telefone para o formato armazenado em {@code clientes_zap}.
     *
     * @param telefone numero em qualquer formato (com/sem +, paren, hifen, espaco)
     * @return numero normalizado contendo apenas digitos, ou {@code null} se input
     *         for {@code null}, ou string vazia se input nao tem nenhum digito.
     */
    public static String normalizar(String telefone) {
        if (telefone == null) {
            return null;
        }
        // Strip todos os nao-digitos: parenteses, espacos, hifens, plus, etc.
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.isEmpty()) {
            return digitos;  // string vazia se nada for digito
        }

        // Numero nao-Brasil (sem prefixo 55, ou comprimento estranho): retorna sanitizado
        // 12 digitos = 55 + DDD(2) + 8-digit local (sem 9)
        // 13 digitos = 55 + DDD(2) + 9-digit local (com 9)
        if (!digitos.startsWith("55") || digitos.length() < 12 || digitos.length() > 13) {
            return digitos;
        }

        String ddd = digitos.substring(2, 4);
        String numero = digitos.substring(4);

        if (DDDS_COM_NONO_DIGITO.contains(ddd)) {
            // SP/RJ/ES: WhatsApp tem registro com 9o digito; preservar como veio
            // (mas se vier sem 9 num desses DDDs, NAO adicionar — pode ser fixo)
            return digitos;
        }

        // Demais DDDs: strip 9o digito se presente (numero comeca com '9' E tem 9 digitos)
        if (numero.length() == 9 && numero.startsWith("9")) {
            return "55" + ddd + numero.substring(1);
        }

        // Ja sem 9o digito (8 digitos), ou numero estranho — retorna como veio
        return digitos;
    }
}
```

### 5.2 Tests (12+ casos)

```java
// api-whatsapp/src/test/java/br/com/erpkit/whatsapp/util/TelefoneBRTest.java
package br.com.erpkit.whatsapp.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelefoneBRTest {

    // ============================================================
    // Casos OUTROS DDDs (strip 9o digito) — pivot do bug 131026
    // ============================================================

    @Test
    @DisplayName("DDD 47 (SC) com 9o digito strip o 9")
    void sc_dd47_com_9() {
        assertThat(TelefoneBR.normalizar("+5547984178525")).isEqualTo("554784178525");
    }

    @Test
    @DisplayName("DDD 31 (MG) com 9o digito strip o 9")
    void mg_ddd31_com_9() {
        assertThat(TelefoneBR.normalizar("+5531987654321")).isEqualTo("553187654321");
    }

    @Test
    @DisplayName("DDD 51 (RS) com 9o digito strip o 9")
    void rs_ddd51_com_9() {
        assertThat(TelefoneBR.normalizar("+5551987654321")).isEqualTo("555187654321");
    }

    @Test
    @DisplayName("DDD 41 (PR) com 9o digito strip o 9")
    void pr_ddd41_com_9() {
        assertThat(TelefoneBR.normalizar("+5541987654321")).isEqualTo("554187654321");
    }

    // ============================================================
    // Casos SP/RJ/ES (mantem 9o digito)
    // ============================================================

    @Test
    @DisplayName("DDD 11 (SP) mantem 9o digito")
    void sp_ddd11_com_9_preserva() {
        assertThat(TelefoneBR.normalizar("+5511987654321")).isEqualTo("5511987654321");
    }

    @Test
    @DisplayName("DDD 21 (RJ) mantem 9o digito")
    void rj_ddd21_com_9_preserva() {
        assertThat(TelefoneBR.normalizar("+5521987654321")).isEqualTo("5521987654321");
    }

    @Test
    @DisplayName("DDD 24 (RJ interior) mantem 9o digito")
    void rj_ddd24_com_9_preserva() {
        assertThat(TelefoneBR.normalizar("+5524987654321")).isEqualTo("5524987654321");
    }

    @Test
    @DisplayName("DDD 27 (ES) mantem 9o digito")
    void es_ddd27_com_9_preserva() {
        assertThat(TelefoneBR.normalizar("+5527987654321")).isEqualTo("5527987654321");
    }

    @Test
    @DisplayName("DDD 19 (SP interior) mantem 9o digito")
    void sp_ddd19_com_9_preserva() {
        assertThat(TelefoneBR.normalizar("+5519987654321")).isEqualTo("5519987654321");
    }

    // ============================================================
    // Casos formato/sanitizacao
    // ============================================================

    @Test
    @DisplayName("Formato com parenteses e hifen e espaco — sanitiza e normaliza")
    void formatado_humano() {
        assertThat(TelefoneBR.normalizar("+55 (47) 98417-8525")).isEqualTo("554784178525");
    }

    @Test
    @DisplayName("Formato com so digitos sem prefixo + — normaliza igual")
    void sem_plus() {
        assertThat(TelefoneBR.normalizar("5547984178525")).isEqualTo("554784178525");
    }

    @Test
    @DisplayName("Numero ja sem 9o digito (8 digitos locais) passa direto")
    void ja_sem_nono() {
        // 12 digitos: 55 + 47 + 8 digitos = ja em formato Meta-friendly
        assertThat(TelefoneBR.normalizar("+554784178525")).isEqualTo("554784178525");
    }

    // ============================================================
    // Casos edge (null, vazio, nao-Brasil)
    // ============================================================

    @Test
    @DisplayName("null retorna null")
    void null_retorna_null() {
        assertThat(TelefoneBR.normalizar(null)).isNull();
    }

    @Test
    @DisplayName("string vazia retorna string vazia")
    void empty_retorna_empty() {
        assertThat(TelefoneBR.normalizar("")).isEmpty();
    }

    @Test
    @DisplayName("string sem digitos retorna string vazia")
    void sem_digitos_retorna_empty() {
        assertThat(TelefoneBR.normalizar("()-+ ")).isEmpty();
    }

    @Test
    @DisplayName("Numero USA (+1) — sanitiza, nao toca prefix nem strip")
    void usa_nao_brasil() {
        assertThat(TelefoneBR.normalizar("+1 (415) 555-1212")).isEqualTo("14155551212");
    }

    @Test
    @DisplayName("Numero curto (10 digitos) — preserve sanitizado, nao tenta normalizar")
    void numero_curto() {
        assertThat(TelefoneBR.normalizar("4784178525")).isEqualTo("4784178525");
    }

    // ============================================================
    // Casos exotic (DDD valido estruturalmente mas inexistente)
    // ============================================================

    @Test
    @DisplayName("DDD inexistente (99) com 9o digito — strip aplica baseado no Set, nao em validacao real de DDD")
    void ddd_inexistente_99_strip_9() {
        // Politica: a logica e baseada no Set lookup, nao em validacao do DDD existir.
        // DDD 99 nao esta no Set DDDS_COM_NONO_DIGITO → strip aplica.
        assertThat(TelefoneBR.normalizar("+5599987654321")).isEqualTo("559987654321");
    }

    @Test
    @DisplayName("Numero estranho (14 digitos) — passa sem alteracao alem de sanitizar")
    void numero_muito_longo() {
        // 14 digitos foge do {12,13} esperado → preserve sanitizado
        assertThat(TelefoneBR.normalizar("+555111987654321")).isEqualTo("555111987654321");
    }
}
```

**Total: 18 test cases.** Cobertura: 4 DDDs com strip + 5 DDDs com preserve + 3 sanitizacao + 4 edge null/empty/USA/curto + 2 exotic. Bate com PER-05 + PITFALLS C-13.

---

## 6. IdempotencyService — codigo completo + tests

### 6.1 Service

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/IdempotencyService.java
package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.model.Direcao;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Idempotencia atomica do log de mensagens (D-02 + WEB-05/WEB-06).
 *
 * <p>Wrap fino do {@link MensagemLogRepository#inserirSeNovo} — converte rowcount
 * em {@code boolean novo} para o orquestrador {@code MensagemService}.
 *
 * <p><b>Contrato:</b> retorna {@code true} se a row foi inserida (mensagem nova),
 * {@code false} se {@code wamid} duplicate (Meta reenviou). Nunca lanca excecao
 * por causa de duplicate — ON CONFLICT DO NOTHING e atomico no banco.
 *
 * <p><b>Sem persistir conteudo em log debug</b> — apenas wamid + tipo + direcao.
 * Conteudo da mensagem e PII, nao deve aparecer em log nem em INFO.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final MensagemLogRepository repository;

    public IdempotencyService(MensagemLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Tenta persistir uma mensagem entrante de forma idempotente.
     *
     * @param wamid    ID da mensagem do Meta (UNIQUE)
     * @param telefone telefone JA NORMALIZADO (caller deve passar via {@code TelefoneBR.normalizar})
     * @param direcao  in / out
     * @param tipo     "text", "interactive_button", "interactive_list", "document",
     *                 "image", "audio", "desconhecido", etc.
     * @param conteudo conteudo extraido do payload (pode ser null para tipos sem texto)
     * @param mediaId  ID do media no Meta (opcional, null se nao tem media)
     * @return {@code true} se inseriu nova row; {@code false} se duplicate
     *         (Meta reenviou — silenciar, nao reprocessar)
     */
    public boolean tentarPersistir(String wamid, String telefone, Direcao direcao,
                                    String tipo, String conteudo, String mediaId) {
        int rowCount = repository.inserirSeNovo(
            wamid, telefone, direcao.name(), tipo, conteudo, mediaId
        );
        if (rowCount == 0) {
            log.debug("Idempotencia: wamid={} ja existe — Meta reenviou, silenciado", wamid);
            return false;
        }
        log.debug("Idempotencia: wamid={} persistido (direcao={}, tipo={})", wamid, direcao, tipo);
        return true;
    }
}
```

### 6.2 Tests

```java
// api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/IdempotencyServiceTest.java
package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import br.com.erpkit.whatsapp.model.Direcao;
import br.com.erpkit.whatsapp.model.MensagemLog;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
class IdempotencyServiceTest {

    @Autowired IdempotencyService idempotency;
    @Autowired MensagemLogRepository repository;

    @Test
    @DisplayName("Inserir wamid novo retorna true")
    void inserir_primeira_vez_retorna_true() {
        boolean novo = idempotency.tentarPersistir(
            "wamid.test.001", "5511987654321", Direcao.in,
            "text", "Olá", null
        );
        assertThat(novo).isTrue();
        Optional<MensagemLog> persistido = repository.findByWamid("wamid.test.001");
        assertThat(persistido).isPresent();
        assertThat(persistido.get().getTelefone()).isEqualTo("5511987654321");
        assertThat(persistido.get().getTipo()).isEqualTo("text");
        assertThat(persistido.get().getConteudo()).isEqualTo("Olá");
    }

    @Test
    @DisplayName("Inserir mesmo wamid duas vezes — segunda retorna false")
    void inserir_segunda_vez_retorna_false() {
        idempotency.tentarPersistir("wamid.test.002", "5511111111111", Direcao.in, "text", "primeira", null);
        boolean segunda = idempotency.tentarPersistir("wamid.test.002", "5511222222222", Direcao.in, "text", "segunda", null);
        assertThat(segunda).isFalse();
        // Conteudo original preservado (DO NOTHING — nao update)
        assertThat(repository.findByWamid("wamid.test.002")).get()
            .extracting(MensagemLog::getConteudo).isEqualTo("primeira");
        assertThat(repository.findByWamid("wamid.test.002")).get()
            .extracting(MensagemLog::getTelefone).isEqualTo("5511111111111");
    }

    @Test
    @DisplayName("Wamid diferentes — ambos retornam true")
    void inserir_dois_wamid_diferentes() {
        assertThat(idempotency.tentarPersistir("wamid.test.003a", "5511333333333", Direcao.in, "text", "a", null)).isTrue();
        assertThat(idempotency.tentarPersistir("wamid.test.003b", "5511333333333", Direcao.in, "text", "b", null)).isTrue();
        assertThat(repository.findByWamid("wamid.test.003a")).isPresent();
        assertThat(repository.findByWamid("wamid.test.003b")).isPresent();
    }

    @Test
    @DisplayName("Concorrencia: 2 threads inserem mesmo wamid simultaneamente — exatamente 1 retorna true")
    void concorrencia_2_threads_mesmo_wamid() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger truthCount = new AtomicInteger(0);

            Runnable tentativa = () -> {
                try {
                    start.await();
                    if (idempotency.tentarPersistir(
                            "wamid.test.race", "5511444444444", Direcao.in, "text", "x", null)) {
                        truthCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Permitir ate UMA excecao — DataIntegrityViolation pode acontecer
                    // dependendo da implementacao. ON CONFLICT DO NOTHING NAO deveria,
                    // mas o fallback sim. Ambos os caminhos: exatamente 1 row no banco.
                }
            };

            executor.submit(tentativa);
            executor.submit(tentativa);
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            // Exatamente 1 thread deve ter retornado true
            assertThat(truthCount.get()).isEqualTo(1);
            // E exatamente 1 row no banco
            assertThat(repository.findByWamid("wamid.test.race")).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Conteudo null + mediaId null — persiste OK (caso desconhecido)")
    void desconhecido_com_nulls() {
        boolean novo = idempotency.tentarPersistir(
            "wamid.test.unknown", "5511555555555", Direcao.in,
            "desconhecido", null, null
        );
        assertThat(novo).isTrue();
        assertThat(repository.findByWamid("wamid.test.unknown")).get()
            .satisfies(m -> {
                assertThat(m.getConteudo()).isNull();
                assertThat(m.getMediaId()).isNull();
                assertThat(m.getTipo()).isEqualTo("desconhecido");
            });
    }
}
```

**Cobertura:** primeira insercao, duplicata silenciada, wamid distintos, concorrencia (2 threads), tipo desconhecido com nulls. Total: **5 tests core**, suficientes para Wave 3.

---

## 7. ClienteZapService — codigo completo + tests

### 7.1 Service

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/ClienteZapService.java
package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.model.ClienteZap;
import br.com.erpkit.whatsapp.repository.ClienteZapRepository;
import br.com.erpkit.whatsapp.util.TelefoneBR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identifica/auto-cria {@link ClienteZap} pelo telefone, e atualiza
 * {@code ultima_mensagem_em} com o relogio do banco em transacao separada
 * (PITFALLS C-01).
 *
 * <p>D-04 do CONTEXT.md: {@code atualizarUltimaMensagemEm} usa
 * {@code @Transactional(REQUIRES_NEW)} para que o UPDATE commite imediatamente apos
 * o INSERT da mensagem entrante — eliminando TOCTOU race com a trava 24h da Phase 4
 * que le {@code ultima_mensagem_em} fora da transacao do webhook.
 *
 * <p>D-07 do CONTEXT.md: {@link #identificar(String)} cria {@code id_cliente_erp = null}
 * para clientes nao mapeados no ERP, preservando o flow. Race em criacao concorrente
 * tratada via try/catch {@link DataIntegrityViolationException} + re-fetch.
 */
@Service
public class ClienteZapService {

    private static final Logger log = LoggerFactory.getLogger(ClienteZapService.class);

    private final ClienteZapRepository repository;

    public ClienteZapService(ClienteZapRepository repository) {
        this.repository = repository;
    }

    /**
     * Recupera ou cria registro de cliente WhatsApp pelo telefone.
     *
     * <p>Normaliza o telefone via {@link TelefoneBR#normalizar}, busca em
     * {@code clientes_zap}, retorna se existe, ou cria com {@code idClienteErp = null}
     * (cliente nao mapeado ainda). Race em INSERT concorrente: catch
     * {@link DataIntegrityViolationException} + re-fetch — UNIQUE constraint
     * em {@code telefone} garante consistencia.
     *
     * @param telefone numero em qualquer formato
     * @return {@link ClienteZap} existente ou recem-criado (nunca {@code null})
     */
    @Transactional
    public ClienteZap identificar(String telefone) {
        String normalizado = TelefoneBR.normalizar(telefone);
        return repository.findByTelefone(normalizado).orElseGet(() -> criarNovo(normalizado));
    }

    private ClienteZap criarNovo(String normalizado) {
        ClienteZap novo = new ClienteZap(normalizado, null);
        try {
            ClienteZap salvo = repository.save(novo);
            log.debug("ClienteZap auto-criado: telefone={} (id_cliente_erp=null)", normalizado);
            return salvo;
        } catch (DataIntegrityViolationException e) {
            // Race: outro thread criou o mesmo telefone concorrentemente. UNIQUE
            // constraint em telefone disparou. Re-fetch e devolve o registro existente.
            log.debug("Race em criar ClienteZap telefone={} — usando registro existente", normalizado);
            return repository.findByTelefone(normalizado)
                .orElseThrow(() -> new IllegalStateException(
                    "Race no INSERT em clientes_zap mas registro nao existe (impossivel)", e));
        }
    }

    /**
     * Atualiza {@code ultima_mensagem_em} para {@code NOW()} do banco em uma transacao
     * SEPARADA. Critico para a trava 24h da Phase 4 (PITFALLS C-01).
     *
     * <p>{@code REQUIRES_NEW}: suspende a transacao corrente (se houver), abre uma
     * NOVA, executa o UPDATE, comita imediatamente. Apos o retorno, qualquer leitor
     * fora da transacao chamadora ja ve o valor atualizado.
     *
     * <p>{@code NOW()} do banco (native query) e a fonte de verdade temporal — clock
     * skew JVM-DB pode ser de segundos, e perto do boundary de 24h isso vira bug
     * (envio fora da janela aceito porque JVM clock atrasou).
     *
     * @return {@code true} se atualizou alguma linha; {@code false} se telefone nao
     *         existe em {@code clientes_zap} (nao deveria acontecer se {@link #identificar}
     *         foi chamado antes — mas {@code MensagemService} chama em ordem garantida).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean atualizarUltimaMensagemEm(String telefone) {
        String normalizado = TelefoneBR.normalizar(telefone);
        int rows = repository.atualizarUltimaMensagemEm(normalizado);
        if (rows == 0) {
            log.warn("atualizarUltimaMensagemEm: telefone={} nao encontrado em clientes_zap", normalizado);
            return false;
        }
        return true;
    }
}
```

### 7.2 Tests

```java
// api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/ClienteZapServiceTest.java
package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import br.com.erpkit.whatsapp.model.ClienteZap;
import br.com.erpkit.whatsapp.repository.ClienteZapRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
class ClienteZapServiceTest {

    @Autowired ClienteZapService service;
    @Autowired ClienteZapRepository repository;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("identificar com telefone novo cria registro com id_cliente_erp=null")
    void identificar_cria_telefone_novo() {
        ClienteZap criado = service.identificar("+5511987654321");
        assertThat(criado.getId()).isNotNull();
        assertThat(criado.getTelefone()).isEqualTo("5511987654321");
        assertThat(criado.getIdClienteErp()).isNull();
    }

    @Test
    @DisplayName("identificar com telefone existente recupera mesmo registro")
    void identificar_recupera_existente() {
        ClienteZap primeiro = service.identificar("+5511111222333");
        ClienteZap segundo = service.identificar("+5511111222333");
        assertThat(segundo.getId()).isEqualTo(primeiro.getId());
    }

    @Test
    @DisplayName("identificar normaliza antes de buscar — DDD 47 SC strip 9")
    void identificar_normaliza_antes_de_buscar() {
        ClienteZap criado = service.identificar("+5547984178525");  // 13 digitos
        assertThat(criado.getTelefone()).isEqualTo("554784178525");  // 12 digitos (sem 9)

        // Lookup subsequente com mesmo input deve recuperar o mesmo registro
        ClienteZap rebusca = service.identificar("(47) 98417-8525");
        assertThat(rebusca.getId()).isEqualTo(criado.getId());
    }

    @Test
    @DisplayName("identificar normaliza — DDD 11 SP preserva 9")
    void identificar_normaliza_sp_preserva_9() {
        ClienteZap criado = service.identificar("+5511987654321");
        assertThat(criado.getTelefone()).isEqualTo("5511987654321");  // 13 digitos preservados
    }

    @Test
    @DisplayName("identificar concorrente (2 threads, mesmo telefone novo) — apenas 1 row no DB")
    void identificar_concorrente_unique() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Runnable r = () -> {
                try {
                    start.await();
                    service.identificar("+5599888777666");
                } catch (Exception ignore) {}
            };
            executor.submit(r);
            executor.submit(r);
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            // UNIQUE constraint garante exatamente 1 row
            String normalizado = "559988877766" + "6"; // 13→12: 559988877766 (sem 9 inicial 9 do local)
            // Hmm, recompute: "+5599888777666" tem 13 digitos: 55-99-988777666 → 9 digitos local começando com 9 → strip 9
            // → "55-99-88777666" = "559988777666"
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM whatsapp.clientes_zap WHERE telefone = ?",
                Integer.class, "559988777666"
            );
            assertThat(count).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("atualizarUltimaMensagemEm com REQUIRES_NEW commita imediato (visivel via 2a conexao)")
    void atualizar_em_nova_transacao_commit_imediato() {
        // Setup: criar cliente
        service.identificar("+5511777666555");

        Instant antes = Instant.now().minusSeconds(2);
        boolean atualizado = service.atualizarUltimaMensagemEm("+5511777666555");
        assertThat(atualizado).isTrue();

        // Le via JdbcTemplate (conexao de pool diferente da que o service usou)
        // — se REQUIRES_NEW funcionou, este SELECT ve o UPDATE comittado.
        Timestamp tsRaw = jdbc.queryForObject(
            "SELECT ultima_mensagem_em FROM whatsapp.clientes_zap WHERE telefone = ?",
            Timestamp.class, "5511777666555"
        );
        assertThat(tsRaw).isNotNull();
        Instant ts = tsRaw.toInstant();
        assertThat(ts).isAfter(antes);
        assertThat(ts).isBeforeOrEqualTo(Instant.now().plusSeconds(2));
    }

    @Test
    @DisplayName("atualizarUltimaMensagemEm com telefone inexistente retorna false (0 rows)")
    void atualizar_telefone_inexistente() {
        boolean atualizado = service.atualizarUltimaMensagemEm("+5511000000000");
        assertThat(atualizado).isFalse();
        // E nao criou registro
        Optional<ClienteZap> persistido = repository.findByTelefone("5511000000000");
        assertThat(persistido).isEmpty();
    }
}
```

**Cobertura:** auto-create, recovery, normalizacao SC + SP, concorrencia, REQUIRES_NEW commit imediato, telefone inexistente. **7 tests**, fechando D-04 + D-07 + PER-05 + PER-06 + PER-07.

---

## 8. WebhookPayloadParser + DTOs — codigo completo

### 8.1 DTOs Jackson (classe, nao record — ver A8 nas Assumptions)

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/WebhookPayloadDTO.java
package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Envelope raiz do webhook do Meta. Tolerante a campos extras
 * ({@code @JsonIgnoreProperties(ignoreUnknown = true)}) — Meta pode adicionar campos novos
 * sem release nosso.
 *
 * @see <a href="https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks/payload-examples">Meta payload examples</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookPayloadDTO {
    private String object;
    private List<EntryDTO> entry;

    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }
    public List<EntryDTO> getEntry() { return entry; }
    public void setEntry(List<EntryDTO> entry) { this.entry = entry; }
}
```

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/EntryDTO.java
package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EntryDTO {
    private String id;
    private List<ChangeDTO> changes;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<ChangeDTO> getChanges() { return changes; }
    public void setChanges(List<ChangeDTO> changes) { this.changes = changes; }
}
```

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ChangeDTO.java
package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChangeDTO {
    private ValueDTO value;
    private String field;

    public ValueDTO getValue() { return value; }
    public void setValue(ValueDTO value) { this.value = value; }
    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
}
```

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ValueDTO.java
package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ValueDTO {
    @JsonProperty("messaging_product") private String messagingProduct;
    private List<MessageDTO> messages;
    private List<StatusDTO> statuses;

    public String getMessagingProduct() { return messagingProduct; }
    public void setMessagingProduct(String s) { this.messagingProduct = s; }
    public List<MessageDTO> getMessages() { return messages; }
    public void setMessages(List<MessageDTO> messages) { this.messages = messages; }
    public List<StatusDTO> getStatuses() { return statuses; }
    public void setStatuses(List<StatusDTO> statuses) { this.statuses = statuses; }
}
```

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/MessageDTO.java
package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageDTO {
    private String from;
    private String id;
    private String timestamp;
    private String type;
    private TextDTO text;
    private InteractiveDTO interactive;
    private DocumentDTO document;
    private MediaDTO image;
    private MediaDTO audio;

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public TextDTO getText() { return text; }
    public void setText(TextDTO text) { this.text = text; }
    public InteractiveDTO getInteractive() { return interactive; }
    public void setInteractive(InteractiveDTO interactive) { this.interactive = interactive; }
    public DocumentDTO getDocument() { return document; }
    public void setDocument(DocumentDTO document) { this.document = document; }
    public MediaDTO getImage() { return image; }
    public void setImage(MediaDTO image) { this.image = image; }
    public MediaDTO getAudio() { return audio; }
    public void setAudio(MediaDTO audio) { this.audio = audio; }
}
```

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/TextDTO.java
package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TextDTO {
    private String body;
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
```

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/InteractiveDTO.java
package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InteractiveDTO {
    private String type;  // "button_reply" | "list_reply"
    @JsonProperty("button_reply") private ReplyDTO buttonReply;
    @JsonProperty("list_reply") private ReplyDTO listReply;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public ReplyDTO getButtonReply() { return buttonReply; }
    public void setButtonReply(ReplyDTO buttonReply) { this.buttonReply = buttonReply; }
    public ReplyDTO getListReply() { return listReply; }
    public void setListReply(ReplyDTO listReply) { this.listReply = listReply; }
}
```

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ReplyDTO.java
package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Usado para button_reply E list_reply (mesma estrutura: id + title). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReplyDTO {
    private String id;
    private String title;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
```

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/DocumentDTO.java
package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentDTO {
    private String id;
    @JsonProperty("mime_type") private String mimeType;
    private String filename;
    private String sha256;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
}
```

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/MediaDTO.java
package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Compartilhado por image e audio. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaDTO {
    private String id;
    @JsonProperty("mime_type") private String mimeType;
    private String sha256;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
}
```

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/StatusDTO.java
package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StatusDTO {
    private String id;
    private String status;
    @JsonProperty("recipient_id") private String recipientId;
    private String timestamp;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
```

### 8.2 Records de saida do parser (Java 21 — usados internamente, sem (de)serializacao Jackson)

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/MensagemEntranteDTO.java
package br.com.erpkit.whatsapp.dto;

/**
 * Mensagem ja extraida e normalizada pelo parser — pronta para
 * {@link br.com.erpkit.whatsapp.service.MensagemService} processar.
 *
 * <p>Usar {@code record} aqui e seguro porque NAO ha (de)serializacao Jackson —
 * apenas instanciacao Java por {@link br.com.erpkit.whatsapp.service.WebhookPayloadParser}.
 *
 * @param wamid    ID unico do Meta (UNIQUE em mensagens_log)
 * @param telefone telefone JA NORMALIZADO via {@link br.com.erpkit.whatsapp.util.TelefoneBR}
 * @param tipo     "text", "interactive_button", "interactive_list", "document",
 *                 "image", "audio", "desconhecido"
 * @param conteudo conteudo extraido (texto, button_reply.id+title, filename, etc.)
 *                 ou {@code null} para tipos sem texto
 * @param mediaId  ID do media no Meta para document/image/audio, ou {@code null}
 */
public record MensagemEntranteDTO(
    String wamid,
    String telefone,
    String tipo,
    String conteudo,
    String mediaId
) { }
```

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/StatusEntranteDTO.java
package br.com.erpkit.whatsapp.dto;

/**
 * Status callback do Meta (sent/delivered/read/failed) — Phase 2 parseia mas
 * NAO persiste (D-05 + D-06). Phase 4 pode adicionar.
 */
public record StatusEntranteDTO(
    String wamid,
    String status,    // "sent" | "delivered" | "read" | "failed"
    String telefone   // recipient_id (JA NORMALIZADO)
) { }
```

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ParsedWebhook.java
package br.com.erpkit.whatsapp.dto;

import java.util.List;

public record ParsedWebhook(
    List<MensagemEntranteDTO> mensagens,
    List<StatusEntranteDTO> statuses
) { }
```

### 8.3 TipoMensagem constants

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/util/TipoMensagem.java
package br.com.erpkit.whatsapp.util;

/**
 * String constants para o campo {@code tipo} de {@code mensagens_log}.
 *
 * <p>Constants em vez de enum — flexibilidade para tipos novos do Meta sem precisar
 * release. Tipos desconhecidos persistem com {@link #DESCONHECIDO} (WEB-07).
 */
public final class TipoMensagem {
    public static final String TEXT = "text";
    public static final String INTERACTIVE_BUTTON = "interactive_button";
    public static final String INTERACTIVE_LIST = "interactive_list";
    public static final String DOCUMENT = "document";
    public static final String IMAGE = "image";
    public static final String AUDIO = "audio";
    public static final String DESCONHECIDO = "desconhecido";

    private TipoMensagem() {}
}
```

### 8.4 WebhookPayloadParser

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/WebhookPayloadParser.java
package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.ChangeDTO;
import br.com.erpkit.whatsapp.dto.EntryDTO;
import br.com.erpkit.whatsapp.dto.InteractiveDTO;
import br.com.erpkit.whatsapp.dto.MensagemEntranteDTO;
import br.com.erpkit.whatsapp.dto.MessageDTO;
import br.com.erpkit.whatsapp.dto.ParsedWebhook;
import br.com.erpkit.whatsapp.dto.ReplyDTO;
import br.com.erpkit.whatsapp.dto.StatusDTO;
import br.com.erpkit.whatsapp.dto.StatusEntranteDTO;
import br.com.erpkit.whatsapp.dto.ValueDTO;
import br.com.erpkit.whatsapp.dto.WebhookPayloadDTO;
import br.com.erpkit.whatsapp.util.TelefoneBR;
import br.com.erpkit.whatsapp.util.TipoMensagem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser do envelope de webhook do Meta. Extrai {@link MensagemEntranteDTO} e
 * {@link StatusEntranteDTO} de {@code byte[] rawBody} cacheado pelo
 * {@link br.com.erpkit.whatsapp.web.CachedBodyHttpServletRequest}.
 *
 * <p>Tolerante a campos ausentes — Meta envia heartbeats, payloads sem messages,
 * sem statuses, etc. Parser nunca lanca exceção por campo ausente; apenas se o
 * JSON em si for malformado (lanca {@link IOException} embrulhada como
 * {@link RuntimeException} pelo caller).
 *
 * <p>Tipos desconhecidos (novos do Meta, ou interactive sem button/list reply):
 * {@code tipo = "desconhecido"}, {@code conteudo = null}, {@code mediaId = null}
 * (D-05 + WEB-07).
 *
 * <p>{@code from} do Meta vem ja como digito-only (formato {@code 5547999999999}),
 * mas o parser aplica {@link TelefoneBR#normalizar} antes de retornar — defesa em
 * profundidade, e custos zero.
 */
@Service
public class WebhookPayloadParser {

    private static final Logger log = LoggerFactory.getLogger(WebhookPayloadParser.class);

    private final ObjectMapper mapper;

    public WebhookPayloadParser(ObjectMapper mapper) {
        // Spring Boot ja registra um ObjectMapper default
        this.mapper = mapper;
    }

    public ParsedWebhook extrair(byte[] rawBody) throws IOException {
        WebhookPayloadDTO payload = mapper.readValue(rawBody, WebhookPayloadDTO.class);

        List<MensagemEntranteDTO> mensagens = new ArrayList<>();
        List<StatusEntranteDTO> statuses = new ArrayList<>();

        if (payload == null || payload.getEntry() == null) {
            log.debug("Webhook payload sem 'entry' — ignorando (heartbeat ou keepalive)");
            return new ParsedWebhook(mensagens, statuses);
        }

        for (EntryDTO entry : payload.getEntry()) {
            if (entry.getChanges() == null) continue;
            for (ChangeDTO change : entry.getChanges()) {
                ValueDTO value = change.getValue();
                if (value == null) continue;

                if (value.getMessages() != null) {
                    for (MessageDTO msg : value.getMessages()) {
                        mensagens.add(extrairMensagem(msg));
                    }
                }
                if (value.getStatuses() != null) {
                    for (StatusDTO st : value.getStatuses()) {
                        statuses.add(extrairStatus(st));
                    }
                }
            }
        }

        log.debug("Webhook parseado: {} mensagens, {} statuses", mensagens.size(), statuses.size());
        return new ParsedWebhook(mensagens, statuses);
    }

    private MensagemEntranteDTO extrairMensagem(MessageDTO msg) {
        String wamid = msg.getId();
        String telefone = TelefoneBR.normalizar(msg.getFrom());
        String tipo = mapTipo(msg);
        String conteudo = extrairConteudo(msg, tipo);
        String mediaId = extrairMediaId(msg);
        return new MensagemEntranteDTO(wamid, telefone, tipo, conteudo, mediaId);
    }

    /** Mapa {@code msg.type} → constants {@link TipoMensagem}. Desconhecidos viram DESCONHECIDO. */
    private String mapTipo(MessageDTO msg) {
        String t = msg.getType();
        if (t == null) return TipoMensagem.DESCONHECIDO;
        switch (t) {
            case "text":     return TipoMensagem.TEXT;
            case "document": return TipoMensagem.DOCUMENT;
            case "image":    return TipoMensagem.IMAGE;
            case "audio":    return TipoMensagem.AUDIO;
            case "interactive":
                InteractiveDTO it = msg.getInteractive();
                if (it == null || it.getType() == null) return TipoMensagem.DESCONHECIDO;
                return switch (it.getType()) {
                    case "button_reply" -> TipoMensagem.INTERACTIVE_BUTTON;
                    case "list_reply"   -> TipoMensagem.INTERACTIVE_LIST;
                    default              -> TipoMensagem.DESCONHECIDO;
                };
            default:
                log.debug("Tipo desconhecido do Meta: {} (wamid={})", t, msg.getId());
                return TipoMensagem.DESCONHECIDO;
        }
    }

    private String extrairConteudo(MessageDTO msg, String tipo) {
        return switch (tipo) {
            case TipoMensagem.TEXT ->
                msg.getText() == null ? null : msg.getText().getBody();
            case TipoMensagem.INTERACTIVE_BUTTON -> {
                ReplyDTO r = msg.getInteractive() == null ? null : msg.getInteractive().getButtonReply();
                yield r == null ? null : r.getId() + "|" + r.getTitle();
            }
            case TipoMensagem.INTERACTIVE_LIST -> {
                ReplyDTO r = msg.getInteractive() == null ? null : msg.getInteractive().getListReply();
                yield r == null ? null : r.getId() + "|" + r.getTitle();
            }
            case TipoMensagem.DOCUMENT ->
                msg.getDocument() == null ? null : msg.getDocument().getFilename();
            case TipoMensagem.IMAGE ->
                msg.getImage() == null ? null : msg.getImage().getMimeType();
            case TipoMensagem.AUDIO ->
                msg.getAudio() == null ? null : msg.getAudio().getMimeType();
            default -> null;  // desconhecido
        };
    }

    private String extrairMediaId(MessageDTO msg) {
        if (msg.getDocument() != null) return msg.getDocument().getId();
        if (msg.getImage() != null)    return msg.getImage().getId();
        if (msg.getAudio() != null)    return msg.getAudio().getId();
        return null;
    }

    private StatusEntranteDTO extrairStatus(StatusDTO st) {
        return new StatusEntranteDTO(
            st.getId(),
            st.getStatus(),
            TelefoneBR.normalizar(st.getRecipientId())
        );
    }
}
```

### 8.5 Test fixtures (8 arquivos JSON)

Localizacao: `api-whatsapp/src/test/resources/fixtures/webhook/`

`text-portugues.json`:
```json
{
  "object": "whatsapp_business_account",
  "entry": [{
    "id": "WABA-ID",
    "changes": [{
      "value": {
        "messaging_product": "whatsapp",
        "messages": [{
          "from": "5547984178525",
          "id": "wamid.HBgN.text.001",
          "timestamp": "1735689600",
          "type": "text",
          "text": { "body": "Olá, gostaria de um orçamento" }
        }]
      },
      "field": "messages"
    }]
  }]
}
```

`button-reply.json`:
```json
{
  "object": "whatsapp_business_account",
  "entry": [{
    "id": "WABA-ID",
    "changes": [{
      "value": {
        "messaging_product": "whatsapp",
        "messages": [{
          "from": "5511987654321",
          "id": "wamid.HBgN.btn.001",
          "timestamp": "1735689601",
          "type": "interactive",
          "interactive": {
            "type": "button_reply",
            "button_reply": { "id": "aprovar_1234", "title": "Aprovar" }
          }
        }]
      },
      "field": "messages"
    }]
  }]
}
```

`list-reply.json`:
```json
{
  "object": "whatsapp_business_account",
  "entry": [{
    "id": "WABA-ID",
    "changes": [{
      "value": {
        "messaging_product": "whatsapp",
        "messages": [{
          "from": "5521987654321",
          "id": "wamid.HBgN.list.001",
          "timestamp": "1735689602",
          "type": "interactive",
          "interactive": {
            "type": "list_reply",
            "list_reply": { "id": "boleto", "title": "Ver boleto" }
          }
        }]
      },
      "field": "messages"
    }]
  }]
}
```

`document-pdf.json`:
```json
{
  "object": "whatsapp_business_account",
  "entry": [{
    "id": "WABA-ID",
    "changes": [{
      "value": {
        "messaging_product": "whatsapp",
        "messages": [{
          "from": "5531987654321",
          "id": "wamid.HBgN.doc.001",
          "timestamp": "1735689603",
          "type": "document",
          "document": {
            "id": "media-id-12345",
            "mime_type": "application/pdf",
            "filename": "comprovante.pdf",
            "sha256": "abc123def456"
          }
        }]
      },
      "field": "messages"
    }]
  }]
}
```

`status-delivered.json`:
```json
{
  "object": "whatsapp_business_account",
  "entry": [{
    "id": "WABA-ID",
    "changes": [{
      "value": {
        "messaging_product": "whatsapp",
        "statuses": [{
          "id": "wamid.HBgN.status.001",
          "status": "delivered",
          "recipient_id": "5547984178525",
          "timestamp": "1735689604"
        }]
      },
      "field": "messages"
    }]
  }]
}
```

`tipo-desconhecido.json`:
```json
{
  "object": "whatsapp_business_account",
  "entry": [{
    "id": "WABA-ID",
    "changes": [{
      "value": {
        "messaging_product": "whatsapp",
        "messages": [{
          "from": "5511987654321",
          "id": "wamid.HBgN.unknown.001",
          "timestamp": "1735689605",
          "type": "ephemeral_message"
        }]
      },
      "field": "messages"
    }]
  }]
}
```

`empty-entry.json`:
```json
{
  "object": "whatsapp_business_account",
  "entry": []
}
```

`multiple-messages.json`:
```json
{
  "object": "whatsapp_business_account",
  "entry": [{
    "id": "WABA-ID",
    "changes": [{
      "value": {
        "messaging_product": "whatsapp",
        "messages": [
          {
            "from": "5547984178525", "id": "wamid.multi.001", "timestamp": "1735689610",
            "type": "text", "text": { "body": "primeira" }
          },
          {
            "from": "5547984178525", "id": "wamid.multi.002", "timestamp": "1735689611",
            "type": "text", "text": { "body": "segunda" }
          }
        ]
      },
      "field": "messages"
    }]
  }]
}
```

### 8.6 Tests do parser

```java
// api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/WebhookPayloadParserTest.java
package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.MensagemEntranteDTO;
import br.com.erpkit.whatsapp.dto.ParsedWebhook;
import br.com.erpkit.whatsapp.util.TipoMensagem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookPayloadParserTest {

    private WebhookPayloadParser parser;

    @BeforeEach
    void setUp() {
        parser = new WebhookPayloadParser(new ObjectMapper());
    }

    private byte[] fixture(String nome) throws IOException {
        try (InputStream in = new ClassPathResource("fixtures/webhook/" + nome).getInputStream()) {
            return StreamUtils.copyToByteArray(in);
        }
    }

    @Test
    @DisplayName("text portugues — extrai conteudo + telefone normalizado")
    void text_portugues() throws Exception {
        ParsedWebhook out = parser.extrair(fixture("text-portugues.json"));
        assertThat(out.mensagens()).hasSize(1);
        MensagemEntranteDTO m = out.mensagens().get(0);
        assertThat(m.wamid()).isEqualTo("wamid.HBgN.text.001");
        assertThat(m.telefone()).isEqualTo("554784178525");  // strip 9 (DDD 47)
        assertThat(m.tipo()).isEqualTo(TipoMensagem.TEXT);
        assertThat(m.conteudo()).isEqualTo("Olá, gostaria de um orçamento");
        assertThat(m.mediaId()).isNull();
    }

    @Test
    @DisplayName("button_reply — extrai id+title")
    void button_reply() throws Exception {
        ParsedWebhook out = parser.extrair(fixture("button-reply.json"));
        assertThat(out.mensagens()).hasSize(1);
        MensagemEntranteDTO m = out.mensagens().get(0);
        assertThat(m.tipo()).isEqualTo(TipoMensagem.INTERACTIVE_BUTTON);
        assertThat(m.conteudo()).isEqualTo("aprovar_1234|Aprovar");
        assertThat(m.telefone()).isEqualTo("5511987654321");  // SP — preserva 9
    }

    @Test
    @DisplayName("list_reply — extrai id+title")
    void list_reply() throws Exception {
        ParsedWebhook out = parser.extrair(fixture("list-reply.json"));
        MensagemEntranteDTO m = out.mensagens().get(0);
        assertThat(m.tipo()).isEqualTo(TipoMensagem.INTERACTIVE_LIST);
        assertThat(m.conteudo()).isEqualTo("boleto|Ver boleto");
    }

    @Test
    @DisplayName("document — extrai filename + mediaId")
    void document() throws Exception {
        ParsedWebhook out = parser.extrair(fixture("document-pdf.json"));
        MensagemEntranteDTO m = out.mensagens().get(0);
        assertThat(m.tipo()).isEqualTo(TipoMensagem.DOCUMENT);
        assertThat(m.conteudo()).isEqualTo("comprovante.pdf");
        assertThat(m.mediaId()).isEqualTo("media-id-12345");
        assertThat(m.telefone()).isEqualTo("553187654321");  // MG — strip 9
    }

    @Test
    @DisplayName("status delivered — vai pra statuses, nao mensagens")
    void status_delivered() throws Exception {
        ParsedWebhook out = parser.extrair(fixture("status-delivered.json"));
        assertThat(out.mensagens()).isEmpty();
        assertThat(out.statuses()).hasSize(1);
        assertThat(out.statuses().get(0).status()).isEqualTo("delivered");
        assertThat(out.statuses().get(0).telefone()).isEqualTo("554784178525");
    }

    @Test
    @DisplayName("tipo desconhecido — persiste com tipo=desconhecido + conteudo null")
    void tipo_desconhecido() throws Exception {
        ParsedWebhook out = parser.extrair(fixture("tipo-desconhecido.json"));
        MensagemEntranteDTO m = out.mensagens().get(0);
        assertThat(m.tipo()).isEqualTo(TipoMensagem.DESCONHECIDO);
        assertThat(m.conteudo()).isNull();
        assertThat(m.mediaId()).isNull();
    }

    @Test
    @DisplayName("empty entry — listas vazias, sem erro")
    void empty_entry() throws Exception {
        ParsedWebhook out = parser.extrair(fixture("empty-entry.json"));
        assertThat(out.mensagens()).isEmpty();
        assertThat(out.statuses()).isEmpty();
    }

    @Test
    @DisplayName("multiple messages — extrai 2 entries")
    void multiple_messages() throws Exception {
        ParsedWebhook out = parser.extrair(fixture("multiple-messages.json"));
        assertThat(out.mensagens()).hasSize(2);
        assertThat(out.mensagens()).extracting(MensagemEntranteDTO::wamid)
            .containsExactly("wamid.multi.001", "wamid.multi.002");
        assertThat(out.mensagens()).extracting(MensagemEntranteDTO::conteudo)
            .containsExactly("primeira", "segunda");
    }

    @Test
    @DisplayName("JSON malformado lanca IOException")
    void json_malformado() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> parser.extrair("{ invalid json".getBytes())
        ).isInstanceOf(IOException.class);
    }
}
```

**Cobertura: 9 tests** — 1 por fixture + 1 erro de parse. Fecha WEB-07 + D-05.

---

## 9. MensagemService orquestrador — codigo completo

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MensagemService.java
package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.dto.MensagemEntranteDTO;
import br.com.erpkit.whatsapp.dto.ParsedWebhook;
import br.com.erpkit.whatsapp.dto.StatusEntranteDTO;
import br.com.erpkit.whatsapp.model.Direcao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Orquestrador sincrono do fluxo de webhook entrante (D-06).
 *
 * <p><b>Phase 2:</b> sincrono — `processarWebhook(byte[])` parseia, persiste cada
 * mensagem nova de forma idempotente, identifica o cliente e atualiza
 * `ultima_mensagem_em` em transacao separada. Statuses sao parseados mas
 * IGNORADOS (D-06: backlog/Phase 4).
 *
 * <p><b>Phase 3:</b> esta classe sera quebrada em fast-path (parse + idempotency
 * gate) sincrono + dispatch `@Async` (identificar cliente + callback ERP).
 *
 * <p><b>Erro de parsing:</b> propaga IOException para o controller decidir
 * (D-06 da RESEARCH: controller captura e retorna 200 — defensivo, alinhado
 * com Phase 3 async).
 */
@Service
public class MensagemService {

    private static final Logger log = LoggerFactory.getLogger(MensagemService.class);

    private final WebhookPayloadParser parser;
    private final IdempotencyService idempotency;
    private final ClienteZapService clienteZap;

    public MensagemService(WebhookPayloadParser parser,
                            IdempotencyService idempotency,
                            ClienteZapService clienteZap) {
        this.parser = parser;
        this.idempotency = idempotency;
        this.clienteZap = clienteZap;
    }

    /**
     * Processa o body bruto de um webhook do Meta (apos HMAC validado pelo Filter).
     *
     * <p>Fluxo:
     * <ol>
     *   <li>Parse via {@link WebhookPayloadParser#extrair}</li>
     *   <li>Para cada mensagem: idempotency tentarPersistir → se nova, identifica
     *       cliente + atualiza ultima_mensagem_em (REQUIRES_NEW)</li>
     *   <li>Statuses: log debug e ignora (Phase 2 escopo)</li>
     * </ol>
     */
    public void processarWebhook(byte[] rawBody) throws IOException {
        ParsedWebhook parsed = parser.extrair(rawBody);
        log.info("Webhook recebido: {} mensagens, {} statuses",
                 parsed.mensagens().size(), parsed.statuses().size());

        for (MensagemEntranteDTO m : parsed.mensagens()) {
            boolean novo = idempotency.tentarPersistir(
                m.wamid(), m.telefone(), Direcao.in, m.tipo(), m.conteudo(), m.mediaId()
            );
            if (!novo) {
                // Meta reenviou — ja persistido. Sem efeito colateral.
                continue;
            }
            // Mensagem nova: identificar cliente (auto-create) + atualizar ultima_mensagem_em
            // em transacao separada (REQUIRES_NEW para commit imediato — PITFALLS C-01).
            clienteZap.identificar(m.telefone());
            clienteZap.atualizarUltimaMensagemEm(m.telefone());
        }

        // Statuses: Phase 2 nao persiste (D-06). Apenas log para visibilidade.
        for (StatusEntranteDTO s : parsed.statuses()) {
            log.debug("Status callback ignorado em Phase 2: wamid={} status={} telefone={}",
                      s.wamid(), s.status(), s.telefone());
        }
    }
}
```

**Tests** (Wave 6):

```java
// api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/MensagemServiceTest.java
package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import br.com.erpkit.whatsapp.model.ClienteZap;
import br.com.erpkit.whatsapp.model.MensagemLog;
import br.com.erpkit.whatsapp.repository.ClienteZapRepository;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
class MensagemServiceTest {

    @Autowired MensagemService service;
    @Autowired MensagemLogRepository logRepo;
    @Autowired ClienteZapRepository clienteRepo;

    private byte[] fixture(String nome) throws IOException {
        try (InputStream in = new ClassPathResource("fixtures/webhook/" + nome).getInputStream()) {
            return StreamUtils.copyToByteArray(in);
        }
    }

    @Test
    @DisplayName("Webhook text persiste 1 row em mensagens_log + cria cliente")
    void webhook_text_persiste() throws Exception {
        Instant antes = Instant.now().minusSeconds(2);
        service.processarWebhook(fixture("text-portugues.json"));

        MensagemLog persistido = logRepo.findByWamid("wamid.HBgN.text.001").orElseThrow();
        assertThat(persistido.getTelefone()).isEqualTo("554784178525");
        assertThat(persistido.getTipo()).isEqualTo("text");
        assertThat(persistido.getConteudo()).isEqualTo("Olá, gostaria de um orçamento");

        ClienteZap cliente = clienteRepo.findByTelefone("554784178525").orElseThrow();
        assertThat(cliente.getIdClienteErp()).isNull();
        assertThat(cliente.getUltimaMensagemEm()).isAfter(antes);
    }

    @Test
    @DisplayName("Webhook duplicado (mesmo wamid 2x) — apenas 1 row")
    void webhook_duplicado_apenas_1_row() throws Exception {
        service.processarWebhook(fixture("text-portugues.json"));
        service.processarWebhook(fixture("text-portugues.json"));
        // Buscar todos com esse wamid: deve ser exatamente 1
        assertThat(logRepo.findByWamid("wamid.HBgN.text.001")).isPresent();
        Long count = (Long) logRepo.count();
        // Pode haver outras rows residuais do test context — checar specific
        long countWamid = logRepo.findAll().stream()
            .filter(m -> "wamid.HBgN.text.001".equals(m.getWamid()))
            .count();
        assertThat(countWamid).isEqualTo(1);
    }

    @Test
    @DisplayName("Webhook multiple messages persiste todas")
    void webhook_multiple_persiste_todas() throws Exception {
        service.processarWebhook(fixture("multiple-messages.json"));
        assertThat(logRepo.findByWamid("wamid.multi.001")).isPresent();
        assertThat(logRepo.findByWamid("wamid.multi.002")).isPresent();
    }

    @Test
    @DisplayName("Webhook status delivered — log debug, NAO persiste em mensagens_log")
    void webhook_status_nao_persiste() throws Exception {
        service.processarWebhook(fixture("status-delivered.json"));
        // Status NAO vai para mensagens_log — D-06 ignora
        assertThat(logRepo.findByWamid("wamid.HBgN.status.001")).isEmpty();
    }
}
```

---

## 10. WebhookController.POST atualizado

### 10.1 Codigo

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WebhookController.java (Phase 2 update)
package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.service.MensagemService;
import br.com.erpkit.whatsapp.web.CachedBodyHttpServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WhatsAppProperties properties;
    private final MensagemService mensagemService;  // NOVO em Phase 2

    public WebhookController(WhatsAppProperties properties, MensagemService mensagemService) {
        this.properties = properties;
        this.mensagemService = mensagemService;
    }

    /** GET handshake do Meta — INALTERADO da Phase 1. */
    @GetMapping(value = "/whatsapp", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verificar(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {
        boolean modeOk = "subscribe".equals(mode);
        byte[] expected = properties.getVerifyToken().getBytes(StandardCharsets.UTF_8);
        byte[] received = (verifyToken == null ? new byte[0] : verifyToken.getBytes(StandardCharsets.UTF_8));
        boolean tokenOk = MessageDigest.isEqual(expected, received);
        if (modeOk && tokenOk) {
            log.info("Webhook verificado pelo Meta — hub.challenge ecoado");
            return ResponseEntity.ok(challenge);
        }
        log.warn("Verificacao do webhook rejeitada — mode={}", mode);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * POST do Meta. Phase 2 substitui o stub da Phase 1: agora delega ao
     * {@link MensagemService} (parse + idempotency + persist + atualizar
     * ultima_mensagem_em). Sincrono em Phase 2 — Phase 3 vira `@Async`.
     *
     * <p>HMAC ja validado pelo {@link br.com.erpkit.whatsapp.web.HmacSignatureFilter}.
     * Body cacheado em {@link CachedBodyHttpServletRequest}.
     *
     * <p><b>Erro handling:</b> capturar excecao de parse e logar como ERROR;
     * retornar 200 mesmo assim (alinhamento com Phase 3 async + Meta retry storm
     * defensiva — JSON malformado nao deve causar reentrega de webhook ad eternum).
     * PITFALLS C-05 (sync timeout vs Meta retry).
     */
    @PostMapping("/whatsapp")
    public ResponseEntity<Void> receber(HttpServletRequest request) {
        byte[] rawBody;
        if (request instanceof CachedBodyHttpServletRequest cached) {
            rawBody = cached.getCachedBody();
        } else {
            // Defensive fallback — nao deve acontecer porque o filter sempre embrulha
            log.warn("Request nao e CachedBodyHttpServletRequest — fallback para getInputStream");
            try {
                rawBody = request.getInputStream().readAllBytes();
            } catch (IOException e) {
                log.error("Erro lendo body do webhook (fallback)", e);
                return ResponseEntity.ok().build();  // 200 mesmo assim — Meta nao deve reenviar
            }
        }

        try {
            mensagemService.processarWebhook(rawBody);
        } catch (IOException e) {
            // JSON malformado ou estrutura inesperada. Log + 200 (NAO 500 — evitar
            // retry storm do Meta para um payload que nunca vai parsear).
            log.error("Erro parseando webhook do Meta — payload sera descartado", e);
        } catch (RuntimeException e) {
            // Erro inesperado em downstream services. Log e ack 200 mesmo assim
            // — Phase 3 sera @Async e este try/catch vai virar logger no listener.
            log.error("Erro processando webhook — descartando", e);
        }
        return ResponseEntity.ok().build();
    }
}
```

### 10.2 Por que ack-first (200 mesmo em erro)

Decisao deliberada da RESEARCH (nao locked em CONTEXT.md mas alinhada):

| Estrategia | Pro | Con | Decisao |
|-----------|-----|-----|---------|
| Propagar excecao → 500 | Erros visiveis em monitoring | Meta reenviara com exponential backoff por ate 7 dias; mesmo JSON quebrado vira retry storm | NAO |
| Capturar e retornar 200 | Alinhado com Phase 3 async (que NAO pode propagar pro Meta apos ack); evita retry storm | Erros silenciosos sem log estruturado | **SIM** |
| Capturar e retornar 400 | Meta para de retentar erros 4xx | Meta dashboard mostra "errored" — pode mascarar bugs reais | NAO |

A decisao de retornar 200 vai junto com **logging estruturado** (`log.error` com stack trace) — operador via logs identifica rapidamente o erro de parse, mas Meta NAO retenta. Phase 6 pode adicionar metric counter `whatsapp_parse_errors_total`.

---

## 11. Integration tests E2E (Wave 7)

`WebhookPersistenciaIntegrationTest` espelha o pattern da Phase 1 (`WebhookControllerIntegrationTest`): `@SpringBootTest(MOCK)` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` + helper `computeSignature` para assinar fixtures dinamicamente. Adiciona `JdbcTemplate` para queries de verificacao pos-200.

```java
// api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookPersistenciaIntegrationTest.java
package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StreamUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = WhatsAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookPersistenciaIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private WhatsAppProperties properties;
    @Autowired private JdbcTemplate jdbc;

    private static String computeSignature(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    private byte[] fixture(String nome) throws Exception {
        try (InputStream in = new ClassPathResource("fixtures/webhook/" + nome).getInputStream()) {
            return StreamUtils.copyToByteArray(in);
        }
    }

    private void postFixture(String nome) throws Exception {
        byte[] body = fixture(nome);
        String sig = computeSignature(body, properties.getAppSecret());
        mockMvc.perform(post("/webhook/whatsapp")
                .header("X-Hub-Signature-256", sig)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
    }

    // ===========================================================
    // SC-1: idempotency — 2 POST same wamid = 1 row
    // ===========================================================
    @Test
    @DisplayName("SC-1 — POST mesmo wamid 2x persiste exatamente 1 row")
    void sc1_idempotency() throws Exception {
        postFixture("text-portugues.json");
        postFixture("text-portugues.json");
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM whatsapp.mensagens_log WHERE wamid = ?",
            Integer.class, "wamid.HBgN.text.001"
        );
        assertThat(count).isEqualTo(1);
    }

    // ===========================================================
    // SC-2: parser todos os tipos
    // ===========================================================
    @Test
    @DisplayName("SC-2a — text persistido com tipo='text' e conteudo correto")
    void sc2a_text() throws Exception {
        postFixture("text-portugues.json");
        String tipo = jdbc.queryForObject(
            "SELECT tipo FROM whatsapp.mensagens_log WHERE wamid = ?",
            String.class, "wamid.HBgN.text.001"
        );
        String conteudo = jdbc.queryForObject(
            "SELECT conteudo FROM whatsapp.mensagens_log WHERE wamid = ?",
            String.class, "wamid.HBgN.text.001"
        );
        assertThat(tipo).isEqualTo("text");
        assertThat(conteudo).isEqualTo("Olá, gostaria de um orçamento");
    }

    @Test
    @DisplayName("SC-2b — button_reply persistido com tipo='interactive_button'")
    void sc2b_button() throws Exception {
        postFixture("button-reply.json");
        String tipo = jdbc.queryForObject(
            "SELECT tipo FROM whatsapp.mensagens_log WHERE wamid = ?",
            String.class, "wamid.HBgN.btn.001"
        );
        assertThat(tipo).isEqualTo("interactive_button");
    }

    @Test
    @DisplayName("SC-2c — list_reply persistido com tipo='interactive_list'")
    void sc2c_list() throws Exception {
        postFixture("list-reply.json");
        String tipo = jdbc.queryForObject(
            "SELECT tipo FROM whatsapp.mensagens_log WHERE wamid = ?",
            String.class, "wamid.HBgN.list.001"
        );
        assertThat(tipo).isEqualTo("interactive_list");
    }

    @Test
    @DisplayName("SC-2d — document persistido com tipo='document' + media_id")
    void sc2d_document() throws Exception {
        postFixture("document-pdf.json");
        String mediaId = jdbc.queryForObject(
            "SELECT media_id FROM whatsapp.mensagens_log WHERE wamid = ?",
            String.class, "wamid.HBgN.doc.001"
        );
        assertThat(mediaId).isEqualTo("media-id-12345");
    }

    @Test
    @DisplayName("SC-2e — tipo desconhecido persistido com tipo='desconhecido' sem erro")
    void sc2e_desconhecido() throws Exception {
        postFixture("tipo-desconhecido.json");
        String tipo = jdbc.queryForObject(
            "SELECT tipo FROM whatsapp.mensagens_log WHERE wamid = ?",
            String.class, "wamid.HBgN.unknown.001"
        );
        assertThat(tipo).isEqualTo("desconhecido");
    }

    @Test
    @DisplayName("SC-2f — status NAO persistido em mensagens_log")
    void sc2f_status_nao_persistido() throws Exception {
        postFixture("status-delivered.json");
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM whatsapp.mensagens_log WHERE wamid = ?",
            Integer.class, "wamid.HBgN.status.001"
        );
        assertThat(count).isZero();
    }

    // ===========================================================
    // SC-3: telefone normalizado
    // ===========================================================
    @Test
    @DisplayName("SC-3a — DDD 47 SC strip 9 ao persistir clientes_zap")
    void sc3a_telefone_sc_strip_9() throws Exception {
        postFixture("text-portugues.json");  // from = 5547984178525
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM whatsapp.clientes_zap WHERE telefone = ?",
            Integer.class, "554784178525"  // 12 digitos sem 9
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("SC-3b — DDD 11 SP preserva 9")
    void sc3b_telefone_sp_preserva_9() throws Exception {
        postFixture("button-reply.json");  // from = 5511987654321
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM whatsapp.clientes_zap WHERE telefone = ?",
            Integer.class, "5511987654321"  // 13 digitos com 9
        );
        assertThat(count).isEqualTo(1);
    }

    // ===========================================================
    // SC-4: auto-create id_cliente_erp = null
    // ===========================================================
    @Test
    @DisplayName("SC-4 — telefone novo cria cliente_zap com id_cliente_erp=null")
    void sc4_auto_create_id_null() throws Exception {
        postFixture("text-portugues.json");
        Long idClienteErp = jdbc.queryForObject(
            "SELECT id_cliente_erp FROM whatsapp.clientes_zap WHERE telefone = ?",
            Long.class, "554784178525"
        );
        assertThat(idClienteErp).isNull();
    }

    // ===========================================================
    // SC-5: REQUIRES_NEW + NOW() do banco
    // ===========================================================
    @Test
    @DisplayName("SC-5 — ultima_mensagem_em populado com NOW() do banco apos POST")
    void sc5_ultima_mensagem_em_populada() throws Exception {
        Instant antes = Instant.now().minusSeconds(5);
        postFixture("text-portugues.json");

        Timestamp tsRaw = jdbc.queryForObject(
            "SELECT ultima_mensagem_em FROM whatsapp.clientes_zap WHERE telefone = ?",
            Timestamp.class, "554784178525"
        );
        assertThat(tsRaw).isNotNull();
        Instant ts = tsRaw.toInstant();
        assertThat(ts).isAfter(antes);
        assertThat(ts).isBeforeOrEqualTo(Instant.now().plusSeconds(5));
    }

    // ===========================================================
    // BONUS — multiple messages
    // ===========================================================
    @Test
    @DisplayName("BONUS — multiple messages num webhook persiste todas")
    void bonus_multiple_persiste_todas() throws Exception {
        postFixture("multiple-messages.json");
        Integer multi1 = jdbc.queryForObject(
            "SELECT COUNT(*) FROM whatsapp.mensagens_log WHERE wamid = ?",
            Integer.class, "wamid.multi.001"
        );
        Integer multi2 = jdbc.queryForObject(
            "SELECT COUNT(*) FROM whatsapp.mensagens_log WHERE wamid = ?",
            Integer.class, "wamid.multi.002"
        );
        assertThat(multi1).isEqualTo(1);
        assertThat(multi2).isEqualTo(1);
    }

    // ===========================================================
    // BONUS — JSON malformado retorna 200 (ack-first defensivo)
    // ===========================================================
    @Test
    @DisplayName("BONUS — JSON malformado retorna 200 + log ERROR (sem retry Meta)")
    void bonus_malformed_returns_200() throws Exception {
        byte[] body = "{ invalid json".getBytes(StandardCharsets.UTF_8);
        String sig = computeSignature(body, properties.getAppSecret());
        mockMvc.perform(post("/webhook/whatsapp")
                .header("X-Hub-Signature-256", sig)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
    }
}
```

**Cobertura: 12 tests** — SC-1 (1), SC-2 (6 sub-cases), SC-3 (2), SC-4 (1), SC-5 (1), BONUS (2). Fecha os 5 ROADMAP success criteria + bonus de robustez.

---

## 12. Risks / Open Questions / Assumptions

| # | Confidence | Claim | Risk se errado | Mitigation |
|---|-----------|-------|----------------|-----------|
| **A1** | MEDIUM-HIGH | H2 v2.x modo PG aceita `INSERT ... ON CONFLICT (col) DO NOTHING` e retorna rowcount correto | Tudo Phase 2 quebra; rollback massivo | **Spike Wave 1** (Secao 2) valida antes de comprometer; fallback documentado (Secao 2.4) |
| **A2** | HIGH | `@Modifying @Query(nativeQuery=true)` retorna rowcount correto via `int` return type em Spring Data JPA | `inserirSeNovo` retorna 1 sempre, gate idempotente quebra | Spring docs confirmam: `@Modifying` com `int` ou `Integer` return type retorna affected rows. Fallback: voltar para `save()` + try/catch |
| **A3** | HIGH | `@Transactional(REQUIRES_NEW)` em metodo public chamado de outro service abre nova transacao via Spring AOP | UPDATE nao commita imediatamente; janela 24h vira mentira | Spring AOP cria proxy quando call vem de outro bean. Self-call (`this.atualizarUltimaMensagemEm` dentro do mesmo `ClienteZapService`) NAO ativa proxy — mas no nosso fluxo o caller e `MensagemService`, outro bean, entao OK. Test em Secao 7.2 valida via 2 conexoes JDBC distintas |
| **A4** | MEDIUM-HIGH | `criadoEm` com `insertable=false, updatable=false` + `DEFAULT NOW()` — Hibernate ignora INSERT/UPDATE mas le no SELECT | Entity refresh falha ou retorna `null` em criadoEm apos save | Hibernate doc: campos com `insertable=false, updatable=false` sao excluidos de SQL gerado, mas mapeados em SELECT. Test smoke do Wave 1 (boot da app) valida via FlywayMigrationTest+entity. Se falhar: usar `@Generated(GenerationTime.INSERT)` (depende da versao Hibernate) |
| **A5** | HIGH | Meta payloads podem ter `entry: []` (heartbeat) ou `messages` ausente | Parser NPE | DTOs com `@JsonIgnoreProperties(ignoreUnknown=true)` + null-checks no parser (`if (payload.getEntry() == null) return empty`). Fixture `empty-entry.json` testa |
| **A6** | MEDIUM | UTF-8 4-byte chars (emoji, chines) sobrevivem roundtrip Jackson → JPA → Postgres TEXT | Conteudo perdido / bytes corrompidos | PostgreSQL `TEXT` e UTF-8 nativo. H2 com `MODE=PostgreSQL` tambem. Jackson `ObjectMapper` default usa UTF-8. Confianca alta. Wave 5 pode adicionar 1 fixture com emoji para regressao se quiser |
| **A7** | HIGH | `@Enumerated(STRING)` com enum `Direcao { in, out }` (lowercase) — Hibernate persiste como `"in"`/`"out"` | CHECK constraint `direcao IN ('in','out')` falha; INSERT lança DataIntegrityViolation | Hibernate `@Enumerated(STRING)` usa `Enum.name()`, que e o exato spelling do constant Java. `Direcao.in.name() == "in"`. Wave 1 test (boot da app + 1 INSERT via Repository) valida |
| **A8** | MEDIUM | Jackson 2.x desserializa Java 21 `record` corretamente com `@JsonCreator` ou `@JsonProperty` | Parser quebra em runtime quando tentar desserializar `WebhookPayloadDTO` se for record | **Decisao deliberada:** DTOs Jackson sao **classe**, nao record (Secao 8.1). Records (`MensagemEntranteDTO`, `ParsedWebhook`) sao usados APENAS internamente pelo parser, instanciados via constructor Java — sem (de)serializacao Jackson. Elimina o risco. (Se quisesse usar record para DTOs Jackson, Jackson 2.12+ suporta, mas exige `jackson-databind` recente — Spring Boot 3.5.x tem, mas deixar de fora reduz superficie) |
| **A9** | MEDIUM | `mensagemService.processarWebhook` rodando fora de `@Transactional` — cada chamada de `idempotency.tentarPersistir` cria sua propria transacao | Performance: 1 mensagem = 3 transacoes (insert log + identificar + REQUIRES_NEW update). On-premise, baixa concorrencia, OK. | Mensurar em Phase 6 se necessario; 3 round-trips DB sao aceitaveis para webhook tipico (1 mensagem por POST) |

### Open Questions (a confirmar com Wave 1 spike + Wave 4 tests)

1. **A1 + A2 — sintaxe ON CONFLICT em H2:** spike resolve em 5 min.
2. **A4 — `criadoEm` insertable=false:** test do Wave 1 que faz 1 INSERT via repository + verifica `entity.getCriadoEm() != null` apos `flush + refresh`.
3. **REQUIRES_NEW transaction propagation com Spring Data + Hibernate:** test Wave 4 (Secao 7.2) valida empiricamente via 2 conexoes JDBC.

### Sem assumption — verificado empiricamente Phase 1

- Schema `whatsapp` deployado em H2 PG-mode com 4 tabelas (FlywayMigrationTest).
- `BIGINT GENERATED ALWAYS AS IDENTITY` + `CHECK` constraint funcionam em H2 v2.x PG-mode.
- `DataIntegrityViolationException` mapeada corretamente do `JdbcSQLIntegrityConstraintViolationException` do H2.
- `CachedBodyHttpServletRequest.getCachedBody()` retorna copia mutavel-safe.
- HMAC over UTF-8 bytes nunca convertido para String — Phase 2 NAO toca essa logica.

---

## Sources

### Files actually read (HIGH confidence)

- `.planning/phases/02-persistencia-idempotencia/02-CONTEXT.md` — D-01..D-07 locked decisions
- `.planning/phases/01-fundacao-hmac-webhook/01-CONTEXT.md` — D-01..D-06 herdadas, especialmente D-06 SQL portavel
- `.planning/phases/01-fundacao-hmac-webhook/01-RESEARCH.md` (lines 1-800) — modelo do RESEARCH.md, helper `computeSignature`, integration test pattern (`@SpringBootTest(MOCK)` + MockMvc + `@AutoConfigureMockMvc`)
- `.planning/research/PITFALLS.md` — §C-01 (TOCTOU 24h race / REQUIRES_NEW + NOW), §C-02 (HMAC body), §C-04 (charset), §C-05 (sync timeout retry storm), §C-06 (wamid concurrent / ON CONFLICT + row-count), §C-13 (BR phone normalization tabela DDDs)
- `.planning/research/ARCHITECTURE.md` — Component Responsibilities (entity defs), Data Flow steps 4-8 (persistencia + atualiza ultima_mensagem_em), Anti-Pattern 1 (sync ao Meta — Phase 3 territory)
- `.planning/REQUIREMENTS.md` — WEB-05/06/07, PER-02..07
- `.planning/ROADMAP.md` Phase 2 — 5 success criteria literais
- `api-whatsapp/src/main/resources/db/migration/V1__criar_tabela_clientes_zap.sql` (linhas 1-26)
- `api-whatsapp/src/main/resources/db/migration/V2__criar_tabela_mensagens_log.sql` (linhas 1-30)
- `api-whatsapp/src/main/resources/db/migration/V3__criar_tabela_media_cache.sql` (linhas 1-22)
- `api-whatsapp/src/main/resources/db/migration/V4__criar_tabela_estado_conversa.sql` (linhas 1-17)
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/CachedBodyHttpServletRequest.java` (linhas 1-89) — getter `getCachedBody()` retorna copy
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WebhookController.java` (linhas 1-90) — Phase 1 stub a estender
- `api-whatsapp/src/main/resources/application.yml` (linhas 1-112) — datasource + JPA + Flyway config
- `api-whatsapp/src/test/resources/application-test.yml` (linhas 1-65) — H2 PG-mode JDBC URL
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/db/FlywayMigrationTest.java` (linhas 1-159) — spike pattern + JdbcTemplate query patterns para INFORMATION_SCHEMA
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookControllerIntegrationTest.java` (linhas 1-80) — pattern para Wave 7 integration test
- `api-whatsapp/pom.xml` (linhas 1-80) — confirma `spring-boot-starter-data-jpa` + `flyway` ja no classpath, sem deps adicionais necessarias para Phase 2
- `api-email/src/main/java/br/com/erpkit/email/model/Email.java` (linhas 1-213) — modelo de `@Entity` no monorepo (sem Lombok, getters/setters explicitos)
- `api-email/src/main/java/br/com/erpkit/email/repository/EmailRepository.java` (linhas 1-24) — `JpaRepository` + `@Query` patterns
- `api-email/src/main/java/br/com/erpkit/email/service/EmailService.java` (linhas 1-100) — service injecting repo + `@Service` + Logger pattern

### External docs (HIGH confidence)

- PostgreSQL `INSERT ... ON CONFLICT` reference — https://www.postgresql.org/docs/current/sql-insert.html#SQL-ON-CONFLICT
- Spring Data JPA `@Modifying` + `@Query` reference — https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html
- Spring `@Transactional` propagation REQUIRES_NEW — https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html
- Hibernate `@Enumerated(STRING)` reference — Jakarta Persistence spec, Hibernate ORM 6.x docs
- Jackson `@JsonIgnoreProperties(ignoreUnknown=true)` — https://github.com/FasterXML/jackson-annotations/wiki

### Versions used by Spring Boot 3.5.9 BOM (verified via Phase 1 build)

- H2: 2.3.232 (`MODE=PostgreSQL` + `DATABASE_TO_UPPER=false`)
- Hibernate: 6.6.x (Spring Boot 3.5.9 BOM)
- Jackson: 2.18.x (Spring Boot 3.5.9 BOM)
- PostgreSQL JDBC driver: 42.7.x (Spring Boot 3.5.9 BOM)
- Flyway: 11.x core + 11.x flyway-database-postgresql (Spring Boot 3.5.9 BOM)
- Spring Data JPA: 3.4.x (Spring Boot 3.5.9 BOM)

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Spring Boot 3.5.9 + JPA + Jackson + H2 PG-mode estabelecidos pela Phase 1.
- Architecture: HIGH — todas as decisoes locked em CONTEXT.md; padroes do monorepo + ARCHITECTURE.md alinhados.
- Idempotency (ON CONFLICT): MEDIUM-HIGH — sintaxe Postgres-native, validada empiricamente apenas para `BIGINT GENERATED` em Phase 1; **spike Wave 1** confirma especifico de `ON CONFLICT (col) DO NOTHING`.
- REQUIRES_NEW: HIGH — Spring AOP doc + cross-bean call confirmam comportamento; Wave 4 test valida.
- Phone normalization: HIGH — algoritmo derivado direto de PITFALLS C-13 + ANATEL 2010; 18 test cases cobrem matriz.
- Parser: HIGH — Jackson tolerante; fixtures Meta reais; tipo desconhecido coberto.

**Research date:** 2026-05-05
**Valid until:** 2026-06-04 (30 days — stable Spring Boot 3.5.9 stack; revisitar se H2 ou Hibernate atualizar major version)

---

*Phase: 2-Persistencia + Idempotencia*
*Researched: 2026-05-05*
