---
phase: 02-persistencia-idempotencia
plan: 07
subsystem: api-whatsapp
tags:
  - api-whatsapp
  - integration-test
  - mockmvc
  - jdbc-template
  - hmac-signature
  - e2e
  - phase-closeout

dependency_graph:
  requires:
    - "Plan 02-01 (entities + repos + spike OnConflict — Wave A)"
    - "Plan 02-02 (TelefoneBR.normalizar — Wave A parallel)"
    - "Plan 02-03 (IdempotencyService.tentarPersistir — Wave B)"
    - "Plan 02-04 (ClienteZapService.identificar + atualizarUltimaMensagemEm REQUIRES_NEW — Wave C)"
    - "Plan 02-05 (WebhookPayloadParser + 8 fixtures Meta — Wave B)"
    - "Plan 02-06 (MensagemService orquestrador + WebhookController.POST atualizado — Wave D)"
    - "Plan 01-05 (CachedBodyHttpServletRequest + HmacSignatureFilter — Phase 1)"
    - "Plan 01-06 (SecurityConfig com webhook publico de API key — Phase 1)"
    - "Plan 01-07 (WebhookControllerIntegrationTest pattern @AutoConfigureMockMvc — Phase 1)"
  provides:
    - "WebhookPersistenciaIntegrationTest com 13 tests E2E (5 SC ROADMAP + 2 bonus)"
    - "Helper computeSignature(byte[], String secret) reusavel para testes futuros (Phase 3 async pode reusar pattern)"
    - "Helper postFixture(String) DRY para tests de webhook autenticados via HMAC"
    - "Phase 2 fechada empiricamente — todos os 5 SC ROADMAP verdes via integration tests"
    - "ROADMAP.md Phase 2 atualizada: Plans TBD → 7 plans com index e brief; Phase 2 [x] na lista de phases; Progress table 7/7 Complete"
    - "Gate de regressao para Phase 3+ — futuras phases nao podem quebrar Phase 2 sem este teste falhar"
  affects:
    - "Phase 3 (async + ROU-01..05) — refator do controller/service deve manter os 13 tests verdes; novo test pode ser adicionado para baseline async timing"
    - "Phase 4 (outbound + trava 24h) — le ultima_mensagem_em commitado pelo REQUIRES_NEW; SC-5 valida empiricamente que esse commit e visivel"
    - "Phase 6 (Qualidade) — gsd-verifier de Phase 2 ja pode rodar; Phase 6 podera adicionar Testcontainers + WireMock para cobertura adicional"

tech-stack:
  added: []
  patterns:
    - "Integration test E2E full stack: @SpringBootTest(MOCK) + @AutoConfigureMockMvc + @ActiveProfiles(test) + @Autowired MockMvc/WhatsAppProperties/JdbcTemplate"
    - "Fixture viva: computeSignature(body, properties.getAppSecret()) — assina dinamicamente em runtime contra appSecret do test profile, sem hex hardcoded"
    - "Helper DRY postFixture(nome): carrega + assina + POST com Content-Type + characterEncoding(UTF-8) + assert 200"
    - "Isolamento entre tests via filter por wamid (nao deleteAll @BeforeEach) — H2 in-memory compartilhado entre tests do mesmo SpringContext"
    - "JdbcTemplate Timestamp.class para validacao temporal precisa (timezone-naive H2 quirk per Plan 02-06 SUMMARY)"
    - "Comparacao temporal via epoch seconds (nao Instant.now) — H2 NOW() retorna LOCAL como UTC; epoch sec local-vs-local neutraliza offset"
    - "Bonus test de JSON malformado retornando 200 — gate de regressao do ack-first defensivo Plan 02-06 (PITFALLS C-05)"

key-files:
  created:
    - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookPersistenciaIntegrationTest.java
  modified:
    - .planning/ROADMAP.md

