---
phase: 02-persistencia-idempotencia
plan: 02
subsystem: api-whatsapp
tags:
  - api-whatsapp
  - utility
  - telefone-br
  - normalizacao
  - tdd
  - junit
  - assertj

dependency_graph:
  requires:
    - "Phase 1 baseline (api-whatsapp boot funcional)"
    - "Plan 02-01 (pacote util/ ja criado para TipoMensagem.java) — confirmado mesmo pacote"
  provides:
    - "TelefoneBR.normalizar(String) pure utility — entrada padronizada para clientes_zap"
    - "Set imutavel DDDS_COM_NONO_DIGITO com 14 DDDs literais (SP 11-19, RJ 21/22/24, ES 27/28)"
    - "Politica documentada: numeros nao-Brasil sao sanitizados sem alteracao (preserve)"
    - "Politica documentada: SP/RJ/ES sem 9o digito NAO sao normalizados (pode ser fixo)"
    - "19 test cases referenciaveis como matriz de comportamento esperado em PR reviews"
  affects:
    - "Plan 04 (ClienteZapService) — chama TelefoneBR.normalizar antes de findByTelefone/save (single source of truth)"
    - "Plan 05 (WebhookPayloadParser) — normaliza msg.from antes de retornar MensagemEntranteDTO"
    - "Plan 06 (MensagemService orquestrador) — confia que ClienteZapService entrega telefone ja normalizado"
    - "PITFALLS C-13 → bug 131026 do Meta deixa de ser silencioso na nossa pilha"

tech_stack:
  added: []  # Sem dependencias novas — usa apenas java.util.Set ja disponivel
  patterns:
    - "Pure utility com private constructor + final class — sem Spring, testavel sem context"
    - "Set.of(...) imutavel para lookup O(1) de constantes"
    - "Algoritmo branch-ordered: null check → sanitize → early return non-BR → DDD lookup → strip condicional"
    - "Tests JUnit 5 + AssertJ puros (sem @SpringBootTest) — execucao <1s para 19 cases"
    - "@DisplayName em PT-BR auto-documentando matriz de cenarios"

key_files:
  created:
    - "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/util/TelefoneBR.java"
    - "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/util/TelefoneBRTest.java"
  modified: []

decisions:
  - "Pacote util/ (mesmo de TipoMensagem da Wave A) — utilidades de dominio sem comportamento Spring ficam agrupadas; pacote service/util/ rejeitado para nao misturar com beans"
  - "Algoritmo NAO adiciona 9o digito quando vier sem em SP/RJ/ES — politica deliberada (numero pode ser fixo); test sp_ddd11_com_9_preserva valida com 9, sem teste com fixo SP intencionalmente (fora do escopo PER-05)"
  - "DDD inexistente (99) ainda passa pelo Set lookup — algoritmo e baseado em Set, nao em validacao real de DDD; documentado em test ddd_inexistente_99_strip_9 e RESEARCH risks"
  - "19 test cases (1 acima do minimo de 18) — adicionado sem_digitos_retorna_empty para cobrir edge entre null (retorna null) e empty (retorna empty)"

metrics:
  duration_seconds: 177
  duration_human: "3m"
  tasks_completed: 3
  files_created: 2
  files_modified: 0
  tests_added: 19
  total_api_whatsapp_tests: 74
  build_time_module: "1.871s (Dtest=TelefoneBRTest only) / 8.361s (verify -pl api-whatsapp)"
  completed_date: "2026-05-05"

requirements-completed:
  - PER-05  # Normalizacao telefone BR — DDDs SP/RJ/ES preservam 9o digito; demais strip
---

# Phase 2 Plan 02: TelefoneBR Utility Summary

**Pure utility `TelefoneBR.normalizar(String)` com 14-DDD set lookup (SP/RJ/ES preservam 9o digito; 80+ outros DDDs strip) coberto por 19 test cases JUnit puros — fecha o vetor do bug 131026 silencioso do Meta documentado em PITFALLS C-13.**

## Performance

- **Duration:** 3 min (177s)
- **Started:** 2026-05-05T15:24:24Z
- **Completed:** 2026-05-05T15:27:21Z
- **Tasks:** 3 (RED → GREEN → verify)
- **Files created:** 2
- **Files modified:** 0

## Accomplishments

