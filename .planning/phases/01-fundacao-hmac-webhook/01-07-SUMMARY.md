---
phase: 01-fundacao-hmac-webhook
plan: 07
subsystem: api-whatsapp
tags: [api-whatsapp, integration-test, mockmvc, phase-gate, end-to-end, springboottest]

# Dependency graph
requires:
  - phase: 01-fundacao-hmac-webhook
    plan: 06
    provides: WebhookController + HmacSignatureFilter + SecurityConfig wired (Wave 6)
provides:
  - WebhookControllerIntegrationTest (@SpringBootTest + @AutoConfigureMockMvc) com 10 cenarios verdes cobrindo os 5 ROADMAP success criteria de Phase 1 end-to-end
  - Confirmacao empirica que MockMvc + @AutoConfigureMockMvc carrega FilterRegistrationBean corretamente (vs webAppContextSetup manual que NAO registra automaticamente — bug detectado no proprio plan)
  - Phase 1 gate fechado: reator inteiro `mvnw verify` BUILD SUCCESS com 127 tests verdes em 27s, 6 modulos verdes (lib-shared 20 + lib-consultas-client 3 + api-email 34 + api-storage 13 + api-consultas 4 + api-whatsapp 53)

affects:
  - Phase 1 100% completa — todos 5 success criteria observable via test output, todos 9 requirements (WEB-01..04, PER-01, CFG-01..04) satisfeitos
  - Phase 2 (WHATS-05..09) — pode comecar com confianca empirica de que HMAC + ApiKey + filter chain do api-whatsapp funcionam end-to-end. Concerns 1-7 do PLAN-06 SUMMARY foram resolvidos.

# Tech tracking
tech-stack:
  added:
    - org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc — registra FilterRegistrationBean automaticamente, vs webAppContextSetup manual que NAO os carrega
    - javax.crypto.Mac + SecretKeySpec — fixture viva para signature HMAC-SHA256 em runtime, alinhada com appSecret de application-test.yml sem hex hardcoded
    - System.currentTimeMillis() para assertion empirica de SC-2 (<1s do ROADMAP)
  patterns:
    - "@SpringBootTest(webEnvironment=MOCK) + @AutoConfigureMockMvc + @ActiveProfiles('test') — slice integration test que carrega ApplicationContext completo (filters reais, properties reais, Flyway aplicado, datasource H2 em modo PG) sem servidor TCP. Reusavel para Phase 2+ quando WhatsAppController internal endpoints precisarem teste end-to-end com filter chain real."
    - "Helper computeSignature(byte[], String) inline no test class — fixture viva (Mac.getInstance(\"HmacSHA256\") + Mac.doFinal(body) + HexFormat.of().formatHex) que produz X-Hub-Signature-256 valido em runtime. Mirror exato do helper de HmacSignatureFilterTest (Wave 6); consistencia entre unit + integration tests."
    - "Test naming convention sc{N}_<comportamento> — gate observavel mapping 1:1 ou N:1 com ROADMAP success criteria, identificacao imediata em surefire output. Reusavel em Phase 6 (QA-01/QA-02) quando integration tests cobrirem demais SC."
    - "MediaType.APPLICATION_JSON + .characterEncoding(StandardCharsets.UTF_8) explicito em MockMvc post — defesa contra default ISO-8859-1 do MockHttpServletRequest. Concern 3 do PLAN-06 SUMMARY resolvido empiricamente: SC-3 portugues passou no primeiro try, gate UTF-8 robusto."

key-files:
  created:
    - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookControllerIntegrationTest.java
    - .planning/phases/01-fundacao-hmac-webhook/01-07-SUMMARY.md
  modified: []
  deleted: []