decisions:
  - "13 tests em vez dos 12 do PLAN — mantida granularidade de 1 SC por test method (sc1, sc2a..sc2f, sc3a, sc3b, sc4, sc5, bonus_multiple, bonus_malformed) para visibilidade individual no report do JUnit. Cada test isolado em seu @DisplayName facilita debugging e mapping para SC do ROADMAP."
  - "Filter por wamid em assertions de COUNT — H2 compartilhado entre tests do mesmo SpringContext acumula rows. Padrao de isolamento canonico do Plan 02-06 SUMMARY."
  - "Comparacao temporal SC-5 via epoch seconds (long) — neutraliza H2 NOW() timezone-naive quirk descoberto em Plan 02-06. Em PostgreSQL real prod com TIMESTAMP WITH TIME ZONE, comparacao Instant direta funcionaria."
  - "Helper postFixture com characterEncoding(UTF-8) explicito — defesa contra default ISO-8859-1 do MockMvc em algumas versoes, especifico para text-portugues.json (PITFALLS C-04 +  Plan 01-07 Wave 6 SUMMARY concern 3)."
  - "Bonus test de JSON malformado usa wamid `wamid.malformado.001` que NAO existe em fixtures — assertion isolada (qualquer COUNT > 0 indicaria efeito colateral indesejado, independente da ordem de tests)."
  - "Pacote `controller/` (alinhamento com WebhookControllerIntegrationTest da Phase 1) ao inves de `integration/` — escopo do PLAN file path alinhado."

requirements-completed: []  # WEB-05/06/07 + PER-05/06/07 ja foram fechados em Plans 02-03..06; este plan VALIDA empiricamente via E2E mas nao introduz novos requirements
requirements-validated:
  - WEB-05  # E2E: idempotency wamid via 2 POSTs identicos (sc1)
  - WEB-06  # E2E: UNIQUE wamid silenciada — count = 1 apos duplicate (sc1)
  - WEB-07  # E2E: parser todos os tipos persiste corretamente (sc2a..f)
  - PER-05  # E2E: telefone normalizado em mensagens_log (sc3a strip vs sc3b preserve)
  - PER-06  # E2E: id_cliente_erp = null em telefone novo (sc4)
  - PER-07  # E2E: ultima_mensagem_em populada com NOW() do banco (sc5)

metrics:
  duration_human: "~9min"
  tasks_completed: 3
  files_created: 1
  files_modified: 1
  tests_added: 13
  total_reactor_tests: 183
  api_whatsapp_tests: 112
  build_status: "BUILD SUCCESS"
  build_time_api_whatsapp: "10.2s"
  build_time_full_reactor: "~30s"
  completed_date: "2026-05-05"
---

# Phase 2 Plan 07: Integration Tests E2E + Phase 2 Closeout Summary

**WebhookPersistenciaIntegrationTest com 13 tests E2E (@SpringBootTest MOCK + MockMvc + JdbcTemplate) exercita full stack Phase 1 + 2 e fecha Phase 2 — todos os 5 ROADMAP success criteria empiricamente verdes; api-whatsapp 112 tests verdes; reator inteiro BUILD SUCCESS; ROADMAP atualizado: Plans TBD → 7 plans + Phase 2 [x] Complete.**

## Performance

- **Duration:** ~9min wall-clock
- **Started:** 2026-05-05T16:41:33Z
- **Completed:** 2026-05-05T16:50:51Z (apos build verify completo)
- **Tasks:** 3 (Task 1 test class + Task 2 ROADMAP + Task 3 verify build reator)
- **Files criados:** 1 (test class)
- **Files modificados:** 1 (ROADMAP.md)

## Accomplishments

- **WebhookPersistenciaIntegrationTest** publicado com 13 tests E2E exercitando full stack Phase 1+2: HmacSignatureFilter -> WebhookController -> MensagemService -> WebhookPayloadParser -> IdempotencyService -> ClienteZapService.identificar -> ClienteZapService.atualizarUltimaMensagemEm REQUIRES_NEW
- **Helper `computeSignature(byte[], String secret)` viva:** assina fixtures dinamicamente com HMAC-SHA256 do `appSecret` do test profile (`test-app-secret`) — sem hex hardcoded, automaticamente alinhada se secret mudar
- **Helper `postFixture(String)` DRY:** carrega fixture do classpath + assina + POST com Content-Type+UTF-8 + assert 200 — uma linha por test
- **5 ROADMAP SC empiricamente verdes** (cada um com >= 1 test passando):
  - SC-1: 2 POSTs mesmo wamid → 1 row (sc1) ✓
  - SC-2: parser todos os tipos (sc2a-text, sc2b-button, sc2c-list, sc2d-document, sc2e-desconhecido, sc2f-status nao persistido) ✓
  - SC-3: telefone normalizado (sc3a-strip DDD 47 SC, sc3b-preserve DDD 11 SP) ✓
  - SC-4: auto-create id_cliente_erp=null (sc4) ✓
  - SC-5: ultima_mensagem_em populada (sc5 via JdbcTemplate Timestamp) ✓
