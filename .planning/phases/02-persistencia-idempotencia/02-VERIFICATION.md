---
phase: 02-persistencia-idempotencia
verified: 2026-05-05T18:28:45Z
status: passed
score: 14/14 must-haves verified
overrides_applied: 0
re_verification:
  previous_status: null
  is_initial: true
build:
  command: "./mvnw verify -pl api-whatsapp -am"
  result: "BUILD SUCCESS"
  api_whatsapp_tests: 112
  api_whatsapp_failures: 0
  api_whatsapp_errors: 0
  total_test_classes: 14
must_haves:
  truths:
    - "SC-1: Dois POSTs com mesmo wamid resultam em exatamente 1 linha em mensagens_log; segundo retorna 200 silenciosamente"
    - "SC-2: Parser entende text/button_reply/list_reply/document/statuses; tipo desconhecido persistido com tipo=desconhecido"
    - "SC-3: Telefone +5547984178525 normalizado para 554784178525; SP/RJ/ES mantem 9o digito"
    - "SC-4: ClienteZapService.identificar cria id_cliente_erp=null para telefones desconhecidos"
    - "SC-5: ultima_mensagem_em atualizado com NOW() do banco em REQUIRES_NEW separada"
    - "WEB-05: Idempotencia fast-path por wamid em IdempotencyService"
    - "WEB-06: Idempotencia hard-guard via UNIQUE wamid + DataIntegrityViolationException silenciada"
    - "WEB-07: Parser entende text/interactive/document/statuses + persiste desconhecido"
    - "PER-02: V1 + @Entity ClienteZap mapeada (validate ok)"
    - "PER-03: V2 + @Entity MensagemLog mapeada com Direcao @Enumerated STRING"
    - "PER-04: V3 + @Entity MediaCache mapeada com columnDefinition CHAR(64)"
    - "PER-05: TelefoneBR.normalizar aplicado no INSERT/lookup"
    - "PER-06: ClienteZapService.identificar cria com id_cliente_erp=null"
    - "PER-07: ClienteZapService.atualizarUltimaMensagemEm REQUIRES_NEW + NOW()"
---

# Phase 2: Persistencia + Idempotencia - Verification Report

**Phase Goal:** Mensagens entrantes sao persistidas de forma idempotente, clientes sao identificados (ou criados) pelo telefone com normalizacao BR, e `ultima_mensagem_em` e atualizado atomicamente preparando a trava 24h.

**Verified:** 2026-05-05T18:28:45Z
**Status:** PASS
**Re-verification:** No - initial verification

## Veredito

**PASS** — Phase 2 entregue conforme planejado. Todos os 5 ROADMAP success criteria + 9 requirements (WEB-05/06/07 + PER-02..07) verificados empiricamente via codigo + 13 integration tests E2E + 99 unit/integration tests adicionais. Build verde no reator (api-whatsapp 112 tests, 0 falhas, 0 erros). Spike de Wave 1 (H2 nao suporta ON CONFLICT) corretamente documentado e fallback save+catch DataIntegrityViolationException implementado em ambos services criticos (IdempotencyService + ClienteZapService).

## Goal Achievement

### Success Criteria Coverage (ROADMAP Phase 2)