key-decisions:
  - "@AutoConfigureMockMvc escolhido em vez de MockMvcBuilders.webAppContextSetup manual — descoberta empirica via Rule 1 (bug fix). Primeira tentativa usou webAppContextSetup conforme PLAN-07 risco listado, mas tests sc2_post_com_body_modificado_retorna_401 e sc2_post_sem_signature_retorna_401 retornavam 200 espuriamente apesar do log mostrar 'HMAC invalido ... rejeitado com 401'. Causa raiz: webAppContextSetup NAO registra FilterRegistrationBean automaticamente em todas as versoes do Spring Boot 3.5.x — filters declarativos sao ignorados. @AutoConfigureMockMvc os respeita. Documentado para Phase 2+ usar essa annotation por default em integration tests."
  - "10 cenarios em vez dos 7 do PLAN-07 — Rule 2 add coverage critica: alem dos 7 originais (3 GET + 3 POST core + 1 UTF-8), adicionei (a) sc4 toString masking smoke check porque integration test pode pegar regressao em redaction que unit test nao pega; (b) d02 webhook publico sem X-API-Key — prova explicita do Set.of('/webhook') no ApiKeyFilter (D-02 do CONTEXT.md), gate empirico que detecta se alguem reverter o construtor de 2-arg; (c) /health bonus — verifica que DEFAULT_PUBLIC_PATHS funciona end-to-end."
  - "computeSignature inline em vez de extrair pra util compartilhada — duplicacao de 6 linhas de codigo entre HmacSignatureFilterTest e WebhookControllerIntegrationTest, mas custo zero de manutencao (HMAC-SHA256 e padrao W3C). Util compartilhada criaria dependencia entre packages test/web/ e test/controller/ sem ganho. Reaproveitamento via copy-paste e idiomatico em test fixtures Java."
  - "SC-5 (Flyway V1-V4) sem teste explicito — implicit pelo proprio @SpringBootTest carregar com sucesso. Se Flyway falhasse (migration corrompida, schema inexistente, SQL incompativel com H2 PG-mode), o ApplicationContext NAO sobe e nenhum test roda. FlywayMigrationTest da Wave 4 (6 cenarios) faz introspection direta do schema; este SUMMARY ja documenta cobertura primaria la. Rule 2 considerou um sc5_schema_whatsapp_tem_4_tabelas mas decidi nao adicionar — duplicacao com Wave 4 sem ganho."
  - "Assertion de elapsed via System.currentTimeMillis() em sc2_post_com_hmac_valido_retorna_200_em_menos_de_1s — abordagem simples, sem dependencia adicional (vs JMH/Awaitility). Threshold 1000ms protege contra ambientes lentos; medicao real foi 7ms em CI tipico (XML surefire confirma). Phase 6 (QA-01) podera adicionar assertion mais rigorosa com WireMock + delay no ERP callback (concern documentado como deferred)."

patterns-established:
  - "Integration test com @SpringBootTest + @AutoConfigureMockMvc + filter chain real — padrao default para tests end-to-end de api-whatsapp. Phase 2 controllers (parser/idempotency) e Phase 4 controllers (WhatsAppController internal endpoints) devem usar este padrao."
  - "Test method naming sc{N}_<descricao> ou <feature_id>_<descricao> — quando teste mapeia diretamente a ROADMAP success criterion ou requirement, naming explicito facilita rastreabilidade plan-checker → test → output."
  - "Fixtures vivas via crypto helpers in-class — eliminar hardcoded hex em testes que dependem de secrets. Se appSecret de application-test.yml mudar, tests recalculam signature automaticamente."

requirements-completed:
  - WEB-01  # GET /webhook/whatsapp ecoa hub.challenge plain text + 403 em token errado — sc1_get_handshake_* (3 tests)
  - WEB-02  # POST valida HMAC e retorna 401 se invalido — sc2_post_* (3 tests) + HmacValidatorTest unit (13 tests da Wave 5)
  - WEB-03  # CachedBodyHttpServletRequest preserva bytes brutos UTF-8 — sc3_post_com_payload_portugues_utf8_valida_corretamente
  - WEB-04  # POST retorna 200 em <1s — sc2_post_com_hmac_valido_retorna_200_em_menos_de_1s (medicao empirica 7ms)

# Metrics
duration: ~8min
completed: 2026-05-05
---

# Phase 01 Plan 07: WebhookControllerIntegrationTest — Phase 1 Gate Summary

