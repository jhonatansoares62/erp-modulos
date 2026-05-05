---
phase: 02-persistencia-idempotencia
plan: 03
subsystem: api-whatsapp
tags:
  - api-whatsapp
  - idempotency
  - wamid
  - data-integrity-violation
  - fallback
  - concorrencia
  - jpa
  - service-layer

dependency_graph:
  requires:
    - "Plan 02-01 (entities + repos esqueleto + spike)"
    - "MensagemLog entity com wamid UNIQUE"
    - "MensagemLogRepository com findByWamid (Wave A)"
    - "Direcao enum lowercase (Wave A)"
    - "OnConflictSpikeTest empirico — validou fallback path"
  provides:
    - "IdempotencyService.tentarPersistir(wamid, telefone, direcao, tipo, conteudo, mediaId) -> boolean novo"
    - "Contrato estavel: true=inseriu nova row, false=duplicate (Meta reenviou) — UNIQUE wamid e o gate atomico"
    - "Implementacao via save() + catch DataIntegrityViolationException (sem ON CONFLICT)"
    - "5 unit tests em IdempotencyServiceTest cobrindo: primeira insercao, duplicata silenciada, wamids distintos, concorrencia 2 threads, tipo desconhecido com nulls"
  affects:
    - "Plan 02-06 (MensagemService orquestrador) — vai chamar tentarPersistir no loop de mensagens entrantes do parser"
    - "Plan 02-04 (ClienteZapService) — segue o mesmo padrao de fallback (race em INSERT clientes_zap.telefone UNIQUE)"
    - "Phase 3 (async + ROU-01..05) — IdempotencyService permanece sincrono, chamado dentro do @Async boundary"
    - "Phase 4 (outbound + envio) — qualquer mensagem outbound persistida via mesmo Service garante idempotencia mesmo se ERP duplicar request"

tech-stack:
  added: []
  patterns:
    - "Service layer fallback save+catch DataIntegrityViolationException (UNIQUE constraint = gate atomico portavel H2/PostgreSQL)"
    - "Constructor injection sem @Autowired (alinhado api-email/EmailService)"
    - "Logger debug sem expor PII (wamid + tipo + direcao apenas — nunca conteudo)"
    - "@SpringBootTest sem @Transactional para tests de concorrencia em threads separadas (cada save commita realmente; cleanup via wamids distintos por test)"
    - "ExecutorService(2) + CountDownLatch start gate para validar empiricamente o gate atomico em 2 threads simultaneos"

key-files:
  created:
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/IdempotencyService.java"
    - "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/IdempotencyServiceTest.java"
  modified: []

key-decisions:
  - "Caminho FALLBACK acionado: save() + catch DataIntegrityViolationException (RESEARCH §2.4) — H2 v2.3.232 PG-mode NAO suporta ON CONFLICT, decisao empirica de 02-01-SUMMARY.md gate Wave 1"
  - "MensagemLogRepository NAO modificado — findByWamid ja existia da Wave A; sem inserirSeNovo native @Query (caminho ON CONFLICT abandonado por incompatibilidade do H2)"
  - "Test de concorrencia com ExecutorService.newFixedThreadPool(2) + CountDownLatch start gate — UNIQUE constraint do banco e o gate atomico real, valida exatamente 1 row apos race"
  - "Tests sem @Transactional — em @SpringBootTest cada save() commita; wamids distintos por test (.001, .002, .003a/b, .race, .unknown) evitam contaminacao"
  - "Logger debug nunca expoe conteudo (PII) — apenas wamid + tipo + direcao (T-02-11 mitigado)"

patterns-established:
  - "Fallback save+catch: DataIntegrityViolationException e o sinal portavel de UNIQUE violation (Spring SQLExceptionTranslator); funciona identicamente em H2 PG-mode (test) e PostgreSQL real (prod)"
  - "Concurrency gate test pattern: 2 threads + CountDownLatch + AtomicInteger truthCount; assert truthCount==1 E exactly 1 row no DB — replicavel para outros services com UNIQUE constraints (ClienteZapService Plan 04)"
  - "Service constructor injection com final field; @Service na classe; sem @Transactional explicito (Spring Data JPA wrap automatico cobre o trivial)"

requirements-completed:
  - "WEB-05"  # Idempotencia fast-path por wamid em IdempotencyService
  - "WEB-06"  # Idempotencia hard-guard por UNIQUE wamid + DataIntegrityViolationException silenciada

duration: 12min
completed: 2026-05-05
---

# Phase 2 Plan 03: IdempotencyService Summary