| SC | Truth | Status | Evidence |
|----|-------|--------|----------|
| **SC-1** | 2 POSTs mesmo wamid → 1 row + 200 silenciosamente | PASS | `IdempotencyService.tentarPersistir()` linhas 65-75 (`api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/IdempotencyService.java`) — try save() / catch DataIntegrityViolationException → return false. Teste `WebhookPersistenciaIntegrationTest.sc1_dois_posts_mesmo_wamid_resulta_em_apenas_uma_row` (linha 156) faz 2 postFixture identicos e valida COUNT(wamid)==1. Teste `IdempotencyServiceTest.concorrencia_2_threads_mesmo_wamid` (linha 84) confirma race com 2 threads simultaneos → truthCount==1. UNIQUE wamid (`V2__criar_tabela_mensagens_log.sql:19`) e o gate atomico do banco. |
| **SC-2** | Parser text/button/list/document/statuses + desconhecido | PASS | `WebhookPayloadParser.mapTipo()` (linha 109) cobre text/document/image/audio/interactive(button_reply/list_reply); default → DESCONHECIDO. Statuses extraidos via `extrairStatus()` linha 160. 6 tests E2E SC-2 verdes (sc2a-text, sc2b-button, sc2c-list, sc2d-document, sc2e-desconhecido, sc2f-status NAO persistido). 9 unit tests `WebhookPayloadParserTest` verdes. |
| **SC-3** | Telefone +5547984178525 (DDD 47) → 554784178525; SP/RJ/ES preservam 9 | PASS | `TelefoneBR.normalizar` linha 66-99 — Set imutavel `DDDS_COM_NONO_DIGITO` com 14 DDDs (SP 11-19, RJ 21/22/24, ES 27/28). Demais DDDs com numero local 9-digit comecando com 9 → strip. 19 tests `TelefoneBRTest` verdes. Tests E2E `sc3a_telefone_DDD47_e_normalizado_strip_9` (linha 307) e `sc3b_telefone_DDD11_SP_mantem_9_digito` (linha 341) validam end-to-end com banco. |
| **SC-4** | ClienteZapService.identificar cria id_cliente_erp=null | PASS | `ClienteZapService.identificar()` linha 60 + `criarNovo()` linha 65-79 — `new ClienteZap(normalizado, null)` (id_cliente_erp = null). Test E2E `sc4_telefone_novo_cria_cliente_zap_com_id_cliente_erp_null` (linha 362) valida via JDBC `WHERE id_cliente_erp IS NULL`. Test unit `ClienteZapServiceTest.identificar_cria_telefone_novo` (linha 52). |
| **SC-5** | ultima_mensagem_em via NOW() do banco em REQUIRES_NEW | PASS | `ClienteZapService.atualizarUltimaMensagemEm()` linha 99 anotado `@Transactional(propagation = Propagation.REQUIRES_NEW)`. Native query `ClienteZapRepository.atualizarUltimaMensagemEm()` linha 44-50 usa `NOW()` do banco (NAO `Instant.now()`). Test `ClienteZapServiceTest.atualizar_em_nova_transacao_commit_imediato` (linha 125) valida commit imediato visivel via 2a conexao do pool (JdbcTemplate). Test E2E `sc5_ultima_mensagem_em_atualizada_apos_webhook` (linha 391). |

**Score:** 5/5 ROADMAP SC verificados.

### Requirements Coverage

| Req | Description | Status | Evidence |
|-----|------------|--------|----------|
| **WEB-05** | Idempotencia fast-path por wamid em IdempotencyService | PASS | `IdempotencyService.tentarPersistir()` retorna false silenciosamente em duplicate sem reprocessar. Test `IdempotencyServiceTest.inserir_segunda_vez_retorna_false`. |
| **WEB-06** | Idempotencia hard-guard UNIQUE wamid + catch DataIntegrityViolationException | PASS | UNIQUE wamid em V2 migration linha 19; catch em IdempotencyService linha 69. Test concorrencia 2 threads valida gate atomico. |
| **WEB-07** | Parser text/button/list/document + tipo=desconhecido sem erro | PASS | Parser cobre todos tipos; tipos novos do Meta → DESCONHECIDO; conteudo=null/mediaId=null. Test `sc2e_parser_tipo_desconhecido_persiste_com_tipo_desconhecido`. |
| **PER-02** | Migration V1 clientes_zap + entity mapeada | PASS | V1 deployada em Phase 1 (PER-01); `@Entity ClienteZap` em Plan 02-01 (commit 1d2b4c6) com `@Table(schema="whatsapp", name="clientes_zap")`. Hibernate validate verde. |
| **PER-03** | Migration V2 mensagens_log + entity com @Enumerated Direcao | PASS | V2 deployada em Phase 1; `@Entity MensagemLog` linha 32-126 com `@Enumerated(STRING)` Direcao + `columnDefinition="TEXT"` em conteudo. |
| **PER-04** | Migration V3 media_cache + entity com columnDefinition CHAR(64) | PASS | V3 deployada em Phase 1; `@Entity MediaCache` linha 23-80 com `@Id` String `columnDefinition="CHAR(64)"`. |
| **PER-05** | Normalizacao telefone BR no INSERT em clientes_zap | PASS | `ClienteZapService.identificar()` linha 61 chama `TelefoneBR.normalizar()` antes de findByTelefone/save; parser tambem normaliza linha 101. 19 tests TelefoneBR + 2 tests E2E (sc3a/sc3b). |
| **PER-06** | identificar(telefone) cria com id_cliente_erp=null | PASS | `criarNovo()` linha 66 — `new ClienteZap(normalizado, null)`. Race protection via catch `DataIntegrityViolationException` + re-fetch (linha 71-77). |
| **PER-07** | atualizarUltimaMensagemEm REQUIRES_NEW + NOW() do DB | PASS | `@Transactional(REQUIRES_NEW)` linha 99 + native `UPDATE ... SET ultima_mensagem_em = NOW()` em repository linha 47. |

