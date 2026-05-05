---
phase: 4
slug: outbound-trava-24h-whatsappcontroller
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-05
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source: `04-RESEARCH.md` §Validation Architecture (Nyquist).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + AssertJ + WireMock 3.10.0 (via spring-boot-starter-test 3.5.9) |
| **Config file** | `api-whatsapp/src/test/resources/application-test.yml` |
| **Quick run command** | `./mvnw -pl api-whatsapp test -Dtest='<TestClass>'` |
| **Full suite command** | `./mvnw -pl api-whatsapp verify` |
| **Estimated runtime** | ~30–60s (api-whatsapp aggregate); ~2–3min (reator inteiro) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw -pl api-whatsapp test -Dtest='<TestClass>'` (single class, ~5–15s)
- **After every plan wave:** Run `./mvnw -pl api-whatsapp verify` (api-whatsapp aggregate)
- **Before `/gsd-verify-work`:** `./mvnw verify` reator inteiro BUILD SUCCESS — todos os 7 modulos verde, zero regressao em Phase 1+2+3 (152 tests existentes + ~30–40 novos Phase 4)
- **Max feedback latency:** 60 segundos

---

## Per-Task Verification Map

> Per-task IDs (`{N}-{plan}-{task}`) are filled by gsd-planner; rows below map success criteria + REQ-IDs to test classes/commands. Status flips to ✅ when the test class lands and passes.

| Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 04-04 | 1 | SC-1 / OUT-05 | C-09 / V7 | Sem `enviarTemplate` no codigo do client; sem stack de Bearer em log | grep gate + reflection unit | `grep -rn 'enviarTemplate\|"template"' api-whatsapp/src/main/java/.../service/WhatsAppCloudClient.java` + `WhatsAppCloudClientTest.metodos_publicos_nao_inclui_template` | ❌ W0 | ⬜ pending |
| 04-02 / 04-05 | 2 | SC-2 / OUT-06 + OUT-07 | C-01 (TOCTOU 24h) | 409 + `JANELA_24H_FECHADA` antes de chamar Cloud API; commit read fora da txn | unit + @SpringBootTest aspect | `./mvnw -pl api-whatsapp test -Dtest='WindowEnforcementServiceTest+WhatsAppControllerTest#janela_fechada_retorna_409'` | ❌ W0 | ⬜ pending |
| 04-03 | 1 | SC-3 / OUT-08 | V12 (file size limits) | enviarDocumento mesmo PDF 2x → 1 upload; expirado → reupload; race save+catch | unit | `./mvnw -pl api-whatsapp test -Dtest='MediaCacheServiceTest'` | ❌ W0 | ⬜ pending |
| 04-04 | 1 | SC-4a / OUT-10 | V7 (no retry on 4xx) | 4xx (400/401/403) NAO retenta + log estruturado com `meta_error_code` | integration WireMock | `./mvnw -pl api-whatsapp test -Dtest='WhatsAppCloudClientTest#quatrocentos_no_retry'` | ❌ W0 | ⬜ pending |
| 04-04 | 1 | SC-4b / OUT-10 | — | 5xx + timeout retentam 3x exponencial (1s/2s/4s); `ResourceAccessException` em retry-exceptions | integration WireMock | `./mvnw -pl api-whatsapp test -Dtest='WhatsAppCloudClientTest#cinquecentos_recupera+timeout_retry'` | ❌ W0 | ⬜ pending |
| 04-04 | 1 | SC-4c / OUT-10 | C-09 / C-14 (Bearer leak) | Bearer NUNCA em log nem em query param; sempre header per-request | grep gate + WireMock event scan | `./mvnw -pl api-whatsapp test -Dtest='WhatsAppCloudClientTest#bearer_nunca_em_query_param'` | ❌ W0 | ⬜ pending |
| 04-04 / 04-05 | 2 | SC-5 / OUT-09 + OUT-11 | — | Outbound persiste com `direcao=out` + wamid; 200 OK retornado | @WebMvcTest + JdbcTemplate assertion | `./mvnw -pl api-whatsapp test -Dtest='WhatsAppControllerTest+WhatsAppCloudClientTest'` | ❌ W0 | ⬜ pending |
| 04-04 | 1 | OUT-01 | — | enviarTexto chama POST /messages JSON `type=text` | WireMock matching | (incluso no WhatsAppCloudClientTest) | ❌ W0 | ⬜ pending |
| 04-04 | 1 | OUT-02 | C-15 (multipart fields) | enviarDocumento upload (multipart 3 fields) + send 2-step | WireMock 2 stubs | (incluso no WhatsAppCloudClientTest) | ❌ W0 | ⬜ pending |
| 04-05 | 2 | OUT-03 | V5 (input validation) | enviarBotoes max 3 / falha early 400 | unit Bean Validation | `./mvnw -pl api-whatsapp test -Dtest='*ValidationTest'` | ❌ W0 | ⬜ pending |
| 04-05 | 2 | OUT-04 | V5 (input validation) | enviarLista max 10 itens cross-secao via `@AssertTrue` | unit Bean Validation | (igual OUT-03) | ❌ W0 | ⬜ pending |
| 04-02 | 1 | OUT-07 | C-01 / Aspect order | aspect intercepta apenas 1 vez em scenario de 3 retries | integration spy + WireMock | `./mvnw -pl api-whatsapp test -Dtest='JanelaEnforcementAspectTest'` | ❌ W0 | ⬜ pending |
| 04-05 | 2 | OUT-11 | V4 (API key) | 5 endpoints + GET /status retornam shape correto | @WebMvcTest 5 happy + 4 erros + 1 status | `./mvnw -pl api-whatsapp test -Dtest='WhatsAppControllerTest'` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/WhatsAppCloudClientTest.java` — covers SC-1, SC-4a, SC-4b, SC-4c, SC-5, OUT-01..04, OUT-08, OUT-10
- [ ] `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/MediaCacheServiceTest.java` — covers SC-3, OUT-08
- [ ] `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/WindowEnforcementServiceTest.java` — covers SC-2, OUT-06
- [ ] `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/aspect/JanelaEnforcementAspectTest.java` — covers SC-2, OUT-07 (counter==1 em 3 retries)
- [ ] `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WhatsAppControllerTest.java` — covers OUT-11, SC-2, SC-5 + validation 400 paths
- [ ] (opcional) `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/dto/EnviarBotoesRequestValidationTest.java` + `EnviarListaRequestValidationTest.java` — covers OUT-03, OUT-04 isoladamente (Bean Validation programatico)
- [ ] Framework install: nenhum — toda dependencia ja em pom.xml (Boot 3.5.9 + Resilience4j 2.2.0 + WireMock 3.10.0 + spring-boot-starter-aop)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| E2E real com WABA Meta (numero verificado) | OUT-01..05 (real) | Verificacao Meta Business e dependencia externa (dias/semanas), fora desta milestone | Milestone seguinte (D7) |
| Operador valida `/status` em piloto MUDAS | SC-5 / OUT-11 | Feedback subjetivo de utilidade do shape minimal (D-04) — alvo de Phase 6 expansao se feedback pedir | Apos deploy piloto, operador acessa `GET /api/whatsapp/status` e confirma `phoneNumberId` bate com env |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags (Maven Surefire em modo single-shot)
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