**IdempotencyService.tentarPersistir com gate atomico via UNIQUE wamid + catch DataIntegrityViolationException — 5 tests verdes incluindo concorrencia 2 threads (truthCount==1, rows==1).**

## Performance

- **Duration:** ~12 min
- **Completed:** 2026-05-05
- **Tasks:** 2 (skipped Task 1 — repository ja tinha findByWamid da Wave A; sem inserirSeNovo por causa do fallback)
- **Files criados:** 2 (1 service + 1 test class)
- **Files modificados:** 0

## Accomplishments

- IdempotencyService publicado com contrato estavel `boolean tentarPersistir(wamid, telefone, direcao, tipo, conteudo, mediaId)` — usado por Plan 06 (MensagemService) sem precisar saber o gate interno
- Fallback save+catch DataIntegrityViolationException implementado conforme decisao empirica do spike Wave 1 (02-01-SUMMARY) — equivalencia funcional total ao caminho ON CONFLICT planejado
- Test de concorrencia com 2 threads simultaneos validou empiricamente o gate atomico: UNIQUE constraint dispara exatamente 1 vez, exatamente 1 row persistida (assertion `truthCount==1` AND `findByWamid.isPresent`)
- Logger debug sem PII em ambos os caminhos (sucesso + duplicata) — T-02-11 mitigado por design
- 79 tests api-whatsapp verdes (era 55 baseline + 19 TelefoneBRTest do Plan 02 paralelo + 5 IdempotencyService); zero regressao

## Decisao Tecnica Final

**Caminho FALLBACK acionado** — confirmado por leitura de `02-01-SUMMARY.md`:

> H2 v2.3.232 (Spring Boot 3.5.9 BOM) com `MODE=PostgreSQL` **NAO ACEITA** a sintaxe Postgres-native `INSERT ... ON CONFLICT (col) DO NOTHING`. O parser H2 levanta `JdbcSQLSyntaxErrorException [42000-232]`.

Implementacao: `repository.save(mensagem)` envolvido em try/catch de `DataIntegrityViolationException`. UNIQUE constraint `mensagens_log.wamid UNIQUE NOT NULL` (V2 migration) e o gate atomico real — Spring traduz a violacao via `SQLExceptionTranslator` para a excecao portavel.

**API publica identica** ao caminho ON CONFLICT planejado: `boolean tentarPersistir(...)` retorna true/false; downstream (Plan 06) nao percebe a diferenca.

**Equivalencia funcional confirmada empiricamente** pelo segundo teste do `OnConflictSpikeTest` (preservacao do registro original, dispatch correto da excecao). Aqui em IdempotencyServiceTest:
- `inserir_segunda_vez_retorna_false` — confirmou `original preservado` (telefone="5511111111111" da 1a, NAO sobrescrito pela 2a com "5511222222222")
- `concorrencia_2_threads_mesmo_wamid` — confirmou `truthCount==1, rows==1`

## Task Commits

| # | Task | Status | Commit |
|---|------|--------|--------|
| 1 | Adicionar findByWamid no MensagemLogRepository (caminho ON CONFLICT) ou nao tocar (caminho fallback) | DONE — fallback acionado, repository ja tinha findByWamid da Wave A, NAO modificado | (sem commit — sem mudanca) |
| 2 | Criar IdempotencyService.tentarPersistir + 5 tests | DONE — 5/5 verdes | `eaad07b` |

**Plan metadata:** (este SUMMARY) — proximo commit `docs(02): adicionar SUMMARY plan 03`

## Files Created/Modified

### Criados
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/IdempotencyService.java` — `@Service` constructor injection do `MensagemLogRepository`; metodo unico `tentarPersistir(...)` com fallback save+catch; logger debug sem PII
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/IdempotencyServiceTest.java` — `@SpringBootTest @ActiveProfiles("test")` com 5 `@Test`: `inserir_primeira_vez_retorna_true`, `inserir_segunda_vez_retorna_false`, `inserir_dois_wamid_diferentes`, `concorrencia_2_threads_mesmo_wamid`, `desconhecido_com_nulls`

### Modificados
Nenhum. `MensagemLogRepository.java` ja vinha da Wave A (Plan 02-01) com `findByWamid` e `findByTelefoneOrderByCriadoEmDesc` — fallback path nao precisa de native @Query nova.

## Test Counts