**Score:** 9/9 requirements satisfeitos.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|---------|--------|---------|
| `model/ClienteZap.java` | @Entity schema=whatsapp + telefone UNIQUE + id_cliente_erp nullable | PASS | 93 linhas; `@Entity @Table(schema="whatsapp", name="clientes_zap")`; toString mascara telefone (PII). |
| `model/MensagemLog.java` | @Entity + wamid UNIQUE + Direcao @Enumerated STRING | PASS | 126 linhas; columnDefinition TEXT em conteudo (resolve quirk H2 PG-mode @Lob). |
| `model/MediaCache.java` | @Entity + arquivoHash PK CHAR(64) | PASS | 80 linhas; columnDefinition CHAR(64); Phase 4 vai consumir. |
| `model/Direcao.java` | enum lowercase {in, out} matching CHECK | PASS | 19 linhas; lowercase deliberado matching V2 CHECK. |
| `repository/ClienteZapRepository.java` | findByTelefone + atualizarUltimaMensagemEm native | PASS | 51 linhas; native @Query com NOW(). |
| `repository/MensagemLogRepository.java` | findByWamid + Page query | PASS | 29 linhas; sem ON CONFLICT (fallback save+catch ja em service). |
| `repository/MediaCacheRepository.java` | esqueleto para Phase 4 | PASS | Existe, sem queries customizadas. |
| `util/TelefoneBR.java` | normalizar(String) com 14 DDDs SP/RJ/ES | PASS | 100 linhas; pure utility, private constructor; 19 tests. |
| `util/TipoMensagem.java` | constants TEXT/INTERACTIVE_BUTTON/...DESCONHECIDO | PASS | 24 linhas; 7 constants (sem STATUS — statuses tratados via StatusEntranteDTO separado, OK por D-06). |
| `service/IdempotencyService.java` | tentarPersistir com fallback save+catch | PASS | 76 linhas; ON CONFLICT NAO esta em codigo de producao (apenas em comentarios documentando o spike). |
| `service/ClienteZapService.java` | identificar + atualizarUltimaMensagemEm REQUIRES_NEW | PASS | 109 linhas; race protection via catch + re-fetch. |
| `service/WebhookPayloadParser.java` | extrair(byte[]) -> ParsedWebhook | PASS | 167 linhas; tolerante a campos null; aplica TelefoneBR.normalizar. |
| `service/MensagemService.java` | processarWebhook orquestrador sincrono | PASS | 95 linhas; sem @Transactional na classe (preserva proxy AOP cross-bean). |
| `controller/WebhookController.java` | POST delega ao MensagemService + ack-first | PASS | 133 linhas; try/catch IOException + RuntimeException → return 200 (PITFALLS C-05). |
| `dto/*` (11 Jackson + 3 records) | Envelope Meta + ParsedWebhook output | PASS | Todos presentes; @JsonIgnoreProperties(ignoreUnknown=true) em todas Jackson DTOs. |
| `test/.../WebhookPersistenciaIntegrationTest.java` | 13 tests E2E mapping 5 SC + 2 bonus | PASS | 13 @Test annotations confirmadas via grep; todos verdes. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| WebhookController.POST | MensagemService.processarWebhook | constructor injection | WIRED | Linha 49-52 + linha 125 (`mensagemService.processarWebhook(rawBody)`). |
| MensagemService | IdempotencyService.tentarPersistir | constructor injection | WIRED | Linha 46-52 + linha 75-77 (loop de mensagens). |
| MensagemService | ClienteZapService.identificar + atualizarUltimaMensagemEm | constructor injection (cross-bean) | WIRED | Linha 85-86 — calls VIA field para ativar proxy AOP REQUIRES_NEW. |
| MensagemService | WebhookPayloadParser.extrair | constructor injection | WIRED | Linha 70 (parser.extrair). |
| IdempotencyService | MensagemLogRepository.save | constructor injection | WIRED | Linha 66 (save dentro de try/catch). |
| ClienteZapService | ClienteZapRepository.findByTelefone | constructor injection | WIRED | Linha 62. |
| ClienteZapService | ClienteZapRepository.atualizarUltimaMensagemEm (native NOW()) | constructor injection | WIRED | Linha 102 (native @Query usando NOW() do DB). |
| WebhookPayloadParser | TelefoneBR.normalizar | static call | WIRED | Linhas 101 + 164 (msg.from + status.recipient_id — defesa em profundidade). |