- **2 bonus tests:** multiple messages no mesmo POST persiste todas (bonus_multiple) + JSON malformado retorna 200 ack-first (bonus_malformed) ✓
- **api-whatsapp `mvnw verify -pl api-whatsapp -am`:** BUILD SUCCESS, **112 tests verdes** (99 prev + 13 novos), 0 falhas, 0 erros, 10.2s
- **Reator inteiro `mvnw verify`:** BUILD SUCCESS, 7 modulos verdes (lib-shared 20 + api-email 34 + api-storage 13 + api-consultas 4 + api-whatsapp 112 + 2 libs sem testes diretos = ~183 tests), zero regressao
- **ROADMAP.md atualizado:** Phase 2 `[x]` na lista, Plans TBD → 7 plans com index, Plan 02-07 `[x]`, Progress table mostra `7/7 Complete (awaiting verifier) | 2026-05-05`
- **Phase 2 entregue empiricamente** — todos 5 SC + 9 reqs (WEB-05/06/07 + PER-02/03/04/05/06/07) satisfeitos observavelmente via tests verdes

## Task Commits

| # | Task | Status | Commit |
|---|------|--------|--------|
| 1 | Criar WebhookPersistenciaIntegrationTest com 13 tests cobrindo 5 SC + bonus | DONE — 13/13 verdes | ab60b1b |
| 2 | Atualizar ROADMAP.md Phase 2 — Plans TBD → 7 plans com index + Progress 7/7 Complete | DONE | ab60b1b |
| 3 | Verificar build do reator + total tests count | DONE — BUILD SUCCESS, 112 tests api-whatsapp, ~183 tests reator | n/a |

**Plan metadata:** proximo commit `docs(02-07): summary + Phase 2 closeout`.

## Files Criados/Modificados

### Criados
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookPersistenciaIntegrationTest.java` — `@SpringBootTest(classes=WhatsAppApplication.class, webEnvironment=MOCK)` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` + 3 helpers (computeSignature/fixture/postFixture) + 13 `@Test` cobrindo 5 SC + 2 bonus; ~370 linhas com Javadoc inline mapeando cada test → SC

### Modificados
- `.planning/ROADMAP.md` — 3 edicoes: (1) Phase 2 marker `[ ]` → `[x]` na lista de Phases com brief expandido (parser + orquestrador + integration tests E2E); (2) Plan 02-07 marker `[ ]` → `[x]` com brief; (3) Progress table linha 2 — `6/7 In Progress` → `7/7 Complete (awaiting verifier) | 2026-05-05`

## Test → SC Mapping

| Test Method | ROADMAP SC | Requirement Validado | Pattern de Validacao |
|-------------|------------|----------------------|----------------------|
| `sc1_dois_posts_mesmo_wamid_resulta_em_apenas_uma_row` | SC-1 | WEB-05, WEB-06 | postFixture 2x + JDBC COUNT(wamid) == 1 |
| `sc2a_parser_text_persiste_com_tipo_text` | SC-2 | WEB-07 (text) | JDBC SELECT tipo + conteudo UTF-8 portugues |
| `sc2b_parser_button_reply_persiste_com_tipo_interactive_button` | SC-2 | WEB-07 (button) | JDBC SELECT tipo == "interactive_button", conteudo == "id\|title" |
| `sc2c_parser_list_reply_persiste_com_tipo_interactive_list` | SC-2 | WEB-07 (list) | JDBC SELECT tipo == "interactive_list" |
| `sc2d_parser_document_persiste_com_tipo_document_e_media_id` | SC-2 | WEB-07 (document) | JDBC SELECT tipo + media_id == "media-id-12345" |
| `sc2e_parser_tipo_desconhecido_persiste_com_tipo_desconhecido` | SC-2 | WEB-07 (desconhecido) | JDBC SELECT tipo == "desconhecido" + COUNT WHERE conteudo IS NULL |
| `sc2f_parser_status_NAO_persiste_em_phase_2` | SC-2 | D-06 (statuses ignorados) | JDBC COUNT(wamid status) == 0 |
| `sc3a_telefone_DDD47_e_normalizado_strip_9` | SC-3 | PER-05 (DDD 47 strip) | JDBC COUNT WHERE telefone == "554784178525" (12 digitos) |
| `sc3b_telefone_DDD11_SP_mantem_9_digito` | SC-3 | PER-05 (DDD 11 preserve) | JDBC COUNT WHERE telefone == "5511987654321" (13 digitos) |
| `sc4_telefone_novo_cria_cliente_zap_com_id_cliente_erp_null` | SC-4 | PER-06 | JDBC COUNT WHERE telefone AND id_cliente_erp IS NULL |
| `sc5_ultima_mensagem_em_atualizada_apos_webhook` | SC-5 | PER-07 | JDBC Timestamp.class via 2a conexao + epoch sec window |
| `bonus_multiple_messages_em_um_post_persiste_todas` | bonus | parser loop | JDBC COUNT por wamid (multi.001 + multi.002) |
| `bonus_post_com_json_malformado_retorna_200_ack_first` | bonus | PITFALLS C-05 | MockMvc status 200 + JDBC COUNT == 0 |