**Modulo `api-whatsapp` ganha integration test end-to-end fechando Phase 1: `WebhookControllerIntegrationTest` (`@SpringBootTest(webEnvironment=MOCK)` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`) sobe o `WhatsAppApplication` completo com TODOS os filters reais wired via `FilterRegistrationBean` (HmacSignatureFilter HIGHEST_PRECEDENCE + ApiKeyFilter HIGHEST_PRECEDENCE+10) e exercita o stack inteiro: HmacSignatureFilter → CachedBodyHttpServletRequest → HmacValidator → ApiKeyFilter → WebhookController. 10 cenarios verdes em ~4.5s cobrindo os 5 ROADMAP success criteria de Phase 1 + 2 bonus (D-02 webhook publico de API key, /health endpoint publico). Helper inline `computeSignature(byte[], String)` produz fixture viva via `Mac.getInstance("HmacSHA256")` automaticamente alinhada com `appSecret = "test-app-secret"` de `application-test.yml` — sem hex hardcoded que rebem se algo mudar. Phase gate empirico: `mvnw verify` (root) BUILD SUCCESS — **127 tests verdes em 27s, 6 modulos com zero regressao** (lib-shared 20 + lib-consultas-client 3 + api-email 34 + api-storage 13 + api-consultas 4 + api-whatsapp 53). Suite api-whatsapp: 43 tests da Wave 6 + 10 novos = 53. Bug detectado e corrigido no proprio plan via Rule 1: primeira tentativa com `MockMvcBuilders.webAppContextSetup` manual fazia `sc2_post_sem_signature_retorna_401` retornar 200 espuriamente apesar do log mostrar "HMAC invalido ... rejeitado com 401" — `webAppContextSetup` NAO registra automaticamente `FilterRegistrationBean` em todas as versoes do Spring Boot 3.5.x; troca para `@AutoConfigureMockMvc` resolveu (gate empirico de "filters customizados precisam ser registrados via auto-config, nao via builder manual"). **Phase 1 100% completa — todos 9 requirements (WEB-01..04, PER-01, CFG-01..04) satisfeitos observavelmente, todos 5 ROADMAP success criteria verificados via test output, pronto para `/gsd-verify-phase 1` e `/gsd-transition`.**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-05-05T07:42:20Z
- **Completed:** 2026-05-05T07:50:00Z (approx)
- **Tasks:** 3 (Task 1 WebhookControllerIntegrationTest + Task 2 build verify api-whatsapp + Task 3 verify reator inteiro)
- **Files created:** 2 (1 test + 1 SUMMARY)
- **Files modified:** 0
- **Files deleted:** 0
- **Tests:** 10 novos (todos verdes em 4.459s)
- **Suite api-whatsapp:** 53 tests (43 antes + 10 novos)
- **Reator inteiro:** 127 tests verdes em 27s
- **Build time:** ~13s api-whatsapp / ~27s reator inteiro

## Mapeamento Test → ROADMAP Success Criterion

| ROADMAP SC (Phase 1) | Test method | Elapsed (XML surefire) |
|----------------------|-------------|------------------------|
| SC-1 (GET hub.challenge plain text + 403) | `sc1_get_handshake_com_token_correto_retorna_challenge_plain_text` | 4ms |
| SC-1 (verifyToken errado → 403) | `sc1_get_handshake_com_token_errado_retorna_403` | 17ms |
| SC-1 (mode != subscribe → 403) | `sc1_get_handshake_com_mode_diferente_de_subscribe_retorna_403` | 4ms |
| SC-2 (HMAC valido em <1s) | `sc2_post_com_hmac_valido_retorna_200_em_menos_de_1s` | **7ms** (gate <1000ms) |
| SC-2 (body modificado 1 byte → 401) | `sc2_post_com_body_modificado_retorna_401` | 15ms |
| SC-2 (sem header signature → 401) | `sc2_post_sem_signature_retorna_401` | 5ms |
| SC-3 (UTF-8 portugues `Olá, gostaria de um orçamento` + HMAC valido → 200) | `sc3_post_com_payload_portugues_utf8_valida_corretamente` | 5ms |
| SC-4 (toString mascara secrets) | `sc4_secrets_nao_aparecem_no_toString_das_properties` | 3ms |
| SC-4 (boot fail-fast) | _Cobertura primaria: WhatsAppPropertiesValidationTest (Wave 3, 6 tests) — implicito via @SpringBootTest carregar_ | n/a |
| SC-5 (Flyway V1-V4) | _Cobertura primaria: FlywayMigrationTest (Wave 4, 6 tests) — implicito via @SpringBootTest carregar com schema valido_ | n/a |
| **Bonus D-02** (webhook publico de API key) | `d02_webhook_passa_sem_api_key_quando_hmac_valido` | 19ms |
| **Bonus** (/health publico) | `health_endpoint_publico_retorna_200_sem_api_key` | 7ms |

**Total elapsed por categoria:**
- 3 tests SC-1 (GET handshake): ~25ms
- 3 tests SC-2 (POST HMAC): ~27ms
- 1 test SC-3 (UTF-8 portugues): ~5ms
- 1 test SC-4 (toString redaction): ~3ms
- 2 tests bonus: ~26ms
- **Soma: ~86ms** (test class total: 4.459s, dominado pelo @SpringBootTest context startup ~3.6s)

## PITFALLS Coverage Validacao

| PITFALL ID | Descricao | Como validado em Wave 7 |
|-----------|-----------|--------------------------|
| C-02 (HMAC body consumed before filter) | Body lido antes do filter validar | `sc3_post_com_payload_portugues_utf8_valida_corretamente` — request com body UTF-8 nao trivial passa pelo HmacSignatureFilter (HIGHEST_PRECEDENCE) que embrulha em CachedBodyHttpServletRequest, valida HMAC, depois deixa o body cacheado disponivel para o controller. End-to-end empirico. |
| C-03 (HMAC timing attack via String.equals) | `MessageDigest.isEqual` constant-time | Cobertura primaria: HmacValidatorTest (13 tests Wave 5). Wave 7 valida o caminho real chamado pelo filter via fixture viva. |
| C-04 (Unicode charset perdido) | UTF-8 raw bytes preservados | `sc3_post_com_payload_portugues_utf8_valida_corretamente` — `"Olá, gostaria de um orçamento"` + `MediaType.APPLICATION_JSON` + `.characterEncoding(StandardCharsets.UTF_8)`. Se o filter convertesse via `String` em algum ponto, signature falharia (ISO-8859-1 → UTF-8 expand bytes). Gate empirico cumprido. |
| C-10 (hub.challenge JSON-wrapped) | Plain text response | `sc1_get_handshake_com_token_correto_retorna_challenge_plain_text` — `content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)` + `content().string("challenge-12345")` LITERAL (sem aspas, sem JSON). Gate strict. |
| C-11 (verifyToken em access logs) | `accesslog.enabled: false` | Cobertura defensiva via inspecao do `application.yml` (Wave 2-4); Wave 7 nao roda Tomcat real, apenas mock servlet — concern documentado para Phase 6 com smoke test em ambiente real. |

## Accomplishments

- **`WebhookControllerIntegrationTest.java` (271 linhas)** em `br.com.erpkit.whatsapp.controller` — Integration test end-to-end com 10 cenarios:
  - 3 SC-1 (GET handshake): verifyToken correto retorna 200 + text/plain + body LITERAL "challenge-12345"; verifyToken errado 403; mode != subscribe 403.
  - 3 SC-2 (POST HMAC): HMAC valido retorna 200 com elapsed <1s (medido via System.currentTimeMillis); body modificado 1 byte retorna 401 + ErrorResponse JSON com "Assinatura HMAC invalida"; sem header X-Hub-Signature-256 retorna 401.
  - 1 SC-3 (UTF-8 portugues): payload Meta-realistic com `"Olá, gostaria de um orçamento"` + HMAC valido + characterEncoding UTF-8 explicito retorna 200.
  - 1 SC-4 (toString redaction): `properties.toString()` nao contem accessToken/appSecret/verifyToken literais; contem `[REDACTED]` marker; contem nao-secrets phoneNumberId/erpCallbackUrl.
  - 2 Bonus: D-02 webhook publico (POST com HMAC valido sem X-API-Key retorna 200, prova `Set.of("/webhook")` no ApiKeyFilter); /health publico (GET /health retorna 200 + JSON `{status:UP, modulo:api-whatsapp}` sem X-API-Key).
  - Helper `computeSignature(byte[], String)` inline produz X-Hub-Signature-256 valido via `Mac.getInstance("HmacSHA256")` + `HexFormat.of().formatHex` — fixture viva alinhada com `appSecret = "test-app-secret"` de application-test.yml.
  - JavaDoc da classe explica decisao de `@AutoConfigureMockMvc` vs `webAppContextSetup` manual (bug detectado e corrigido).

- **Reator inteiro BUILD SUCCESS** — 127 tests verdes em 27s. Zero regressao vs Wave 6 baseline (117 tests). +10 = WebhookControllerIntegrationTest.

- **Suite api-whatsapp: 53 tests verdes em ~7s.** Distribuicao: WhatsAppPropertiesValidationTest 6 + WhatsAppPropertiesHappyPathTest 1 + FlywayMigrationTest 6 + HmacValidatorTest 13 + CachedBodyHttpServletRequestTest 6 + HmacSignatureFilterTest 6 + WebhookControllerTest 4 + HealthControllerTest 1 + WebhookControllerIntegrationTest **10**.

## Task Commits

1. **Task 1 + Task 2 + Task 3 (atomico):** `test(api-whatsapp): adicionar integration test end-to-end fechando Phase 1` — commit `2c297c0`
   - 1 arquivo: `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookControllerIntegrationTest.java`
   - Pos-commit deletion check: 0 deletions

2. **SUMMARY metadata:** commit pendente (proximo passo apos este file ser escrito).

## Files Created/Modified

- **`api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookControllerIntegrationTest.java`** (NEW, 271 linhas) — Integration test @SpringBootTest + @AutoConfigureMockMvc com 10 cenarios end-to-end. JavaDoc explica escolha da annotation, helper computeSignature, mapping test → SC.
- **`.planning/phases/01-fundacao-hmac-webhook/01-07-SUMMARY.md`** — este arquivo.

## Decisions Made

- **@AutoConfigureMockMvc em vez de MockMvcBuilders.webAppContextSetup manual** — Rule 1 (bug fix) na primeira tentativa. webAppContextSetup NAO registra FilterRegistrationBean automaticamente em Spring Boot 3.5.x — POST sem signature retornava 200 espuriamente apesar do log "HMAC invalido". @AutoConfigureMockMvc respeita os FilterRegistrationBean de SecurityConfig. Padrao reusavel para Phase 2+.
- **10 cenarios vs 7 do PLAN-07** — Rule 2 add coverage critica: sc4 toString masking, d02 webhook publico, /health smoke. Cada um cobre uma decisao arquitetural (CFG-03 redaction, D-02 publicPaths, DEFAULT_PUBLIC_PATHS) que poderia regredir silenciosamente.
- **computeSignature inline em vez de util compartilhada** — duplicacao de 6 linhas com HmacSignatureFilterTest, mas custo zero de manutencao (padrao W3C). Reaproveitamento por copy-paste e idiomatico em test fixtures Java.
- **SC-5 sem test explicito** — coberto implicitamente pelo @SpringBootTest carregar com schema Flyway aplicado. Wave 4 FlywayMigrationTest (6 tests) faz introspection rigorosa.
- **Assertion elapsed <1000ms via System.currentTimeMillis()** — simples, sem dependencia (vs Awaitility/JMH). Threshold 1000ms protege contra ambientes lentos; medicao real 7ms confirma margem amplisima.

## Deviations from Plan

**1. [Rule 1 - Bug] MockMvcBuilders.webAppContextSetup nao registrava FilterRegistrationBean — troca para @AutoConfigureMockMvc**
- **Found during:** Task 1 — primeira execucao do test (`./mvnw clean verify -pl api-whatsapp -am -q`) com 8/10 verdes.
- **Issue:** Tests `sc2_post_com_body_modificado_retorna_401` e `sc2_post_sem_signature_retorna_401` retornavam 200 em vez de 401, apesar do log capturar `HmacSignatureFilter : HMAC invalido em POST /webhook/whatsapp - rejeitado com 401`. Causa raiz: `MockMvcBuilders.webAppContextSetup(context).build()` carrega a `WebApplicationContext` mas NAO registra automaticamente filters declarados via `@Bean FilterRegistrationBean<T>` em `SecurityConfig` — os filters customizados sao ignorados pelo MockMvc. Concern listado nos `<risks>` do PLAN-07 ("MockMvc + filters customizados drift") confirmado empiricamente.
- **Fix:** Trocar para `@AutoConfigureMockMvc` (Spring Boot annotation) que injeta o `MockMvc` ja configurado com os FilterRegistrationBean de SecurityConfig. Removido `WebApplicationContext context` field, `@BeforeEach setUp()` method, `MockMvcBuilders.webAppContextSetup` chamada. Adicionado `@Autowired private MockMvc mockMvc` direto. Re-run: 10/10 tests verdes.
- **Files modified:** WebhookControllerIntegrationTest.java
- **Commit:** `2c297c0`

**2. [Rule 2 - Add critical coverage] 3 tests adicionais alem dos 7 do PLAN-07**
- **Found during:** Task 1 (escrita do test) — analise dos 5 ROADMAP success criteria mostrou que SC-4 (boot fail-fast + secrets nao em logs) tem cobertura primaria em WhatsAppPropertiesValidationTest mas a parte "secrets nao em logs" NAO tinha smoke test explicito. Similarmente, D-02 (Set.of("/webhook") no ApiKeyFilter) e /health (DEFAULT_PUBLIC_PATHS) sao decisoes arquiteturais que podem regredir silenciosamente sem teste explicito.
- **Issue:** Sem o sc4 toString smoke, alguem alterando o `toString()` de WhatsAppProperties (ex: removendo a `[REDACTED]` substitution) poderia passar PR sem failure. Sem o d02_webhook_passa_sem_api_key, alguem revertendo o construtor de 2-arg do ApiKeyFilter ou removendo o `Set.of("/webhook")` em SecurityConfig poderia passar PR sem failure (Wave 6 unit tests do filter nao pegam isso).
- **Fix:** Adicionar 3 tests: `sc4_secrets_nao_aparecem_no_toString_das_properties`, `d02_webhook_passa_sem_api_key_quando_hmac_valido`, `health_endpoint_publico_retorna_200_sem_api_key`. Total tests: 7 plan + 3 add = 10. Custo: ~30 linhas, ~30ms elapsed adicional.
- **Files modified:** WebhookControllerIntegrationTest.java (cenarios extras incluidos no commit unico).
- **Commit:** `2c297c0`

Nenhum desvio Rule 3 (build verde sem blocking issues) nem Rule 4 (sem mudanca arquitetural).

## Issues Encountered

- **Tentativa #1 com webAppContextSetup falhou em 2 dos 10 tests** — round-trip 1: identifiquei o problema via comparacao do log (filter rodou) vs status (200 espurio), troca para @AutoConfigureMockMvc resolveu. Custo: ~3min. Documentado como pattern-established + decision para Phase 2+.
- **Maven `-pl api-whatsapp -am` precisa de `-Dsurefire.failIfNoSpecifiedTests=false`** — sem isso, surefire dos modulos lib-shared/api-email/etc. falham com "No tests matching pattern WebhookControllerIntegrationTest were executed". Ja documentado para futuras waves.

## Verification Performed

| Check | Comando | Resultado |
|-------|---------|-----------|
| WebhookControllerIntegrationTest run isolado | `./mvnw clean test -pl api-whatsapp -am -Dtest=WebhookControllerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false` | Tests run: 10, Failures: 0, Errors: 0 |
| api-whatsapp suite completa | `./mvnw clean verify -pl api-whatsapp -am` | BUILD SUCCESS — 53 tests verdes, ~13s |
| Reator inteiro | `./mvnw verify` | BUILD SUCCESS — 127 tests verdes em ~27s. Zero regressao vs Wave 6 baseline (117 tests). +10 = WebhookControllerIntegrationTest |
| SC-2 elapsed <1s gate | XML surefire `time="0.007"` | 7ms (gate <1000ms) — margem 142x |
| Test com payload portugues UTF-8 valido | `sc3_post_com_payload_portugues_utf8_valida_corretamente` | PASS — body com `Olá, gostaria de um orçamento` mais 9999... + HMAC valido retorna 200 |
| Filter chain real wired | Logs surefire `HmacSignatureFilter : HMAC invalido em POST /webhook/whatsapp - rejeitado com 401` aparecem 3x (sc2_body_modificado, sc2_sem_signature, mais um) | Confirma que filter foi invocado pelo MockMvc |
| Pos-commit deletion check | `git diff --diff-filter=D --name-only HEAD~1 HEAD` | 0 deletions |
| Commit existe | `git log --oneline 2c297c0` | OK — 1 arquivo no commit |

## Threat Model Compliance

Per `<threat_model>` do PLAN-07:

| Threat ID | Mitigation enforced | Test |
|-----------|---------------------|------|
| T-07-01 (Spoofing: @WebMvcTest em vez de @SpringBootTest) | Anotacao `@SpringBootTest(classes = WhatsAppApplication.class, webEnvironment = MOCK)` + `@AutoConfigureMockMvc` explicita; JavaDoc da classe documenta decisao. Se algum dev futuro tentar reescrever pra `@WebMvcTest` por "performance", os tests `sc2_post_*_retorna_401` falharao (filter nao roda → 200), gate empirico. |
| T-07-02 (Tampering: signature hardcoded fica stale) | Helper `computeSignature(byte[], String)` computa em runtime via Mac.getInstance("HmacSHA256") com `properties.getAppSecret()` (lido de application-test.yml). Sem hex hardcoded — automaticamente alinhada se appSecret mudar. |
| T-07-03 (InfoDisclosure: appSecret real em logs) | accept — appSecret de test e "test-app-secret" (dummy de application-test.yml). Nao e Meta secret real. Production usa `${WHATSAPP_APP_SECRET}` env var. |
| T-07-04 (DoS: multiple SpringBootTest contexts) | Cache do Spring reusa contexto entre @SpringBootTest com mesma config (FlywayMigrationTest, WhatsAppPropertiesHappyPathTest, WebhookControllerIntegrationTest). Tempo total api-whatsapp ~13s — aceitavel. |

Todas as 4 threat IDs cobertas (2 mitigate + 2 accept). T-07-01 e T-07-02 sao gates empiricos que detectariam regressao.

## Risks Resolved

- **MockMvc + filters customizados drift** (PLAN-07 risks list) — RESOLVIDO PARCIALMENTE via Rule 1: `webAppContextSetup` confirmado NAO registra FilterRegistrationBean automaticamente em Spring 3.5.x (vs documentacao implicita); `@AutoConfigureMockMvc` resolve. Documentado como decision + pattern-established.
- **Time-out / contexto Spring lento** — NAO REALIZADO. 4 test classes do api-whatsapp com @SpringBootTest carregam contexto em ~13s total (cache do Spring reusa entre tests com mesma config). Aceitavel.
- **A4 do RESEARCH (@RequestParam("hub.mode") com ponto)** — JA RESOLVIDA POSITIVAMENTE em Wave 6; reconfirmada em Wave 7 via 3 tests SC-1 GET handshake usando `param("hub.mode", ...)` e `param("hub.verify_token", ...)`. Spring 3.5.9 funciona.
- **content().string(...) vs equalTo(...)** (PLAN-07 risks list) — `content().string("challenge-12345")` (assertion exata) usado, sem whitespace/newline issues observados. Spring `ResponseEntity.ok(challenge)` produz body literal sem ruido.
- **HexFormat lowercase vs uppercase** — Java `HexFormat.of()` default e lowercase, alinhado com Meta. Confirmado empiricamente — signatures computadas casam com `HmacValidator.isValid` em runtime.

## Phase 1 Final Status

**Phase 1 — Fundacao HMAC + Webhook: COMPLETA (7/7 plans)**

| Wave | Plan | Status | Commit | Tests |
|------|------|--------|--------|-------|
| 1 | 01-01 | Complete | 1b72009 | lib-shared/ApiKeyFilter 2-arg constructor |
| 2 | 01-02 | Complete | 78c7716 | api-whatsapp skeleton Maven |
| 3 | 01-03 | Complete | 7fd5c8c | WhatsAppProperties fail-fast (7 tests) |
| 4 | 01-04 | Complete | febb68b | Flyway V1-V4 + datasource (6 tests) |
| 5 | 01-05 | Complete | ca877bb | HmacValidator + CachedBodyHttpServletRequest (19 tests) |
| 6 | 01-06 | Complete | 51ba38a | HmacSignatureFilter + SecurityConfig + Webhook/Health Controllers (11 tests) |
| 7 | 01-07 | Complete | 2c297c0 | WebhookControllerIntegrationTest end-to-end (10 tests) |

**ROADMAP Phase 1 Success Criteria: 5/5 satisfeitos observavelmente**

| SC | Status | Cobertura |
|----|--------|-----------|
| 1. GET hub.challenge plain text + 403 | OK | sc1_get_handshake_* (3 tests) + WebhookControllerTest (3 tests Wave 6) |
| 2. POST HMAC valido 200 em <1s + body modificado 401 + MessageDigest.isEqual | OK | sc2_post_* (3 tests, elapsed 7ms) + HmacValidatorTest (13 tests Wave 5) + HmacSignatureFilterTest (6 tests Wave 6) |
| 3. CachedBodyHttpServletRequest eager + UTF-8 portugues | OK | sc3_post_com_payload_portugues_utf8 + CachedBodyHttpServletRequestTest (6 tests Wave 5) |
| 4. Boot fail-fast + secrets nao em logs | OK | WhatsAppPropertiesValidationTest (6 tests Wave 3) + sc4 smoke |
| 5. Flyway V1-V4 + mvnw verify verde | OK | FlywayMigrationTest (6 tests Wave 4) + reator inteiro BUILD SUCCESS |

**REQUIREMENTS satisfeitos: 9/9**

| ID | Status | Phase | Cobertura |
|----|--------|-------|-----------|
| WEB-01 | Complete | 1 | sc1_get_handshake_* + WebhookControllerTest |
| WEB-02 | Complete | 1 | sc2_post_com_hmac_valido + HmacValidatorTest + HmacSignatureFilterTest |
| WEB-03 | Complete | 1 | sc3_post_com_payload_portugues_utf8 + CachedBodyHttpServletRequestTest |
| WEB-04 | Complete | 1 | sc2_post_com_hmac_valido_retorna_200_em_menos_de_1s |
| PER-01 | Complete | 1 | FlywayMigrationTest (schema 'whatsapp' criado pela V1) |
| CFG-01 | Complete | 1 | WhatsAppPropertiesValidationTest |
| CFG-02 | Complete | 1 | application.yml com placeholders ${WHATSAPP_*:} (Wave 2-4) |
| CFG-03 | Complete | 1 | sc4_secrets_nao_aparecem_no_toString + WhatsAppPropertiesHappyPathTest |
| CFG-04 | Complete | 1 | application.yml `server.port: ${SERVER_PORT:9193}` (Wave 2) |

**Total tests Phase 1: 53 em api-whatsapp + 14 em lib-shared (ApiKeyFilter inclui o 2-arg do PLAN-01) + outros modulos = 127 tests no reator inteiro.**

**Phase 1 ready for `gsd-verifier` (gate: ROADMAP SC 1-5 satisfeitos + 9 REQUIREMENTS satisfeitos + reator BUILD SUCCESS).**

## Concerns para Phase 2 (PER-02..07 + WEB-05..07)

1. **WhatsAppController POST stub atual (Wave 6) sera substituido em Phase 2** — parser do payload Meta (`message.text`, `message.interactive.button_reply`, `message.interactive.list_reply`, `message.document`, `statuses.status`) substitui o stub `ResponseEntity.ok().build()`. O HMAC + filter chain wired em Wave 6/7 NAO precisa mudar — Phase 2 apenas adiciona o parser apos o filter validar.

2. **`CachedBodyHttpServletRequest` ja preserva bytes brutos** — Phase 2 `@RequestBody String corpo` ou `@RequestBody WebhookPayloadDTO` no controller pode ler o body novamente porque o wrapper esta downstream do HMAC filter. Confirmado empiricamente em `post_com_hmac_valido_passa` (Wave 6) onde `chain.getRequest()` foi `CachedBodyHttpServletRequest`.

3. **IdempotencyService (WEB-05/06) — UNIQUE wamid + DataIntegrityViolationException** ja documentado em PITFALLS C-08. Phase 2 implementa.

4. **WhatsAppCommandHandler SPI (Phase 5)** — Phase 2 tambem deve garantir que o parser de payload extrai informacoes de identidade (telefone do `from`) que o ClienteZapService usa em PER-05/06 (normalizacao de telefone BR + resolver id_cliente_erp).

5. **Schema validation Hibernate** — Wave 4 deixou `ddl-auto: validate` ativado mas sem entities. Phase 2 adiciona @Entity para `mensagens_log`, `clientes_zap`, `media_cache`, `estado_conversa`. Quando entity nao bater com schema, Hibernate falha boot. Wave 4 FlywayMigrationTest fixou schema; Phase 2 entities tem que casar 1:1 com migrations.

6. **Phase 1 nao toca cross-repo (ERP-MUDAS, installer)** — confirmado. Phase 7+ ou outro GSD project endereca.

## Self-Check: PASSED

- [x] `WebhookControllerIntegrationTest.java` existe em `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/`
- [x] 10 cenarios cobrindo SC-1 (3) + SC-2 (3) + SC-3 (1) + SC-4 (1) + bonus (2)
- [x] @SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("test") declarados
- [x] Helper `computeSignature(byte[], String)` inline com Mac.getInstance("HmacSHA256")
- [x] Body LITERAL "challenge-12345" assertion exata em sc1_get_handshake_*_correto
- [x] content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN) em sc1
- [x] System.currentTimeMillis() + assertThat(elapsed).isLessThan(1000L) em sc2_*_em_menos_de_1s
- [x] Payload `Olá, gostaria de um orçamento` UTF-8 em sc3
- [x] characterEncoding(StandardCharsets.UTF_8) em sc3 (concern Wave 6 SUMMARY resolvido)
- [x] sc4 valida toString nao contem accessToken/appSecret/verifyToken; contem [REDACTED]
- [x] d02 valida POST /webhook sem X-API-Key retorna 200 com HMAC valido
- [x] /health bonus retorna 200 + JSON {status:UP, modulo:api-whatsapp}
- [x] WebhookControllerIntegrationTest run: Tests run: 10, Failures: 0, Errors: 0, Time elapsed: 4.459s
- [x] api-whatsapp suite: 53 tests verdes
- [x] Reator inteiro: BUILD SUCCESS — 127 tests verdes em 27s
- [x] SC-2 elapsed real: 7ms (gate <1000ms — margem 142x)
- [x] Commit `2c297c0` existe no historico (`git log --oneline -3` confirma)
- [x] Pos-commit deletion check: 0 deletions
- [x] Phase 1 100% completa (7/7 plans + 5/5 SC + 9/9 REQUIREMENTS)

---
*Phase: 01-fundacao-hmac-webhook*
*Completed: 2026-05-05*
*Status: Phase 1 COMPLETA — pronto para `/gsd-verify-phase 1` e `/gsd-transition`*