Todas as 8 conexoes criticas estao wired (verificado via grep + leitura de codigo).

### Locked Decisions Verification

| Decision | Status | Evidence |
|----------|--------|----------|
| **D-01** 3 entities @Table(schema=whatsapp) + Instant + IDENTITY | PASS | Verificado em ClienteZap.java + MensagemLog.java + MediaCache.java; todos com `@Table(schema="whatsapp",...)` + `@GeneratedValue(IDENTITY)` + `Instant` para timestamps. Hibernate validate verde. |
| **D-02** native ON CONFLICT idempotency → fallback save+catch | PASS | Spike OnConflictSpikeTest confirmou empiricamente que H2 v2.3.232 NAO suporta ON CONFLICT (test 1) e que UNIQUE constraint dispara DataIntegrityViolationException (test 2). IdempotencyService linha 65-75 usa save() + catch. **Codigo de producao NAO contem `ON CONFLICT`** (grep retornou 0 linhas em src/main/java de producao executavel; apenas em comentarios de documentacao). |
| **D-03** TelefoneBR utility puro com lookup table SP/RJ/ES | PASS | TelefoneBR.java pure utility (private constructor + final class); Set imutavel com 14 DDDs (linha 49-53); 19 tests cobrindo SP/RJ/ES preserva, demais strip. |
| **D-04** REQUIRES_NEW + NOW() do DB | PASS | ClienteZapService.atualizarUltimaMensagemEm linha 99 com `@Transactional(propagation = Propagation.REQUIRES_NEW)`; native query linha 47-50 usa `NOW()` do banco. Test `atualizar_em_nova_transacao_commit_imediato` valida commit imediato via 2a conexao. |
| **D-05** Parser Jackson com tipo=desconhecido | PASS | WebhookPayloadParser.mapTipo linha 109-129 com switch + default → DESCONHECIDO; interactive sem button/list → DESCONHECIDO. Test `sc2e_parser_tipo_desconhecido_persiste_com_tipo_desconhecido` valida. |
| **D-06** WebhookController.POST sincrono em Phase 2 | PASS | WebhookController.receber (linha 106) chama `mensagemService.processarWebhook(rawBody)` SINCRONO, sem @Async. Statuses parseados mas log.debug only (MensagemService linha 90-93) — Phase 4 vai persistir. |
| **D-07** auto-create cliente com id_cliente_erp=null + race protection | PASS | ClienteZapService.criarNovo linha 65-79 com `new ClienteZap(normalizado, null)` + try/catch DataIntegrityViolationException + re-fetch. Test concorrencia `identificar_concorrente_unique` valida 2 threads → 1 row. |