**Total:** 13 tests, 5 SC cobertos, 6 requirements validados (WEB-05/06/07 + PER-05/06/07).

## Decisions Made

- **13 tests em vez dos 12 do PLAN:** mantida granularidade de 1 SC por test method para visibilidade individual no report do JUnit. SC-2 foi quebrado em 6 sub-tests (sc2a..sc2f), cada um com `@DisplayName` claro mapeado a um tipo Meta — facilita debugging quando algum tipo regredir.
- **Filter por wamid em assertions de COUNT:** H2 in-memory e compartilhado entre tests do mesmo SpringContext (cache de contexto Spring reusa), entao COUNT(*) global pode ser contaminado por outros tests. Padrao canonico Plan 02-06 SUMMARY (`webhook_duplicado_apenas_1_row` filtra `findAll().stream().filter`); aqui aplicado via SQL `WHERE wamid = ?` que e mais eficiente.
- **Comparacao temporal SC-5 via epoch seconds (long, nao Instant):** H2 v2.3.232 NOW() retorna `TIMESTAMP WITHOUT TIME ZONE` que e mapeado pelo Hibernate para `Instant` assumindo UTC, mas o valor armazenado e LOCAL TIME (BRT, UTC-3). Validacao `Instant.isAfter(Instant.now().minusSeconds(5))` falharia por offset de 3h (mesmo bug Rule 1 do Plan 02-06). Solucao: extrair epoch seconds via `tsRaw.getTime() / 1000L` e comparar com `System.currentTimeMillis() / 1000L` — local-vs-local, neutraliza offset.
- **Pacote `controller/` (alinhamento com Phase 1):** PLAN file path original sugeria `integration/` mas `WebhookControllerIntegrationTest` da Phase 1 ja vive em `controller/`. Manter colocalizado simplifica navegacao e mantem consistencia. Ambos sao integration tests de webhook end-to-end.
- **Helper `postFixture(String)` com `characterEncoding(UTF_8)` explicito:** defesa contra default ISO-8859-1 do MockMvc em algumas versoes — especifico para `text-portugues.json` que tem caracteres acentuados ("Olá, gostaria de um orçamento"). Mesmo concern documentado em Plan 01-07 SUMMARY.
- **Bonus test de JSON malformado usa wamid inexistente para COUNT:** assertion isolada — qualquer `COUNT > 0` indicaria efeito colateral indesejado independente da ordem de tests do JUnit. Robusto contra qualquer interleaving.
- **Sem `@BeforeEach` deleteAll:** poderia mascarar bug de race condition entre tests E2E (e.g., um test falando de cliente que outro acabou de criar). Wamids unicos sao a estrategia de isolamento canonica do codebase Phase 2.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Test sc3a_telefone_DDD47_e_normalizado_strip_9 falhou na primeira execucao com COUNT=3**

- **Found during:** Task 3 primeira rodada do test (`./mvnw test -pl api-whatsapp -Dtest=WebhookPersistenciaIntegrationTest`)
- **Issue:** `SELECT COUNT(*) FROM whatsapp.mensagens_log WHERE telefone = '554784178525'` retornou 3 em vez de 1. Outros tests do mesmo SpringContext (sc1 com 2 POSTs identicos antes do conflict + sc2a + bonus_multiple — todas usam from `5547984178525` que normaliza para o mesmo telefone) ja haviam inserido rows com este telefone. JUnit nao garante ordem entre tests do mesmo file (e cache de contexto Spring reusa o H2 in-memory).
- **Fix:** Mudar assertion principal de `COUNT WHERE telefone = ?` para `COUNT WHERE wamid = ? AND telefone = ?` — wamid e UNIQUE entao filtra exatamente a row do POST deste test. Mantida a assertion secundaria (COUNT WHERE telefone = "5547984178525" == 0) que e sempre verdadeira independente de ordem (formato cru de 13 digitos NUNCA deve aparecer em mensagens_log porque INSERT sempre normaliza).
- **Files modificados:** `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookPersistenciaIntegrationTest.java`
- **Verification:** test sc3a verde apos fix; total 13/13 verdes na re-rodada
- **Committed in:** `ab60b1b`
- **Impact:** zero em codigo de producao; quirk de isolamento JUnit + H2 in-memory compartilhado.