```
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.745 s -- in br.com.erpkit.whatsapp.service.IdempotencyServiceTest

api-whatsapp module total apos este plan:
[INFO] Tests run: 79, Failures: 0, Errors: 0, Skipped: 0
  - 1   WhatsAppPropertiesHappyPathTest
  - 6   WhatsAppPropertiesValidationTest
  - 1   HealthControllerTest
  - 10  WebhookControllerIntegrationTest
  - 4   WebhookControllerTest
  - 6   FlywayMigrationTest
  - 13  HmacValidatorTest
  - 5   IdempotencyServiceTest        ← NOVO neste plan
  - 2   OnConflictSpikeTest
  - 19  TelefoneBRTest                ← Plan 02-02 paralelo (visivel mas nao do meu escopo)
  - 6   CachedBodyHttpServletRequestTest
  - 6   HmacSignatureFilterTest

lib-shared:                            20 tests
api-whatsapp:                          79 tests
TOTAL run no comando verify -pl api-whatsapp -am: 99 tests, 0 failures, 0 errors

BUILD SUCCESS
```

### Detalhe do test de concorrencia (`concorrencia_2_threads_mesmo_wamid`)

```
ExecutorService.newFixedThreadPool(2) + CountDownLatch start (count=1) gate
Runnable tentativa: start.await() → idempotency.tentarPersistir("wamid.test.race", ...)
                    → if (true) truthCount.incrementAndGet()
                    → catch (Exception) { /* silently lose */ }

Submeteu 2 threads → start.countDown() libera AMBAS simultaneamente
shutdown + awaitTermination(5s)

Resultado:
  truthCount.get() == 1                         ← exatamente 1 thread venceu
  repository.findByWamid("wamid.test.race")     ← exatamente 1 row no DB

Logs do Hibernate confirmaram a excecao da thread perdedora:
  WARN o.h.engine.jdbc.spi.SqlExceptionHelper : SQL Error: 23505, SQLState: 23505
  ERROR Unique index or primary key violation: ... ('wamid.test.race')

Spring traduziu para DataIntegrityViolationException → catch interno do Service
  silenciou e retornou false. Thread vencedora retornou true e incrementou o contador.
```

## Build Status

`./mvnw verify -pl api-whatsapp -am`: **BUILD SUCCESS**, 99 tests, 0 failures, 0 errors. Build time ~30s no laptop dev.

`./mvnw test -pl api-whatsapp -Dtest=IdempotencyServiceTest -am -Dsurefire.failIfNoSpecifiedTests=false`: **BUILD SUCCESS**, 5/5 tests verdes.

## Decisions Made

Ver `key-decisions` no frontmatter. Sumario:
1. Caminho fallback (save+catch) confirmado pelo gate empirico Wave 1
2. Repository nao modificado (findByWamid ja existia)
3. Test de concorrencia com 2 threads + CountDownLatch
4. Tests sem @Transactional + wamids distintos por test
5. Logger debug sem PII

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] surefire `failIfNoSpecifiedTests` quebra build com `-Dtest=` em multi-modulo**

- **Found during:** primeira execucao de `mvnw test -pl api-whatsapp -Dtest=IdempotencyServiceTest -am`
- **Issue:** O flag `-am` faz Maven construir/testar tambem `lib-shared` (modulo dependencia). Como `-Dtest=IdempotencyServiceTest` nao matched nenhum test em `lib-shared`, o `surefire` falha com `No tests matching pattern "IdempotencyServiceTest" were executed!`
- **Fix:** Adicionar `-Dsurefire.failIfNoSpecifiedTests=false` ao comando — sinaliza ao surefire para tolerar modulos sem o test pattern. Comportamento seguro: ainda valida que os matched tests passam, so nao falha por nao-match em modulos transitivos.
- **Files modificados:** Nenhum (apenas comando de execucao)
- **Verification:** Apos a flag, build passou: `Tests run: 5, Failures: 0, Errors: 0`
- **Committed in:** N/A — apenas tecnica de execucao, nenhum codigo mudou

### Authentication Gates

Nenhuma.

### Architectural Decisions (Rule 4)

Nenhuma — caminho fallback ja era o esperado pelo Plan (CASO 2 explicito no `<interfaces>` do PLAN.md, gatilhado pelo SUMMARY 02-01).

---

**Total deviations:** 1 auto-fixed (1 blocking comando Maven)
**Impact on plan:** Nenhum em codigo. Comando de teste pode ficar como referencia para futuras execucoes paciais multi-modulo.

## Issues Encountered

Nenhum issue real. O log do Hibernate mostrou as excecoes esperadas (uma do test 002 duplicate + uma do test concorrencia race) — ambas capturadas pelo catch interno do Service, indicacao de comportamento correto, nao problema.

## User Setup Required