- **TelefoneBR.normalizar(String)** implementado conforme algoritmo do RESEARCH §5.1 — branch-ordered (null → sanitize → non-BR early return → DDD set lookup → strip condicional)
- **14 DDDs do Set DDDS_COM_NONO_DIGITO** (SP 11-19, RJ 21/22/24, ES 27/28) batem com a regra ANATEL 2010 + politica historica do WhatsApp documentada em PITFALLS C-13
- **19 test cases** cobrem matriz completa: 4 strip (SC/MG/RS/PR) + 5 preserve (SP/RJ/ES) + 3 sanitizacao (formatado humano, sem +, ja sem 9) + 5 edge (null/empty/sem-digitos/USA/curto) + 2 exotic (DDD 99 inexistente, 14 digitos)
- **Tempo de execucao dos 19 tests: 0.114s** (sem Spring context, JUnit 5 + AssertJ puro) — confirma decisao Wave B de rodar paralelo aos demais waves sem overhead
- **Build agregado api-whatsapp verde** com 74 tests (55 baseline + 19 novos): 0 falhas, 0 erros, BUILD SUCCESS em 8.361s

## Task Commits

Cada task foi commitada atomicamente:

1. **Task 1: TelefoneBRTest com 19 @Test (RED gate — nao executado isoladamente para evitar build failure intermediario; combinado com Task 2 conforme permitido pelo plan)** — `b0bba6f` (test embutido no commit feat)
2. **Task 2: TelefoneBR.java implementacao (GREEN)** — `b0bba6f` (feat — TelefoneBR + TelefoneBRTest no mesmo commit atomico)
3. **Task 3: Verify GREEN — Tests run: 19, Failures: 0** — sem commit (validacao apenas)

**Plan metadata:** este SUMMARY (commit pendente apos write).

_Note: TDD `tdd="true"` da Task 1 permite skip do gate RED literal conforme plan §Action linha 184-185 ("OU pode pular o gate RED e fazer ambas as tasks juntas — o gate da Task 3 (verify) confirma o GREEN final"). Optei por commit atomico por simplicidade — codigo + tests entram juntos sem janela de commit nao-compilavel no master._

## Files Created

- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/util/TelefoneBR.java` — pure utility, `final class` + `private constructor`, 1 metodo `static String normalizar(String)`, 1 Set imutavel `DDDS_COM_NONO_DIGITO`
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/util/TelefoneBRTest.java` — JUnit 5 + AssertJ, 19 `@Test` com `@DisplayName` em PT-BR, sem `@SpringBootTest` (pure unit test, no context)

## Decisions Made

- **Pacote `util/`** (mesmo de `TipoMensagem` da Wave A) — alinhamento com layout ja estabelecido por Plan 02-01. Pacote alternativo `service/util/` rejeitado para nao misturar utilidade pura com beans Spring.
- **Algoritmo NAO adiciona 9o digito** quando vier sem em SP/RJ/ES — politica deliberada do RESEARCH §5.1 (numero pode ser fixo, nao mobile). Test `sp_ddd11_com_9_preserva` valida com 9; nao ha test "SP fixo" intencionalmente (fora do escopo PER-05).
- **DDD inexistente (99) ainda passa pelo Set lookup** — algoritmo e baseado em Set, nao em validacao real de DDD. Test `ddd_inexistente_99_strip_9` valida explicitamente que 99 strip o 9 (nao esta no Set). Se input for invalido, fica invalido normalizado e Meta rejeita com codigo proprio.
- **19 tests** (1 acima do minimo 18 planejado) — adicionado `sem_digitos_retorna_empty` para cobrir o caso entre `null` (retorna null) e `""` (retorna ""): input com so caracteres nao-digito (`"()-+ "`) retorna `""` apos sanitizacao. Util pra reviews futuros entender que pass-through e empty, nao null.

## Deviations from Plan

None — plan executado exatamente como escrito.

O codigo foi copiado literalmente do RESEARCH §5.1 e §5.2 conforme prescrito pelas Task §Action. Comportamento de Task 1 (RED isolado) foi o caminho documentado pelo plan como aceitavel: "pode pular o gate RED e fazer ambas as tasks juntas — o gate da Task 3 (verify) confirma o GREEN final".

**Total deviations:** 0
**Impact on plan:** Nenhum — execucao seguiu o caminho otimista descrito.

## TDD Gate Compliance

`type: tdd` no Task 1, mas plan top-level e `type: execute` (nao `type: tdd`). Plan-level TDD gate enforcement nao se aplica.

Para Task 1 com `tdd="true"`:
- **RED gate:** skip explicitamente permitido pelo plan §Action linha 184-185
- **GREEN gate:** confirmado por Task 3 — `Tests run: 19, Failures: 0`
- **REFACTOR gate:** N/A — codigo era literalmente copia do RESEARCH §5.1, sem refactor a fazer