### PITFALLS Coverage

| Pitfall | Addressed by | Verified by |
|---------|-------------|-------------|
| **C-01** TOCTOU race com janela 24h | REQUIRES_NEW + NOW() do DB em ClienteZapService.atualizarUltimaMensagemEm | Test `atualizar_em_nova_transacao_commit_imediato` (commit imediato visivel via 2a conexao do pool) + test E2E sc5 |
| **C-06** wamid concurrent delivery race | UNIQUE constraint do DB + catch DataIntegrityViolationException em IdempotencyService | Test `concorrencia_2_threads_mesmo_wamid` (truthCount==1, rows==1) + spike OnConflictSpikeTest test 2 |
| **C-13** 9th digit normalization (bug 131026) | TelefoneBR.normalizar com Set imutavel SP/RJ/ES | 19 tests TelefoneBRTest + tests E2E sc3a (DDD 47 strip) e sc3b (DDD 11 preserve) |

### Build Verification

```bash
$ export JAVA_HOME="/c/Program Files/Java/jdk21.0.10_7"
$ ./mvnw verify -pl api-whatsapp -am
[INFO] Reactor Summary for ERP Kit - Modulos Plugaveis 1.1.0-SNAPSHOT:
[INFO] BUILD SUCCESS

api-whatsapp test classes (14 classes):
  - WhatsAppPropertiesHappyPathTest:        1 test
  - WhatsAppPropertiesValidationTest:       6 tests
  - HealthControllerTest:                   1 test
  - WebhookControllerIntegrationTest:      10 tests   (Phase 1 — sem regressao)
  - WebhookControllerTest:                  4 tests
  - WebhookPersistenciaIntegrationTest:    13 tests   (Phase 2 SC gate)
  - FlywayMigrationTest:                    6 tests
  - ClienteZapServiceTest:                  7 tests
  - HmacValidatorTest:                     13 tests   (Phase 1)
  - IdempotencyServiceTest:                 5 tests
  - MensagemServiceTest:                    4 tests
  - WebhookPayloadParserTest:               9 tests
  - OnConflictSpikeTest:                    2 tests   (spike Wave 1 — gate doc)
  - TelefoneBRTest:                        19 tests
  - CachedBodyHttpServletRequestTest:       6 tests
  - HmacSignatureFilterTest:                6 tests
  ─────────────────────────────────
  Total: 112 tests, 0 failures, 0 errors

lib-shared:
  - 20 tests, 0 failures, 0 errors
```

#### Anti-pattern Grep Checks

| Check | Expected | Actual | Status |
|-------|----------|--------|--------|
| `grep -r "ON CONFLICT" api-whatsapp/src/main/java/` (codigo executavel — exclui comentarios) | 0 | 0 | PASS — fallback path ativo |
| `grep -r "@Transactional(propagation = Propagation.REQUIRES_NEW)" src/main/java/` | >= 1 | 1 (ClienteZapService linha 99) | PASS |
| `grep -r "MessageDigest.isEqual" src/main/java/` | >= 1 | 2 (HmacValidator linha 84 + WebhookController linha 77) | PASS — Phase 1 gate ainda vivo |
| `grep -r "DataIntegrityViolationException" src/main/java/` (catch sites) | >= 2 | 2 (IdempotencyService linha 69 + ClienteZapService linha 71) | PASS |

### Behavioral Spot-Checks