Nenhum. Service interno do api-whatsapp; sem novo env var, sem nova dependencia externa, sem nova UI.

## Threat Surface Scan

Nenhuma nova superficie de seguranca. Service nao expoe endpoint HTTP novo (chamado por Plan 06 via injecao); usa parametros nomeados em prepared statement (Spring Data JPA); UNIQUE constraint do DB e a defesa em profundidade contra race; logger nao expoe conteudo PII.

Threat register T-02-09..T-02-13 do PLAN coberto:
- **T-02-09 (Tampering — TOCTOU race PITFALLS C-06):** mitigado via UNIQUE constraint + DataIntegrityViolation catch — test de concorrencia validou empiricamente
- **T-02-10 (Repudiation — duplicate silenciada sem log):** accept; log debug em ambos os caminhos com wamid registra duplicates
- **T-02-11 (Information Disclosure — conteudo em log):** mitigado; log debug NUNCA inclui `conteudo`, apenas `wamid + tipo + direcao`
- **T-02-12 (DoS — wamid rate limit):** accept; cada chamada e 1 INSERT O(1) com UNIQUE; on-premise volume baixo nao e gargalo
- **T-02-13 (Tampering — SQL injection):** mitigado via Spring Data prepared statements (sem string concat)

## Threat Flags

Nenhum.

## Self-Check: PASSED

### Files criados (verificados via ls + build verde):
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/IdempotencyService.java`
- FOUND: `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/IdempotencyServiceTest.java`

### Commit hash:
- FOUND: `eaad07b` — confirmado via `git log --oneline -3`

### Build verde:
- 99 tests reator (api-whatsapp + lib-shared), 0 failures, 0 errors, BUILD SUCCESS
- 5 tests novos do plan: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`

## Concerns para Wave C / Plans posteriores

1. **Plan 02-06 (MensagemService orquestrador) usara `IdempotencyService.tentarPersistir(wamid, telefone, direcao, tipo, conteudo, mediaId)`** — API publica estavel, retorno boolean: true=continuar processamento (chamar ClienteZapService.identificar + atualizarUltimaMensagemEm), false=skip silenciosamente (Meta reenviou). Nao precisa try/catch externo.

2. **Plan 02-04 (ClienteZapService) tem race similar** em INSERT de `clientes_zap.telefone` UNIQUE — mesmo padrao de fallback save+catch DataIntegrityViolationException pode ser aplicado (CONTEXT.md D-07 ja documenta isso). Concurrency test pattern usado aqui (ExecutorService(2) + CountDownLatch + AtomicInteger) e replicavel.

3. **Phase 4 outbound** — quando MensagemService persistir mensagens de SAIDA, mesmo IdempotencyService funciona: passar `Direcao.out` em vez de `in`, mesmo wamid (Meta atribui id na resposta do POST send). Padrao consistente em ambas as direcoes.

4. **Custo do fallback vs ON CONFLICT** — operacao mais cara (excecao + rollback do INSERT) que ON CONFLICT DO NOTHING. Em on-premise volume baixo (~10-100 mensagens/dia tipico), e desprezivel. Se uma versao futura de H2 adicionar suporte a ON CONFLICT, `OnConflictSpikeTest` test 1 vai falhar — sinal pra revisitar e migrar para sintaxe direta. Refactor seria minimo: 1 metodo no repository + 1 implementacao no Service; tests do contrato nao mudam.

5. **Tests de concorrencia podem ter overhead em CI** — `@SpringBootTest` boota Spring inteiro; `awaitTermination(5, SECONDS)` permite 5s pra duas threads de teste completarem (na pratica vimos ~200ms). Se CI for muito lento, pode flake — mitigacao seria aumentar timeout ou usar `Testcontainers` em PG real (Phase 6).

6. **`@SpringBootTest` sem `@Transactional` deixa rows committed** — apos toda a suite IdempotencyServiceTest rodar, 5+1+1+1+1=9 rows ficam em `mensagens_log` (5 wamids distintos: .001, .002, .003a, .003b, .race, .unknown). H2 in-memory entre tests do mesmo SpringContext: rows persistem (DB_CLOSE_DELAY=-1). Plans 04/06 que tambem usem `@SpringBootTest` no mesmo profile NAO veem essas rows (cache de SpringContext criou um H2 separado); mas se reutilizarem o mesmo `@SpringBootTest(classes=...)` com mesma config, podem ver. Mitigacao se for problema: `@DirtiesContext` no test class (custoso) ou wamids unicos por classe de test (barato).

---
*Phase: 02-persistencia-idempotencia*
*Completed: 2026-05-05*
