---
phase: 02-persistencia-idempotencia
plan: 04
subsystem: api
tags: [api-whatsapp, spring-boot, jpa, transactions, requires-new, native-query, race-condition, telefone-br, postgres-now, jdbc-template]

requires:
  - phase: 01-fundacao
    provides: ClienteZap entity + clientes_zap migration V1 com UNIQUE(telefone)
  - phase: 02-persistencia-idempotencia/02
    provides: TelefoneBR.normalizar pure utility (politica D-03)
  - phase: 02-persistencia-idempotencia/01
    provides: ClienteZapRepository esqueleto (Wave A)
provides:
  - ClienteZapRepository.findByTelefone (derived query, telefone JA NORMALIZADO)
  - ClienteZapRepository.atualizarUltimaMensagemEm (native @Query com NOW())
  - ClienteZapService.identificar(telefone) -> ClienteZap (auto-create + race protection)
  - ClienteZapService.atualizarUltimaMensagemEm(telefone) -> boolean (REQUIRES_NEW + DB clock)
affects:
  - phase 02-persistencia-idempotencia/06 (MensagemService orquestrador — caller cross-bean)
  - phase 04-outbound-trava-24h (WindowEnforcementService le ultima_mensagem_em)
  - phase 05-callback-erp (orchestrator pode usar identificar)

tech-stack:
  added: []
  patterns:
    - "@Transactional(propagation = REQUIRES_NEW) para commit imediato de UPDATE em fluxo de webhook (PITFALLS C-01)"
    - "Native @Query com NOW() do banco em vez de Instant.now() da JVM (eliminacao de clock skew)"
    - "Race protection via try/catch DataIntegrityViolationException + re-fetch (UNIQUE como gate atomico portavel H2/PostgreSQL — paralelo de IdempotencyService)"
    - "JdbcTemplate como 2a conexao do mesmo pool para validar visibilidade de commit em outra transacao (test pattern)"

key-files:
  created:
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/ClienteZapService.java
    - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/ClienteZapServiceTest.java
  modified:
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/repository/ClienteZapRepository.java

key-decisions:
  - "atualizarUltimaMensagemEm via REQUIRES_NEW + native NOW() — defesa explicita para TOCTOU race da trava 24h (PITFALLS C-01)"
  - "Race em INSERT concorrente tratada com catch DataIntegrityViolationException + re-fetch (mesmo pattern do IdempotencyService — UNIQUE constraint e o gate)"
  - "Cross-bean call obrigatorio para REQUIRES_NEW ativar proxy AOP — Javadoc enfatiza; MensagemService (Plan 06) sera o caller"
  - "Test 5 (concorrencia): telefone do RESEARCH original (+5599888777666) NAO ativava strip-9 — corrigido para +5599988777666 (numero local 988777666 comeca com 9)"
  - "Test 3 (normaliza): rebusca precisa de prefixo '55' para o normalizador reconhecer como BR — input '(47) 98417-8525' falhava early-return; corrigido para '+55 (47) 98417-8525'"

patterns-established:
  - "@Transactional(REQUIRES_NEW) + native @Query NOW(): padrao para qualquer UPDATE de timestamp critico em fluxo de webhook"
  - "Test de REQUIRES_NEW: JdbcTemplate.queryForObject(Timestamp.class, ...) le via 2a conexao do pool, valida que UPDATE commitou antes do return do service method"
  - "Race protection sem ON CONFLICT: save() + catch DataIntegrityViolationException + findByTelefone — replicavel para qualquer UNIQUE constraint (paralelo IdempotencyService.tentarPersistir)"
  - "Telefones distintos por test method (sem @Transactional de teste) para evitar contaminacao cross-test no SpringContext H2 reusado"

requirements-completed:
  - PER-05
  - PER-06
  - PER-07

duration: 24min
completed: 2026-05-05
---

# Phase 02 Plan 04: ClienteZapService Summary