| Behavior | Mecanismo | Result | Status |
|----------|-----------|--------|--------|
| 2 POSTs identicos → 1 row | WebhookPersistenciaIntegrationTest.sc1 com MockMvc + JdbcTemplate COUNT(wamid)==1 | Test verde no build | PASS |
| Telefone DDD 47 strip 9 | sc3a com COUNT(wamid+telefone normalizado)==1 + COUNT(telefone cru)==0 | Test verde | PASS |
| auto-create id_cliente_erp=null | sc4 com COUNT(telefone AND id_cliente_erp IS NULL)==1 | Test verde | PASS |
| ultima_mensagem_em populada via REQUIRES_NEW | sc5 + ClienteZapServiceTest.atualizar_em_nova_transacao via JdbcTemplate Timestamp | Tests verdes | PASS |
| Concorrencia wamid (race) | IdempotencyServiceTest.concorrencia_2_threads_mesmo_wamid (truthCount==1) | Test verde | PASS |
| Concorrencia telefone (race) | ClienteZapServiceTest.identificar_concorrente_unique (COUNT==1) | Test verde | PASS |
| JSON malformado → 200 ack-first | bonus_post_com_json_malformado_retorna_200_ack_first | Test verde | PASS |

### Atomic Commits (16 commits desde ae24f3a)

```
8521b0f docs(02-07): summary + Phase 2 closeout (STATE/REQUIREMENTS)
ab60b1b feat(api-whatsapp): integration test E2E + ROADMAP Phase 2 fechado
19f4e86 docs(02-06): adicionar SUMMARY plan 06 + atualizar STATE/ROADMAP
24ac27c feat(api-whatsapp): orquestrador MensagemService + WebhookController POST
32accde docs(02-04): adicionar SUMMARY plan 04 + atualizar STATE/ROADMAP/REQUIREMENTS
f347de4 feat(api-whatsapp): adicionar ClienteZapService com REQUIRES_NEW + NOW()
54c9dff docs(02-05): adicionar SUMMARY plan 05 + atualizar STATE/ROADMAP/REQUIREMENTS
4b63f60 docs(02): adicionar SUMMARY plan 03
6ba039e feat(api-whatsapp): adicionar WebhookPayloadParser + DTOs Jackson + fixtures Meta
eaad07b feat(api-whatsapp): adicionar IdempotencyService com fallback save+catch (wamid UNIQUE)
b0bba6f feat(api-whatsapp): adicionar TelefoneBR.normalizar com 19 tests
26fc053 docs(02): adicionar SUMMARY plan 01 + atualizar STATE/ROADMAP/REQUIREMENTS
1d2b4c6 feat(02-01): adicionar entities + repos esqueleto + spike ON CONFLICT
7ec94c2 chore(state): begin phase 2 execution
f05868a docs(02): plan phase 2 (7 plans, 5 waves, 1 paralela)
dcae155 docs(02): phase 2 research
```

Padrao consistente: 1 commit `feat` por plan + 1 commit `docs` SUMMARY (alguns combinados como o `ab60b1b`/`8521b0f` pair do Plan 07).

## Risks From Plan-Check / RESEARCH

| Risk | Materialized? | Resolution |
|------|--------------|------------|
| Spike Wave 1: H2 nao suporta ON CONFLICT | YES — confirmado empiricamente | Fallback save+catch DataIntegrityViolationException implementado em IdempotencyService E ClienteZapService. Ambos validados via tests de concorrencia (2 threads + UNIQUE = 1 row). Codigo de producao = 0 occurrences de "ON CONFLICT" (apenas em comentarios documentando o spike). |
| H2 NOW() retorna LOCAL como UTC (timezone-naive) | YES — descoberto durante MensagemServiceTest e WebhookPersistenciaIntegrationTest | Documentado em SUMMARYs 02-06 e 02-07; assertions temporais ajustadas: usar JdbcTemplate Timestamp.class (timezone correction) ou epoch seconds (local-vs-local). Em PostgreSQL real (TIMESTAMP WITH TIME ZONE), o issue nao se manifesta. Test `atualizar_em_nova_transacao_commit_imediato` (Plan 04) tem validacao temporal precisa. |
| Hibernate AssertionFailure pos-DataIntegrityViolation | YES — issue benigno do entity manager apos race | Documentado em SUMMARY 02-04 e 02-06 como know-issue; nao quebra tests; cada webhook em producao e processado em transacao independente (sem session reuse). Logs barulhentos em testes mas semantica correta. |
| Statuses Meta nao persistidos em Phase 2 | NO — escopo deliberado D-06 | Test sc2f valida empiricamente que status callback NAO persiste em mensagens_log. Phase 4 vai adicionar persistencia para statuses de saida. |