Sem warning sobre violacao de gate.

## Issues Encountered

Nenhum. Build limpo na primeira execucao. Algoritmo do RESEARCH funcionou exato — 19 tests verdes em 0.114s.

## Self-Check: PASSED

### Files criados (verificados via filesystem):
- FOUND: `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/util/TelefoneBR.java`
- FOUND: `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/util/TelefoneBRTest.java`

### Commit hash:
- FOUND: `b0bba6f` — confirmado via `git log --oneline -1` apos commit

### Build verde:
- 19 tests `TelefoneBRTest` — Failures: 0, Errors: 0
- 74 tests api-whatsapp aggregate — Failures: 0, Errors: 0, BUILD SUCCESS

### Verification gates do plan:
1. `TelefoneBR.java` existe em `util/` — PASS
2. `TelefoneBRTest.java` existe com 19 `@Test` (>= 18) — PASS
3. `grep -c "@Test" TelefoneBRTest.java` retorna 19 — PASS
4. `mvnw test -pl api-whatsapp -Dtest=TelefoneBRTest` Tests run: 19, Failures: 0 — PASS
5. `mvnw verify -pl api-whatsapp` BUILD SUCCESS, 74 tests verdes — PASS
6. Set `DDDS_COM_NONO_DIGITO` contem exatamente 14 DDDs literais — PASS (visual + 9 SP + 3 RJ + 2 ES = 14)

## Threat Surface Scan

Nenhuma nova superficie introduzida. Pure utility sem I/O, sem state, sem Spring component. Nao loga (chamadores Plans 04/05 mascaram conforme padrao do monorepo).

Threat register do plan:
- T-02-06 (Tampering DDDs) — **mitigated:** Set imutavel com 14 DDDs literais, 19 tests cobrem matriz, constants verificavel via grep
- T-02-07 (Repudiation non-BR) — **mitigated:** branch early-return `!digitos.startsWith("55") || len fora [12,13]`, test `usa_nao_brasil` valida +1 415 nao-altered
- T-02-08 (PII em logs) — **accepted:** utility nao loga; chamadores responsaveis

## Threat Flags

Nenhum.

## Next Phase Readiness — Concerns para Wave B (em curso) + Wave C (futuro)

1. **Plans 04 (ClienteZapService) + 05 (WebhookPayloadParser) podem importar `TelefoneBR.normalizar` agora** — pacote `br.com.erpkit.whatsapp.util.TelefoneBR`. Single source of truth: ambos caminhos (INSERT + lookup) DEVEM passar pelo normalizador, ou UNIQUE constraint quebra. Plan 04 explicitamente: `findByTelefone(TelefoneBR.normalizar(input))` E `clienteZap.setTelefone(TelefoneBR.normalizar(input))` antes do save.

2. **Wave B paralela (Plans 02-03 IdempotencyService + 02-05 *) sem conflito de arquivos** — `git status --short` mostra `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/`, `IdempotencyService.java`, `IdempotencyServiceTest.java` como untracked apos meu commit (ainda em progresso por outras agents). Disjuntos do meu scope (`util/TelefoneBR.java` + `TelefoneBRTest.java`). Plan documentou corretamente: "Sem risco de conflito".

3. **Numero fixo brasileiro NAO testado** — politica documentada (algoritmo pass-through 8-digit local sem 9), mas sem test explicito. Se Phase 5 receber webhook com fixo (ex: callcenter), comportamento e: passa direto sem alteracao. Considerar adicionar test em Phase 5 quando WebhookPayloadParser exercitar caminho real.

4. **`+55` curto (length 2 apos sanitize) NAO testado explicitamente** — esta coberto implicitamente por `length() < 12` early return. Comportamento: retorna `"55"` literal. Se Phase 5 receber payload malformado do Meta, normalizar nao trava (passa o sanitizado pra frente; lookup falha em `clientes_zap`, log warn). Aceitavel.

5. **Encoding LF→CRLF warning no `git add`** — Windows vai usar CRLF no working tree mas LF no commit (autocrlf=true do git config). Sem impacto funcional. JUnit/maven Brand-agnostic ao line ending.

6. **Spike OnConflictSpikeTest continua verde** (2/2 passing dentro dos 74 totais) — gate Wave 1 mantido. Plan 02-03 deve usar fallback save+catch DataIntegrityViolationException conforme decisao gravada empiricamente em 02-01-SUMMARY.md.

---
*Phase: 02-persistencia-idempotencia*
*Plan: 02-02*
*Completed: 2026-05-05*