### Authentication Gates

Nenhuma.

### Architectural Decisions (Rule 4)

Nenhuma — todas as escolhas seguiram o RESEARCH §11 + Plan 02-06 SUMMARY concerns.

---

**Total deviations:** 1 auto-fixed (Rule 1)
**Impact on plan:** Em test data/assertion strategy, nao em codigo de producao. Behavior do controller, service, parser, idempotency e cliente exatamente como descrito no PLAN. Sem scope creep.

## Issues Encontradas

- **Logs `[ERROR] o.h.engine.jdbc.spi.SqlExceptionHelper` durante test sc1 (idempotency):** comportamento esperado — IdempotencyService captura DataIntegrityViolationException da segunda chamada (UNIQUE wamid). Hibernate loga o erro do JDBC antes do Spring traduzir e do service silenciar. Indicacao de comportamento correto, nao problema. Documentado tambem em Plan 02-03 SUMMARY e Plan 02-06 SUMMARY.
- **Logs `[ERROR] b.c.e.w.controller.WebhookController : Erro parseando webhook do Meta`:** comportamento esperado do bonus_malformed — controller captura IOException do parser (Jackson lanca em JSON invalido), loga error, retorna 200. Validacao do ack-first defensivo PITFALLS C-05 + Plan 02-06 D-06.

## User Setup Required

Nenhum. Codigo puro Spring Boot, sem env vars novos, sem servicos externos, sem migrations adicionais.

## Threat Surface Scan

Nenhuma nova superficie de seguranca emergente fora do `<threat_model>` do PLAN. T-02-30..T-02-34 todos enderecados:
- **T-02-30 (Tampering — Filter HMAC desabilitado em test):** mitigated — application-test.yml NAO tem flag de disable; Filter sempre ativo. Tests assinam dinamicamente com mesmo `appSecret`. Bonus test de body malformado ainda e assinado validamente — Filter nao gate de validade JSON.
- **T-02-31 (Tampering — appSecret incorreto causaria 401 falso-positivo):** mitigated — `properties.getAppSecret()` injetado e mesmo secret que o Filter usa (single source of truth via Bean Validation no boot).
- **T-02-32 (Repudiation — test passa mas em prod regrediu):** accept — Phase 6 podera adicionar Testcontainers + WireMock para PG-real coverage. Phase 2 assume H2 PG-mode + fixtures Meta como representativos (RESEARCH §8.5).
- **T-02-33 (DoS — integration test slow):** accept — 13 tests em 4.85s e otimo; full Spring context boot uma vez via test class compartilhado. Plus 1 boot pra MensagemServiceTest e 1 pra ClienteZapServiceTest, total api-whatsapp em 10.2s.
- **T-02-34 (Information Disclosure — logs do test podem incluir conteudo das fixtures):** accept — fixtures sao dummy data (`5547984178525`, `aprovar_1234`, `Olá gostaria de um orçamento`); sem PII real. Logs em INFO/WARN level apropriados.

## Threat Flags

Nenhum. Endpoint inalterado da Phase 1 + Plan 02-06; comportamento HTTP externo identico.

## Self-Check: PASSED

### Files criados/modificados (verificados via build verde + git log):
- FOUND: `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookPersistenciaIntegrationTest.java` (criado, 13 @Test verdes)
- FOUND: `.planning/ROADMAP.md` (modificado: Phase 2 [x] + Plan 02-07 [x] + Progress 7/7 Complete)

### Commit hash:
- FOUND: `ab60b1b` — confirmado via gsd-tools.cjs commit response

### Build verde:
- 13 tests novos do plan: `Tests run: 13, Failures: 0, Errors: 0` em WebhookPersistenciaIntegrationTest
- 112 tests api-whatsapp aggregate: `Tests run: 112, Failures: 0, Errors: 0`
- Reator inteiro 7 modulos: BUILD SUCCESS