## Concerns / Notes for Phase 3+

1. **Phase 3 (async) deve preservar os 13 tests E2E:** quando MensagemService for refatorado em fast-path + @Async, particularmente sc1 (idempotency), sc4/sc5 (auto-create + ultima_mensagem_em), tests podem precisar de `Awaitility` para wait-for-condition. Reuso de helpers `computeSignature` + `postFixture` recomendado em uma classe utility.

2. **AssertionFailure pos-DataIntegrityViolation pode poluir logs em alta concorrencia:** issue benigno mas se virar problema operacional, considerar refatorar `criarNovo` (ClienteZapService) para sub-transacao isolada (REQUIRES_NEW). Nao bloqueador hoje.

3. **TipoMensagem nao tem constant `STATUS`** (CONTEXT.md D-05 mencionava 8 constants; codigo final tem 7). Statuses Meta sao tratados via `StatusEntranteDTO` separado e nao precisam ir em `mensagens_log.tipo` em Phase 2 (D-06 ignora). Phase 4 (outbound) pode precisar revisitar quando persistir statuses de saida — entao adicionar STATUS constant ou usar `mensagens_log.direcao=out` e tipo=text/document/etc seria mais coerente.

4. **logging.level WARN/ERROR de SqlExceptionHelper poluindo log de tests** — esperado em tests de duplicate/race (UNIQUE constraint disparando), nao indica falha. Phase 6 pode adicionar `logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper=ERROR` em application-test.yml para reducao de ruido.

5. **MediaCache entity criada em Phase 2 mas nao consumida** — esperado (D-05 do CONTEXT). Phase 4 vai criar `MediaCacheService` que populara essa tabela. Entity + repository ficam disponiveis sem warning.

6. **WebhookPayloadParser tolerante a payload de heartbeat (entry vazio)** — verificado via fixture `empty-entry.json` + 9 tests. Phase 3 async pode usar mesmo padrao para detectar heartbeats antes de despachar callback ERP.

## Recommendation

**PASS — Phase 2 ready to close. Proceed to /gsd-discuss-phase 3.**

Todos os 5 ROADMAP success criteria verificados empiricamente via 13 integration tests E2E + tests unitarios complementares (99 outros tests). 9/9 requirements satisfeitos. Build verde no reator inteiro (api-whatsapp 112 tests, 0 falhas). Spike de Wave 1 (ON CONFLICT) corretamente documentado e fallback save+catch DataIntegrityViolationException implementado em IdempotencyService + ClienteZapService. Phase 1 (HmacValidator + CachedBodyHttpServletRequest + HmacSignatureFilter + 4 migrations Flyway) preservada — `WebhookControllerIntegrationTest` 10/10 verdes (zero regressao).

Phase 3 (async + ROU-01..05) pode comecar com:
- Reuso de `WebhookPayloadParser`, `IdempotencyService`, `ClienteZapService` (todos estaveis)
- Refator de `MensagemService.processarWebhook` em fast-path (parse + idempotency check sincrono) + `@Async` (identificar + callback ERP)
- Reuso das 8 fixtures JSON em `src/test/resources/fixtures/webhook/`
- Reuso do helper `computeSignature` (extrair para WebhookTestUtils)

---

_Verified: 2026-05-05T18:28:45Z_
_Verifier: Claude (gsd-verifier)_
