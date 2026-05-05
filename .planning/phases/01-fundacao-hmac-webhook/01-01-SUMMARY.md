---
phase: 01-fundacao-hmac-webhook
plan: 01
subsystem: security
tags: [lib-shared, security, filter, backward-compat, api-key, webhook]

# Dependency graph
requires:
  - phase: 00-bootstrap
    provides: lib-shared/ApiKeyFilter base (1-arg constructor) usado por api-email/api-storage/api-consultas
provides:
  - ApiKeyFilter com construtor de 2 args aceitando additionalPublicPaths configuraveis
  - Backward-compat preservada via delegation (this(apiKey, Set.of()))
  - DEFAULT_PUBLIC_PATHS renomeado (era PUBLIC_PATHS) e tornado uniao com additional via campo de instancia publicPaths
  - Suite de tests: 9 originais + 5 novos = 14 verdes
affects:
  - 01-fundacao-hmac-webhook (waves seguintes)
  - api-whatsapp/SecurityConfig (PLAN-06 — registrara /webhook como path adicional)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Filter com policy de paths publicos extensivel via construtor adicional (uniao DEFAULT + additional)"
    - "Imutabilidade do Set publicPaths via Set.copyOf(merged) — evita tampering apos construcao (T-01-03)"
    - "Null-safe construction: additionalPublicPaths null tratado como Set vazio"

key-files:
  created:
    - .planning/phases/01-fundacao-hmac-webhook/01-01-SUMMARY.md
  modified:
    - lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java
    - lib-shared/src/test/java/br/com/erpkit/shared/security/ApiKeyFilterTest.java

key-decisions:
  - "Manter mensagens 401 com acentuacao original (Nao -> Não, invalida -> inválida) conforme PLAN linha 125 — RESEARCH §6 propunha sem acento, mas PLAN tem precedencia e PT-BR e convencao"
  - "Renomear PUBLIC_PATHS para DEFAULT_PUBLIC_PATHS sem quebrar visibilidade (continua private static final) — alinhado com novo papel da constante (default vs uniao final)"
  - "Construtor de 1 arg delega para 2-arg via this(apiKey, Set.of()) — caminho unico de inicializacao, evita duplicacao de logica"

patterns-established:
  - "Extensibilidade por construtor sobrecarregado: 1-arg backward-compat preservado, 2-arg adiciona capacidade (D-02 do CONTEXT.md)"
  - "Tests de regressao convivem com tests de feature nova no mesmo arquivo, separados por comentario header"

requirements-completed:
  - PER-01  # parcial — desbloqueia /webhook como path publico, completara em PLAN-06

# Metrics
duration: 3min
completed: 2026-05-05
---

# Phase 01 Plan 01: ApiKeyFilter additionalPublicPaths Summary

**ApiKeyFilter ganha construtor de 2 args (apiKey, Set<String> additionalPublicPaths) preservando 100% backward-compat com api-email/api-storage/api-consultas via delegation, habilitando registro futuro de /webhook do api-whatsapp como path publico (validado por HMAC, nao API Key).**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-05-05T06:20:23Z
- **Completed:** 2026-05-05T06:23:37Z
- **Tasks:** 3 (Task 1 modificacao, Task 2 tests, Task 3 verificacao reator)
- **Files modified:** 2 (codigo + tests) + 1 SUMMARY criado
- **Tests:** 14 verdes (9 originais + 5 novos)

## Accomplishments

- `ApiKeyFilter.java`: novo construtor `public ApiKeyFilter(String apiKey, Set<String> additionalPublicPaths)` que faz uniao de `DEFAULT_PUBLIC_PATHS` + `additionalPublicPaths` em campo de instancia imutavel `publicPaths`.
- Construtor de 1 arg preservado via `this(apiKey, Set.of())` — zero mudanca para callers existentes.
- Null-safe: `additionalPublicPaths` null tratado como Set vazio (sem NPE).
- 5 novos tests cobrem: regressao 1-arg, path adicional permitido (`/webhook/whatsapp`), Set vazio == 1-arg, null nao quebra, uniao (defaults + additional).
- BUILD SUCCESS no reator restrito (`lib-shared,api-email,api-storage,api-consultas`) — zero regressao em consumidores.

## Task Commits

1. **Task 1+2+3 (atomico):** `feat(lib-shared): adicionar construtor 2-arg em ApiKeyFilter com additionalPublicPaths` — commit `1b72009`
   - Inclui: modificacao em ApiKeyFilter.java + 5 novos tests em ApiKeyFilterTest.java
   - O plan especificou commit atomico unico (PLAN-01-01 secao `<commit>`); seguido literalmente.