### Verification automatizada:
- 13 @Test annotations no test class — confirmado
- 5 ROADMAP SC todos com >= 1 test correspondente (sc1, sc2*, sc3*, sc4, sc5)
- 2 bonus tests verdes (multiple + malformed)
- ROADMAP.md `grep -c "02-PLAN-0[1-7]"` deve retornar 7 — confirmado
- Phase 1 WebhookControllerIntegrationTest mantem 10/10 verdes (sem regressao)
- Phase 2 outros tests (MensagemServiceTest 4, ClienteZapServiceTest 7, IdempotencyServiceTest 5, WebhookPayloadParserTest 9, TelefoneBRTest 19, OnConflictSpikeTest 2) todos verdes

## Phase 2 Closeout

### Status final: COMPLETO

- **7/7 plans:** 02-01 (entities + spike) + 02-02 (TelefoneBR) + 02-03 (IdempotencyService) + 02-04 (ClienteZapService) + 02-05 (WebhookPayloadParser) + 02-06 (MensagemService orquestrador) + 02-07 (integration tests E2E)
- **5/5 ROADMAP success criteria:** todos empiricamente verdes via 13 integration tests
- **9/9 REQUIREMENTS:** WEB-05/06/07 + PER-02/03/04/05/06/07 satisfeitos (PER-02/03/04 fechados na Phase 1 via migrations Flyway V1-V4 + entities mapeadas no Plan 02-01)
- **Build aggregate:** api-whatsapp 112 tests, reator 7 modulos BUILD SUCCESS, zero regressao Phase 1

### Pronto para gsd-verifier

Phase 2 esta pronta para handoff ao `gsd-verify-phase`:
1. Todos os 5 SC do ROADMAP sao **observaveis** via test output (nao via inspecao de codigo)
2. Cada SC tem >= 1 integration test verde mapeado em `Test → SC Mapping` (secao acima)
3. Sem requirements pendentes em Phase 2
4. Build verde no reator + zero regressao em Phase 1 (`WebhookControllerIntegrationTest` 10/10)
5. Documentacao completa: 7 SUMMARY.md (um por plan) + ROADMAP atualizado + STATE.md prestes a ser atualizado

### Concerns para Phase 3 (Wave seguinte)

1. **Refator async preserva integration tests:** Phase 3 quebra `MensagemService.processarWebhook` em fast-path (parse + idempotency check sincrono) + `@Async` (identificar cliente + callback ERP). Os 13 tests atuais devem continuar verdes — particularmente sc1 (idempotency persiste mesmo no fast-path), sc2 (parser idem), sc4 e sc5 (auto-create + ultima_mensagem_em — podem virar async, entao tests podem precisar de `Awaitility` ou similar para wait-for-condition).
2. **Gate de timing SC-2 do ROADMAP Phase 3:** "POST retorna 200 em <1s mesmo que ERP callback demore 10s". Plan 03-NN pode adicionar test que mede `System.currentTimeMillis()` antes/depois do POST e assert <1000ms — pattern ja existe em `WebhookControllerIntegrationTest.sc2_post_com_hmac_valido_retorna_200_em_menos_de_1s` (Phase 1).
3. **Reuso de helpers:** `computeSignature` + `fixture` + `postFixture` podem ser extraidos para uma classe utility test (`WebhookTestUtils`) na Wave inicial de Phase 3 quando integration tests de async chegarem. Por enquanto duplicacao com `WebhookControllerIntegrationTest` da Phase 1 e aceitavel (~15 linhas).
4. **Performance baseline:** Plan 02-07 integration test roda em 4.85s para 13 tests — ~370ms/test em media. Phase 3 async pode ter overhead de scheduling (`@EnableAsync` + `TaskScheduler`); monitorar e considerar `@DirtiesContext` ou `@MockBean ExecutorService` para isolar test runs.
5. **WireMock para callback ERP em Phase 3:** Phase 3 introduzira `ErpCallbackClient` que faz POST ao ERP. Testes de Phase 3 devem usar WireMock 3.8.1 (ja confirmado seguro em STATE.md blockers). Plan 02-07 nao precisou de WireMock porque Phase 2 e sincrono e nao chama o ERP ainda.
6. **Statuses ainda ignorados:** Phase 2 nao persiste statuses Meta (D-06). Phase 4 (outbound) podera adicionar persistencia de statuses para mensagens de saida (`direcao=out` em `mensagens_log`). Test sc2f garante que Phase 2 mantem o comportamento atual de NAO persistir.

---
*Phase: 02-persistencia-idempotencia*
*Plan: 07*
*Completed: 2026-05-05*
*Phase 2 Closeout: COMPLETO — pronto para gsd-verify-phase*