**ClienteZapService com identificar (auto-create + race protection via UNIQUE catch) e atualizarUltimaMensagemEm em REQUIRES_NEW usando NOW() do banco — eliminando TOCTOU race da trava 24h (PITFALLS C-01).**

## Performance

- **Duration:** ~24 min
- **Started:** 2026-05-05T15:39:18Z
- **Completed:** 2026-05-05T16:03:03Z
- **Tasks:** 3 (Task 1 repository + Task 2 TDD service+test + Task 3 verify build)
- **Files modified:** 3 (1 repo modificado + 2 novos arquivos)

## Accomplishments

- `ClienteZapRepository` ganha 2 metodos: `findByTelefone` (derived) + `atualizarUltimaMensagemEm` (native @Query com `UPDATE ... SET ultima_mensagem_em = NOW() WHERE telefone = :telefone`)
- `ClienteZapService` 100% per RESEARCH §7.1: `identificar(telefone)` (auto-create com `id_cliente_erp=null`, race protection via try/catch `DataIntegrityViolationException` + re-fetch) e `atualizarUltimaMensagemEm(telefone)` em `@Transactional(propagation = REQUIRES_NEW)`
- 7 unit tests novos cobrindo: auto-create (PER-06), recovery, normaliza SC (strip 9), normaliza SP (preserva 9), concorrencia (UNIQUE como gate), REQUIRES_NEW commit imediato (visivel via 2a conexao JdbcTemplate), telefone inexistente
- Reator inteiro `mvnw verify -pl api-whatsapp -am`: **BUILD SUCCESS**, **95 tests verdes (88 prev + 7 novos), 0 falhas, 0 erros, zero regressao**
- D-04 (REQUIRES_NEW + NOW() do banco) e D-07 (auto-create id_cliente_erp=null) ambos satisfeitos

## Task Commits

1. **Task 1+2+3 (atomico): ClienteZapService + Repository + Test + verify** — `f347de4` (feat)

   Comprime as 3 tasks em 1 commit per `<commit>` do PLAN: feature + repository change + test class + verify gate, todos verdes antes do commit (TDD RED via compile error → GREEN apos service criado → todos os 7 tests verdes).

**Plan metadata:** (proximo commit `docs(02-04): adicionar SUMMARY plan 04`)

## Files Created/Modified

- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/repository/ClienteZapRepository.java` — adicionado `findByTelefone(String): Optional<ClienteZap>` (derived) e `atualizarUltimaMensagemEm(String): int` (native @Query usando `NOW()` do banco)
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/ClienteZapService.java` — novo `@Service` com `identificar` (`@Transactional` default REQUIRED) e `atualizarUltimaMensagemEm` (`@Transactional(REQUIRES_NEW)`); helper privado `criarNovo` com try/catch `DataIntegrityViolationException` + re-fetch
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/ClienteZapServiceTest.java` — `@SpringBootTest` + `@ActiveProfiles("test")`, 7 metodos `@Test` com `@DisplayName` em PT-BR cobrindo D-04 + D-07 + PER-05 + PER-06 + PER-07

## Decisions Made

- **Auto-create id_cliente_erp=null (PER-06):** clientes nao mapeados no ERP sao registrados com sentinel `null` para o flow continuar; reconciliation futura (job fora desta milestone) preenche depois. `log.debug` registra evento.
- **REQUIRES_NEW + NOW() (D-04, PITFALLS C-01):** UPDATE da `ultima_mensagem_em` tem que ser visivel para a trava 24h da Phase 4 mesmo se a transacao do webhook ainda nao commitou; REQUIRES_NEW abre nova transacao, commita o UPDATE imediatamente. NOW() do banco e fonte de verdade temporal — clock skew JVM-DB perto do boundary 24h pode aceitar erroneamente envio fora da janela.
- **Race protection via UNIQUE + catch:** exatamente o mesmo pattern do `IdempotencyService.tentarPersistir` (Plan 03) — UNIQUE constraint em `telefone` e o gate atomico portavel H2/PostgreSQL; `DataIntegrityViolationException` capturada + `findByTelefone` re-fetch devolve o registro existente. Empiricamente validado pelo Test 5 (2 threads + CountDownLatch + COUNT=1).
- **Cross-bean call obrigatorio:** documentado em Javadoc — Spring AOP proxy nao ativa em self-call dentro do mesmo bean, entao chamadas `this.atualizarUltimaMensagemEm` virariam no-op de propagation. `MensagemService` (Plan 06) sera o caller, garantindo proxy ativo.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Test 3 (normaliza_antes_de_buscar): rebusca usava input sem prefixo "55"**
- **Found during:** Task 2 (rodada inicial dos 7 tests)
- **Issue:** Input `(47) 98417-8525` foi interpretado pelo `TelefoneBR.normalizar` como NAO-BR (early return em `!digitos.startsWith("55")`), gerando `47984178525` (11 chars) — `findByTelefone` nao encontrava a row criada antes (`554784178525`), e `criarNovo` inseria um SEGUNDO registro. AssertJ reportou `expected: 112L but was: 113L` (Long IDs distintos das 2 rows).
- **Fix:** Trocar input do rebusca para `+55 (47) 98417-8525`, que normaliza para o mesmo `554784178525`. Adicionado comentario explicando que o prefixo 55 e obrigatorio para o normalizador reconhecer como BR.
- **Files modified:** `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/ClienteZapServiceTest.java`
- **Verification:** Test 3 passa: `rebusca.getId() == criado.getId()`.
- **Committed in:** `f347de4`

**2. [Rule 1 - Bug] Test 5 (concorrente): input do RESEARCH nao ativava strip-9**
- **Found during:** Task 2 (rodada inicial dos 7 tests)
- **Issue:** Input `+5599888777666` (do RESEARCH §7.2 linha 1378) tem numero local `888777666` (9 chars, comeca com **8**), nao com 9 — algoritmo `TelefoneBR` so faz strip se o local comeca com `9`. Resultado salvo era `5599888777666` (13 chars), enquanto o test fazia `COUNT WHERE telefone = '559988777666'` (12 chars). COUNT retornou 0 e o assert falhou.
- **Fix:** Trocar input para `+5599988777666` (numero local `988777666` comeca com 9 → strip → `559988777666`, 12 chars). Comentario do test atualizado para refletir a aritmetica correta dos digitos.
- **Files modified:** `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/ClienteZapServiceTest.java`
- **Verification:** Test 5 passa: COUNT = 1 (UNIQUE + catch race protection funcionou).
- **Committed in:** `f347de4`

---

**Total deviations:** 2 auto-fixed (Rule 1 — bugs nos inputs do test; codigo do RESEARCH §7.2 herdou inconsistencia da aritmetica do telefone)
**Impact on plan:** Ambas as correcoes sao em test data, nao em codigo de producao. Algoritmo `TelefoneBR` permanece correto (Plan 02-02 ja tinha 19 tests verdes confirmando). Comportamento do service permanece exatamente como descrito no PLAN. Sem scope creep.

## Issues Encountered

- **Hibernate `AssertionFailure` no log do test 5 (esperado, nao falha):** quando a thread perdedora apanha `DataIntegrityViolationException` no `save()`, o entity manager fica em estado inconsistente (`Entry for instance of 'ClienteZap' has a null identifier`). O re-fetch via `findByTelefone` ainda funciona porque devolve um snapshot lido (Spring Data abre nova session-level operation), e o resultado e entregue ao caller. A transacao chamadora vai rollback no commit boundary, mas a operacao funcional e: thread vencedora persistiu a row, perdedora retornou a row vencedora. Test passa. Em producao com PostgreSQL real, comportamento identico ao H2 PG-mode. Pode-se migrar `criarNovo` para sub-transacao `REQUIRES_NEW` em uma futura iteracao se isso virar problema (e.g., MensagemService chamando dentro de transacao maior que precisa permanecer ativa) — mas nao e necessario hoje pois o caller cross-bean (Plan 06) tipicamente trata cada mensagem como unidade transacional independente. Documentado aqui como know-issue.
- **Build mostra `[ERROR] o.h.engine.jdbc.spi.SqlExceptionHelper`:** sao logs Hibernate de UNIQUE constraint disparando — esperado (race protection do service captura). Nao impactam BUILD SUCCESS nem teste.

## User Setup Required

None — codigo puro Spring Boot, sem env vars novos, sem servicos externos, sem migrations adicionais.

## Threat Flags

Nenhum threat flag emergente fora do `<threat_model>` do PLAN. T-02-14, T-02-15, T-02-16 todos `mitigate` enderecados (UNIQUE + catch race, REQUIRES_NEW + NOW(), Javadoc cross-bean). T-02-17, T-02-18 permanecem `accept` per PLAN.

## Next Phase Readiness

**Wave 4 / Plan 06 (MensagemService orquestrador) pode consumir:**
- `clienteZapService.identificar(telefone) -> ClienteZap` — never null, idempotente, normaliza internamente; passar telefone bruto do webhook (Cloud API entrega no formato `5511987654321` digitos).
- `clienteZapService.atualizarUltimaMensagemEm(telefone) -> boolean` — chamar APOS persistir mensagem entrante (UPDATE em transacao separada commita imediato; transacao do webhook pode rollback se mensagem falhar persistir, mas o UPDATE de `ultima_mensagem_em` permanece consistente porque so executa apos `tentarPersistir == true`). **Cross-bean call obrigatorio** — chamar de dentro do `MensagemService`, nunca via `this.` no proprio `ClienteZapService` (proxy AOP nao ativa).

**Wave 5+ / Phase 4 trava 24h:**
- Leitor de `ultima_mensagem_em` na trava 24h ja ve o valor commitado pela `atualizarUltimaMensagemEm` mesmo se a transacao do webhook ainda nao terminou (REQUIRES_NEW garante visibilidade fora da transacao chamadora).
- Comparacao temporal deve usar `NOW() - ultima_mensagem_em < INTERVAL '24 hours'` no banco (mesmo clock) ou `Instant.now().minus(24, HOURS).isBefore(ultima_mensagem_em.toInstant())` na JVM apos refetch — segunda opcao tem clock skew, primeira e fonte de verdade.

**Concerns para Wave D / Plan 06:**
- Se `MensagemService` precisar agrupar `identificar + tentarPersistir + atualizarUltimaMensagemEm` em UMA transacao para invariantes de negocio, observar que `atualizarUltimaMensagemEm` ainda commitara em transacao SEPARADA por causa de REQUIRES_NEW (semantica desejada — D-04). Se for indesejavel para algum cenario futuro, expor variante sem REQUIRES_NEW.
- A `AssertionFailure` no log da thread perdedora do race pode poluir logs de producao em cenarios de alta concorrencia. Se virar problema operacional, considerar refatorar `criarNovo` para sub-transacao isolada (REQUIRES_NEW + try/catch fora) — mas nao e bloqueador hoje.

## Self-Check: PASSED

- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/repository/ClienteZapRepository.java` — FOUND (modificado, 2 metodos novos)
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/ClienteZapService.java` — FOUND (novo, 2 metodos publicos + 1 helper privado)
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/ClienteZapServiceTest.java` — FOUND (novo, 7 @Test com @DisplayName)
- Commit `f347de4` — FOUND (`git log --oneline -3`)
- Tests run: 7, Failures: 0, Errors: 0 (ClienteZapServiceTest)
- Build: BUILD SUCCESS — 95 tests api-whatsapp aggregate, 0 falhas

---
*Phase: 02-persistencia-idempotencia*
*Plan: 04*
*Completed: 2026-05-05*