**Plan metadata:** este SUMMARY commit ainda pendente (proximo passo).

## Files Created/Modified

- `lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java` — Construtores sobrecarregados (1-arg delega para 2-arg), campo de instancia `publicPaths` (imutavel via `Set.copyOf`), `isPublicPath` agora usa o campo. `PUBLIC_PATHS` renomeado para `DEFAULT_PUBLIC_PATHS`. Mensagens 401 PT-BR preservadas com acentuacao.
- `lib-shared/src/test/java/br/com/erpkit/shared/security/ApiKeyFilterTest.java` — 5 metodos `@Test` adicionados ao final, sem alterar os 9 existentes. Import `java.util.Set` adicionado.
- `.planning/phases/01-fundacao-hmac-webhook/01-01-SUMMARY.md` — este arquivo.

## Decisions Made

- **Acentuacao das mensagens 401:** Mantida (`"Não autorizado"`, `"API Key inválida ou ausente"`) conforme PLAN linha 125, embora RESEARCH §6 sugerisse versao sem acento. Justificativa: PLAN > RESEARCH na hierarquia de fontes; convencao do projeto e PT-BR completo (CLAUDE.md `## Naming Patterns` e `## Language & Identifiers`).
- **Estrutura dos novos tests:** Espelharam o padrao dos 9 existentes (`MockHttpServletRequest`/`MockHttpServletResponse`/`MockFilterChain` da `spring-test`) em vez de Mockito puro com `verify(chain).doFilter(...)`. Justificativa: consistencia visual + framework ja em test scope via `spring-boot-starter-test`.
- **Header de comentario divisor:** Bloco `// =====` separa tests legados dos novos para auditabilidade do `git diff` durante revisao.

## Deviations from Plan

None — plan executed exactly as written.

Pequenos pontos de divergencia entre PLAN e RESEARCH foram resolvidos a favor do PLAN (mensagens 401 com acentuacao original) — ver "Decisions Made" acima. Isto nao e desvio de plan, e seguimento literal.

## Issues Encountered

- **JAVA_HOME desconfigurado:** primeira invocacao de `mvnw` falhou com `JAVA_HOME points to /c/Program Files/Amazon Corretto/jdk21.0.10_7 (no such directory)`. JDK real esta em `/c/Program Files/Java/jdk21.0.10_7/`. Resolvido exportando `JAVA_HOME` por comando. Nao requer mudanca em arquivo de projeto — e configuracao de ambiente local.

## Verification Performed

| Check | Comando | Resultado |
|-------|---------|-----------|
| Compilacao lib-shared | `./mvnw compile -pl lib-shared -q` | BUILD SUCCESS |
| Tests ApiKeyFilterTest | `./mvnw -pl lib-shared test -Dtest=ApiKeyFilterTest` | Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 |
| Reator restrito (lib-shared + 3 consumidores) | `./mvnw verify -pl lib-shared,api-email,api-storage,api-consultas` | BUILD SUCCESS — lib-shared, api-email, api-storage, api-consultas todos verdes |
| Diff somente adicoes | `git diff --stat HEAD~1 HEAD` | 110 insertions(+), 2 deletions(-) — deletions sao apenas substituicao de `private static final Set PUBLIC_PATHS` (1 linha) e adicao de `final Set publicPaths`; logica preservada |
| Pos-commit deletion check | `git diff --diff-filter=D --name-only HEAD~1 HEAD` | OK: nenhum arquivo deletado |

## Next Phase Readiness

- Pronto para PLAN 01-02 (proximo plan da Phase 1).
- `api-whatsapp/SecurityConfig` (PLAN-06) podera fazer:
  ```java
  registration.setFilter(new ApiKeyFilter(apiKey, Set.of("/webhook")));
  ```
  sem hardcodar policy em modulo individual — D-02 satisfeita.
- Nenhum blocker. Nenhum risco residual identificado nas tasks deste plan.

## Self-Check: PASSED

- [x] `lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java` modificado e contem `DEFAULT_PUBLIC_PATHS` e `this.publicPaths` (verificado via Read pos-edicao)
- [x] `lib-shared/src/test/java/br/com/erpkit/shared/security/ApiKeyFilterTest.java` contem 14 metodos `@Test` (9 antigos preservados intactos + 5 novos)
- [x] Commit `1b72009` existe no historico (`git log --oneline -3`)
- [x] BUILD SUCCESS em todos os 4 modulos (lib-shared, api-email, api-storage, api-consultas)
- [x] Zero deletions inesperadas no commit

---
*Phase: 01-fundacao-hmac-webhook*
*Completed: 2026-05-05*
