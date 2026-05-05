---
phase: 01-fundacao-hmac-webhook
plan: 06
subsystem: api-whatsapp
tags: [api-whatsapp, filter, controller, webhook, security-config, hmac, integration]

# Dependency graph
requires:
  - phase: 01-fundacao-hmac-webhook
    plan: 05
    provides: HmacValidator + CachedBodyHttpServletRequest (componentes-fundacao prontos para wire-up)
provides:
  - HmacSignatureFilter (OncePerRequestFilter) registrado em /webhook/* com Ordered.HIGHEST_PRECEDENCE
  - SecurityConfig com 2 FilterRegistrationBean — HMAC ordem HIGHEST_PRECEDENCE; ApiKeyFilter ordem HIGHEST_PRECEDENCE+10 com Set.of("/webhook") como path publico extra
  - WebhookController GET /webhook/whatsapp produces TEXT_PLAIN_VALUE (PITFALLS C-10) — verifyToken comparado via MessageDigest.isEqual UTF-8
  - WebhookController POST /webhook/whatsapp = stub minimo D-04 (ResponseEntity.ok().build(), sem parsing, sem log de body)
  - HealthController GET /health — JSON {status:UP, modulo:api-whatsapp}, sem Spring Boot Actuator
  - 11 tests novos (6 HmacSignatureFilter unit + 4 WebhookController WebMvcTest + 1 HealthController WebMvcTest) — verdes
  - Confirmacao empirica: @RequestParam("hub.mode") com ponto literal funciona em Spring Boot 3.5.9 (resolve Assumption A4 do RESEARCH)

affects:
  - 01-fundacao-hmac-webhook Wave 7 (PLAN-07) — integration test MockMvc end-to-end pode wirar SecurityConfig completo via @SpringBootTest e exercitar HMAC + ApiKeyFilter juntos no fluxo real
  - Phase 2 (WHATS-05..09) — POST stub sera substituido por parser + idempotency + dispatch async, mantendo o wire-up de HMAC inalterado

# Tech tracking
tech-stack:
  added:
    - org.springframework.web.filter.OncePerRequestFilter — superclass do HmacSignatureFilter
    - org.springframework.boot.web.servlet.FilterRegistrationBean — registracao programatica com order + url patterns
    - org.springframework.test.web.servlet.MockMvc + WebMvcTest slice — testes de controller leves sem subir aplicacao inteira
    - org.springframework.mock.web.MockFilterChain — testes unitarios do filter sem MockMvc
  patterns:
    - "Filter HIGHEST_PRECEDENCE + service injetado — gate de seguranca aplicado antes do MVC pipeline (PITFALLS C-02). HmacSignatureFilter delega para HmacValidator (pure function) preservando testabilidade unitaria de ambos"
    - "ApiKeyFilter com construtor 2-arg (lib-shared, PLAN-01) + Set.of('/webhook') — path publico declarado declarativamente, sem hardcode no filter; permite outros modulos seguirem o mesmo pattern (webhooks de payment provider, OAuth callbacks, etc.)"
    - "@WebMvcTest + @ActiveProfiles('test') — slice test carrega application-test.yml (com dummy values dos 5 secrets) sem subir Flyway/JPA. Necessario porque @EnableConfigurationProperties no WhatsAppApplication ativa Bean Validation que rejeita placeholders vazios"
    - "@RequestParam com nome literal contendo ponto (hub.mode, hub.verify_token, hub.challenge) — Spring 3.5.9 suporta nativamente, sem precisar de fallback HttpServletRequest.getParameter. Confirmado empiricamente nos 4 tests do WebhookController"
    - "Filter detection de IOException no construtor de CachedBodyHttpServletRequest — converte para 400 (vs 401) para distinguir 'body ilegivel' de 'HMAC invalido'. Defesa em profundidade alem do que RESEARCH §3.2 cobria"

key-files:
  created:
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/HmacSignatureFilter.java
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/SecurityConfig.java
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WebhookController.java
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/HealthController.java
    - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/web/HmacSignatureFilterTest.java
    - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookControllerTest.java
    - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/HealthControllerTest.java
    - .planning/phases/01-fundacao-hmac-webhook/01-06-SUMMARY.md
  modified: []
  deleted: []

key-decisions:
  - "Pacote `web/` (consistente com Wave 5) escolhido para HmacSignatureFilter — RESEARCH usa `webhook/` mas Wave 5 SUMMARY confirmou que CachedBodyHttpServletRequest ficou em `web/`. Filter colocado no mesmo pacote para coesao (ambos sao infra de servlet). Controllers ficam em `controller/` (espelho api-consultas/api-email)"
  - "ApiKeyFilter ordem HIGHEST_PRECEDENCE+10 (vs ordem 1 do RESEARCH/PLAN) — ambos garantem que ApiKey roda DEPOIS do HMAC, mas '+10' deixa explicita a relacao. Em runtime e equivalente (Integer.MIN_VALUE + 10 vs 1 — qualquer Filter custom Phase 4+ pode usar 100, 200, etc. e ainda fica depois). Decisao informada pela spec do scope (executor recebeu 'HIGHEST_PRECEDENCE+10')"
  - "Filter trata IOException ao instanciar CachedBodyHttpServletRequest com 400 (Bad Request), nao 401. Nuance: 401 e 'sem credencial valida'; 400 e 'request mal formatado'. Spec inicial deixou implicito ('Falha ao ler body do webhook... 400'); implementei exatamente como spec, com mensagem 'Body do webhook ilegivel'. Defesa em profundidade — Phase 2 podera diferenciar timeouts vs corrupcao real"
  - "WebhookControllerTest usa @ActiveProfiles('test') + @TestPropertySource(verifyToken=tk-correto) em vez de @TestConfiguration interna. Razao: WhatsAppApplication ativa @EnableConfigurationProperties(WhatsAppProperties.class) globalmente, e qualquer @WebMvcTest carrega isso por default. Sobrescrever via @Bean cria conflito de bean; usar profile resolve sem complicacao"
  - "HmacSignatureFilterTest usa MockHttpServletRequest + MockFilterChain (spring-test) — testes unitarios puros sem Spring context, ~13ms total para 6 cenarios. Mesma abordagem de PLAN-05 para CachedBodyHttpServletRequestTest, consistencia"
  - "POST stub literalmente retorna ResponseEntity.ok().build() — corpo vazio. Test post_webhook_retorna_200_vazio enforce com .andExpect(content().string('')). Phase 2 vai mudar para retornar antes do dispatch async"

patterns-established:
  - "Filter unit test via Mock{Servlet}Request + MockFilterChain — quando Filter tem dependencias simples (validator service + properties), test direto sem WebMvcTest e mais rapido. WebMvcTest reservado para validar binding @RequestParam/@PathVariable/Jackson serialization no controller"
  - "@ActiveProfiles('test') em todo @WebMvcTest do api-whatsapp — padrao reusavel quando @EnableConfigurationProperties e ativo no application class. Documentar para Wave 7 + Phase 2 controllers"

requirements-completed:
  - WEB-01  # GET /webhook/whatsapp ecoa hub.challenge plain text + 403 em token errado
  - WEB-02  # POST valida HMAC via filter (HmacValidator de PLAN-05) e retorna 401 se invalido
  - WEB-03  # Filter usa CachedBodyHttpServletRequest no doFilterInternal antes de validar
  - WEB-04  # POST stub retorna 200 imediato apos filter validar (D-04)

# Metrics
duration: ~7min
completed: 2026-05-05
---

# Phase 01 Plan 06: HmacSignatureFilter + SecurityConfig + WebhookController + HealthController Summary

**Modulo `api-whatsapp` ganha o wire-up completo de seguranca da Phase 1: `HmacSignatureFilter` (OncePerRequestFilter, HIGHEST_PRECEDENCE em `/webhook/*`) embrulha `HttpServletRequest` em `CachedBodyHttpServletRequest` (PLAN-05) e delega a validacao HMAC ao `HmacValidator` (PLAN-05) com appSecret de `WhatsAppProperties`; em invalido retorna 401 + ErrorResponse JSON sem chamar `chain.doFilter`; em valido encaminha o wrapper downstream. `SecurityConfig` registra 2 `FilterRegistrationBean` — HMAC HIGHEST_PRECEDENCE em `/webhook/*` e `ApiKeyFilter` (lib-shared, construtor 2-arg de PLAN-01) HIGHEST_PRECEDENCE+10 em `/*` com `Set.of("/webhook")` como path publico extra (D-02). `WebhookController` GET ecoa `hub.challenge` em `text/plain` (PITFALLS C-10) com `verifyToken` comparado via `MessageDigest.isEqual` em UTF-8 bytes (consistencia com HMAC, custo zero); POST e stub minimo D-04 que retorna `ResponseEntity.ok().build()` sem parsing e sem log de body. `HealthController` atende `/health` com JSON `{status:UP, modulo:api-whatsapp}` (path publico default no `ApiKeyFilter`), sem `Spring Boot Actuator`. 11 testes novos verdes em ~5.5s — 6 unit do filter via `MockHttpServletRequest`+`MockFilterChain` (GET passa, POST com HMAC valido encaminha wrapper, sem header → 401 sem chamar chain, signature errada → 401, body modificado → 401, body UTF-8 portugues → 200), 4 do controller via `@WebMvcTest`+`@ActiveProfiles("test")` (handshake sucesso 200 plain text, verifyToken errado 403, mode != subscribe 403, POST 200 vazio) e 1 do health (200 com JSON). Reator inteiro `mvnw verify` BUILD SUCCESS — 117 tests verdes em ~22s, zero regressao em lib-shared/lib-consultas-client/api-email/api-storage/api-consultas. **Assumption A4 do RESEARCH resolvida positivamente: `@RequestParam("hub.mode")` com ponto literal funciona em Spring 3.5.9 sem precisar de fallback `HttpServletRequest.getParameter`** — confirmado empiricamente pelos 3 tests do GET handshake. Phase 1 fecha 100% dos requirements WEB-01..04; PLAN-07 adiciona integration test end-to-end via `@SpringBootTest` para confirmar que os 2 filters interagem corretamente no fluxo real.**

## Performance

- **Duration:** ~7 min
- **Started:** 2026-05-05T07:27:01Z
- **Completed:** 2026-05-05T07:33:58Z
- **Tasks:** 5 (Task 1 HmacSignatureFilter + Task 2 SecurityConfig + Task 3 WebhookController + Task 4 HealthController + Tasks 6 testes + Task 7 build verify) — Task 6 smoke test manual pulado (cobertura via WebMvcTest + filter unit tests e suficiente)
- **Files created:** 8 (4 producao + 3 testes + 1 SUMMARY)
- **Files modified:** 0
- **Files deleted:** 0
- **Tests:** 11 novos (6 HmacSignatureFilter + 4 WebhookController + 1 HealthController) — verdes. Suite api-whatsapp total: 43 tests (32 de PLAN-05 + 11 novos = 43, batendo previsao de 43 do scope). Suite reator total: 117 tests (lib-shared 20 + lib-consultas-client 3 + api-email 34 + api-storage 13 + api-consultas 4 + api-whatsapp 43). Zero regressao vs PLAN-05 baseline.
- **Build time:** ~22.8s (mvnw verify reator inteiro)

## Accomplishments

- **`HmacSignatureFilter.java` (102 linhas)** em `br.com.erpkit.whatsapp.web` — `extends OncePerRequestFilter`. Construtor recebe `HmacValidator` + `WhatsAppProperties`. `doFilterInternal`:
  1. GET → `chain.doFilter(request, response)` direto (sem wrap)
  2. POST → `try { new CachedBodyHttpServletRequest(request) }` — IOException → 400 + ErrorResponse JSON
  3. Le header `X-Hub-Signature-256` do request original (header preservado pelo wrapper)
  4. `validator.isValid(body, signature, properties.getAppSecret())` — false → 401 + ErrorResponse JSON, `return` (sem `chain.doFilter`)
  5. Valido → `chain.doFilter(cached, response)` (passa wrapper downstream para POST handler poder ler body novamente em Phase 2)
  
  Logger SLF4J nivel WARN apenas com URI + metodo HTTP. NUNCA inclui body, signature, secret. ObjectMapper static com JavaTimeModule (mirror ApiKeyFilter).

- **`SecurityConfig.java` (66 linhas)** em `br.com.erpkit.whatsapp.config` — 2 `@Bean FilterRegistrationBean`:
  1. `hmacSignatureFilter(HmacValidator, WhatsAppProperties)` → DI por argument do `@Bean` method, `addUrlPatterns("/webhook/*")`, `setOrder(Ordered.HIGHEST_PRECEDENCE)`
  2. `apiKeyFilter(@Value("${modulo.api-key:}") String apiKey)` → `new ApiKeyFilter(apiKey, Set.of("/webhook"))` (construtor 2-arg PLAN-01), `addUrlPatterns("/*")`, `setOrder(Ordered.HIGHEST_PRECEDENCE + 10)` — depois do HMAC filter

- **`WebhookController.java` (76 linhas)** em `br.com.erpkit.whatsapp.controller`:
  - GET `/webhook/whatsapp` `produces = MediaType.TEXT_PLAIN_VALUE` (PITFALLS C-10 — sem JSON wrap)
  - 3 `@RequestParam` com nome literal: `"hub.mode"`, `"hub.verify_token"`, `"hub.challenge"` (A4 RESEARCH confirmada)
  - `verifyToken` comparado via `MessageDigest.isEqual(properties.getVerifyToken().getBytes(UTF_8), verifyToken.getBytes(UTF_8))` — constant-time
  - Sucesso: `ResponseEntity.ok(challenge)` (string, content-type ja TEXT_PLAIN). Falha: `ResponseEntity.status(HttpStatus.FORBIDDEN).build()`
  - Logger INFO em sucesso (`Webhook verificado pelo Meta`), WARN em falha (`mode={}` apenas — NUNCA verifyToken)
  - POST `/webhook/whatsapp`: `log.debug("Webhook POST recebido com HMAC valido (stub Phase 1)")` + `return ResponseEntity.ok().build()`. Sem `@RequestBody`, sem parsing.

- **`HealthController.java` (25 linhas)** em `br.com.erpkit.whatsapp.controller` — `@GetMapping("/health")` retorna `Map.of("status","UP","modulo","api-whatsapp")` em ResponseEntity. Sem dependencias, sem Actuator. JavaDoc explica que Phase 4/6 podera adicionar validacao de WABA subscription (PITFALLS C-12).

- **`HmacSignatureFilterTest.java` (162 linhas)** — 6 testes unit puros sem Spring context. Helper `computeHeader(byte[], String)` produz `"sha256=<hex>"` valido via `Mac.getInstance("HmacSHA256")`. Cenarios:
  1. `get_request_passa_sem_validacao` — GET → chain.doFilter chamada com request original (sem wrap)
  2. `post_com_hmac_valido_passa` — POST com header valido → chain.doFilter recebe `CachedBodyHttpServletRequest`
  3. `post_sem_header_retorna_401_e_nao_chama_chain` — POST sem `X-Hub-Signature-256` → 401, content-type application/json, body com "Nao autorizado", `chain.getRequest() == null`
  4. `post_com_hmac_invalido_retorna_401` — POST com signature `"sha256=" + "0".repeat(64)` → 401
  5. `post_com_body_modificado_retorna_401` — assina bodyA, envia bodyB com signature de A → 401 (tampering)
  6. `post_com_body_portugues_utf8_passa` — `"Olá, gostaria de um orçamento"` em UTF-8 com signature valida → wrapper downstream

- **`WebhookControllerTest.java` (78 linhas)** — `@WebMvcTest(WebhookController.class)` + `@ActiveProfiles("test")` + `@TestPropertySource(properties = "app.modulos.whatsapp.verifyToken=tk-correto")`. 4 testes:
  1. `get_hub_challenge_com_verify_token_correto_retorna_plain_text` — params `hub.mode=subscribe`, `hub.verify_token=tk-correto`, `hub.challenge=abc123` → 200, content-type compativel `text/plain`, body LITERAL `"abc123"` (sem aspas, sem JSON wrap — gate PITFALLS C-10)
  2. `get_hub_challenge_com_verify_token_errado_retorna_403`
  3. `get_hub_challenge_com_mode_diferente_retorna_403` — `mode=unsubscribe` → 403
  4. `post_webhook_retorna_200_vazio` — POST direto (filter nao wired no WebMvcTest) → 200, body vazio (D-04 enforce)

- **`HealthControllerTest.java` (33 linhas)** — `@WebMvcTest(HealthController.class)` + `@ActiveProfiles("test")`. 1 teste:
  1. `health_retorna_200_com_status_up` — GET `/health` → 200, content-type application/json, `$.status=UP`, `$.modulo=api-whatsapp`

- **Reator inteiro BUILD SUCCESS** — 117 tests verdes em ~22.8s. Zero regressao vs PLAN-05 baseline (106 tests). +11 = 6 filter + 4 controller + 1 health.

## Task Commits

1. **Tasks 1-5 (atomico):** `feat(api-whatsapp): adicionar HmacSignatureFilter + SecurityConfig + WebhookController + HealthController` — commit `51ba38a`
   - 7 arquivos: 4 producao + 3 testes
   - Pos-commit deletion check: 0 deletions (verificado via `git diff --diff-filter=D --name-only HEAD~1 HEAD`)

2. **SUMMARY metadata:** commit pendente (proximo passo apos este file ser escrito).

## Files Created/Modified

- **`api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/HmacSignatureFilter.java`** (NEW, 102 linhas) — `OncePerRequestFilter` que valida HMAC-SHA256 em POST /webhook/*. JavaDoc da classe + metodos documentando referencias a PITFALLS C-02.
- **`api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/SecurityConfig.java`** (NEW, 66 linhas) — 2 FilterRegistrationBean. JavaDoc explica matriz de comportamento (POST/GET webhook + endpoints internos + /health).
- **`api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WebhookController.java`** (NEW, 76 linhas) — GET handshake + POST stub. JavaDoc enfatiza PITFALLS C-10 (TEXT_PLAIN_VALUE) e D-04 (sem parsing).
- **`api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/HealthController.java`** (NEW, 25 linhas) — GET /health stub. JavaDoc menciona Phase 4/6 podera adicionar WABA subscription check.
- **`api-whatsapp/src/test/java/br/com/erpkit/whatsapp/web/HmacSignatureFilterTest.java`** (NEW, 162 linhas) — 6 unit tests com MockHttpServletRequest+MockFilterChain.
- **`api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookControllerTest.java`** (NEW, 78 linhas) — 4 tests via @WebMvcTest+@ActiveProfiles.
- **`api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/HealthControllerTest.java`** (NEW, 33 linhas) — 1 test via @WebMvcTest+@ActiveProfiles.
- **`.planning/phases/01-fundacao-hmac-webhook/01-06-SUMMARY.md`** — este arquivo.

## Decisions Made

- **Pacote `web/` consistente com Wave 5** — Wave 5 colocou `CachedBodyHttpServletRequest` em `web/`; mantive o filter no mesmo pacote para coesao (servlet-level infra). Controllers ficam em `controller/` (espelho api-consultas/api-email).
- **ApiKeyFilter ordem `Ordered.HIGHEST_PRECEDENCE + 10`** (vs `1` no RESEARCH/PLAN) — semanticamente equivalente em runtime (ambos garantem que ApiKey roda DEPOIS do HMAC), mas `+10` deixa explicita a relacao e abre espaco para Filter custom Phase 4+ usarem `100`/`200`/etc. sem colisao. Decisao alinhada com `<scope>` do executor.
- **Filter trata IOException no construtor de CachedBodyHttpServletRequest com 400** (Bad Request), nao 401. Razao: 401 e "sem credencial valida"; 400 e "request mal formatado". Defesa em profundidade alem do RESEARCH §3.2 (que so cobria 401).
- **WebhookControllerTest com `@ActiveProfiles("test")` + `@TestPropertySource(verifyToken=tk-correto)`** em vez de `@TestConfiguration` interna que estava na primeira versao do test. Razao: `WhatsAppApplication` ativa `@EnableConfigurationProperties(WhatsAppProperties.class)` globalmente, e WebMvcTest carrega isso por default. Sobrescrever via `@Bean` interno cria conflito de bean (Bean Validation reclama de placeholders vazios em `application.yml` antes mesmo de chegar no @TestConfiguration). Profile resolve sem complicacao.
- **HmacSignatureFilterTest usa MockHttpServletRequest+MockFilterChain (spring-test)** sem Spring context — testes ~13ms total. WebMvcTest fica reservado para tests de controller que precisam de binding @RequestParam/Jackson serialization.

## Deviations from Plan

**1. [Rule 3 - Auto-fix blocking issue] WebhookControllerTest e HealthControllerTest precisaram de @ActiveProfiles("test")**
- **Found during:** Build apos primeiro `mvnw verify` falhou com 5 errors em WebhookControllerTest (4) + HealthControllerTest (1)
- **Issue:** `@WebMvcTest` por default carrega `WhatsAppApplication` como configuration source, que tem `@EnableConfigurationProperties(WhatsAppProperties.class)`. Spring carrega `application.yml` (nao `application-test.yml`) com placeholders vazios `${WHATSAPP_*:}` e Bean Validation rejeita 5 campos `@NotBlank`. Causa raiz no surefire report: `BindValidationException: Field error in object 'app.modulos.whatsapp' on field 'verifyToken': rejected value []`.
- **Fix:** Adicionar `@ActiveProfiles("test")` para carregar `application-test.yml` (com dummy values) em ambos os WebMvcTest. WebhookControllerTest tambem ganhou `@TestPropertySource(properties = "app.modulos.whatsapp.verifyToken=tk-correto")` para sobrescrever apenas o verifyToken (test usa `tk-correto` como valor esperado nos asserts).
- **Files modified:** WebhookControllerTest.java, HealthControllerTest.java
- **Commit:** `51ba38a`

**2. [Rule 2 - Auto-add critical functionality] HmacSignatureFilter trata IOException no construtor de CachedBodyHttpServletRequest com 400**
- **Found during:** Task 1 (escrita do filter)
- **Issue:** Plan/RESEARCH especifica `new CachedBodyHttpServletRequest(request)` mas nao trata o `throws IOException` — wave 7 com payload corrupto/timeout do client poderia gerar IOException nao capturada e response 500 (Spring default). Atacante poderia sondar endpoint com bodies parcialmente enviados para diferenciar webhook ativo de inativo.
- **Fix:** `try/catch (IOException ex)` envolvendo o construtor; em catch retorna 400 + ErrorResponse "Body do webhook ilegivel". 400 e o status correto (request mal formatado), distinguindo de 401 (HMAC invalido). Logger WARN com URI mas SEM body/exception details (poderia vazar conteudo parcial).
- **Files modified:** HmacSignatureFilter.java (`writeBadRequest` helper privado)
- **Commit:** `51ba38a`

Nenhum desvio Rule 1 (sem bugs reais — implementacao por especificacao foi limpa) nem Rule 4 (sem mudancas arquiteturais).

## Issues Encountered

- **WebMvcTest carrega `application.yml` por default, nao `application-test.yml`** — round-trip 1: build falhou com 5 errors, identifiquei via surefire report (`BindValidationException` na causa raiz), adicionei `@ActiveProfiles("test")` em ambos os WebMvcTest. Custo: ~2min. Documentado como `pattern-established` para futuras waves.
- **Smoke test manual (Task 6) pulado** — opcional na spec, sem ambiente de DB local rodando neste momento. Cobertura via WebMvcTest + filter unit tests cobre os mesmos cenarios em mock-mode. Wave 7 (PLAN-07) com integration test `@SpringBootTest` exercitara o stack completo.

## Verification Performed

| Check | Comando | Resultado |
|-------|---------|-----------|
| Ordered.HIGHEST_PRECEDENCE em SecurityConfig | `grep -c "Ordered.HIGHEST_PRECEDENCE" SecurityConfig.java` | 4 (1x bean HMAC + 1x bean ApiKey + 2x JavaDoc — gate >=1 atende) |
| addUrlPatterns(/webhook/*) em SecurityConfig | `grep -c 'addUrlPatterns("/webhook/\*")' SecurityConfig.java` | 1 |
| Set.of("/webhook") em SecurityConfig | `grep -c 'Set.of("/webhook")' SecurityConfig.java` | 3 (1x uso real + 2x JavaDoc — gate >=1 atende) |
| TEXT_PLAIN_VALUE em WebhookController | `grep -c "TEXT_PLAIN_VALUE" WebhookController.java` | 1 (PITFALLS C-10) |
| MessageDigest.isEqual em WebhookController | `grep -c "MessageDigest.isEqual" WebhookController.java` | 3 (1x uso real + 2x JavaDoc {@link} — gate >=1 atende) |
| @RequestBody em WebhookController (NAO deve aparecer) | `grep -c "@RequestBody" WebhookController.java` | 0 (D-04 enforce) |
| new CachedBodyHttpServletRequest em HmacSignatureFilter | `grep -c "new CachedBodyHttpServletRequest" HmacSignatureFilter.java` | 1 |
| @GetMapping("/health") em HealthController | `grep -c '@GetMapping("/health")' HealthController.java` | 1 |
| HmacSignatureFilterTest run isolado | `./mvnw test -Dtest=HmacSignatureFilterTest` | Tests run: 6, Failures: 0 |
| WebhookControllerTest run isolado | `./mvnw test -Dtest=WebhookControllerTest` | Tests run: 4, Failures: 0 |
| HealthControllerTest run isolado | `./mvnw test -Dtest=HealthControllerTest` | Tests run: 1, Failures: 0 |
| api-whatsapp suite completa | `./mvnw verify -pl api-whatsapp -am` | BUILD SUCCESS — 43 tests verdes, ~5.8s |
| Reator inteiro | `./mvnw verify` | BUILD SUCCESS — 117 tests verdes em ~22.8s. Zero regressao vs PLAN-05 baseline (106 tests). +11 = 6 filter + 4 controller + 1 health |
| Pos-commit deletion check | `git diff --diff-filter=D --name-only HEAD~1 HEAD` | 0 deletions |
| Commit existe | `git log --oneline 51ba38a` | OK — 7 arquivos no commit |

## Threat Model Compliance

Per `<threat_model>` do PLAN-06:

| Threat ID | Mitigation enforced | Test |
|-----------|---------------------|------|
| T-06-01 (Spoofing: POST sem assinatura) | HmacSignatureFilter HIGHEST_PRECEDENCE valida e retorna 401 antes do MVC | `post_sem_header_retorna_401_e_nao_chama_chain` (HmacSignatureFilterTest) |
| T-06-02 (Spoofing: signature de body diferente) | MessageDigest.isEqual em HmacValidator (PLAN-05) | `post_com_body_modificado_retorna_401` (HmacSignatureFilterTest) |
| T-06-03 (InfoDisclosure: verifyToken via String.equals) | WebhookController GET usa MessageDigest.isEqual em UTF-8 bytes | `get_hub_challenge_com_verify_token_correto_retorna_plain_text` + `..._errado_retorna_403` |
| T-06-04 (Tampering: hub.challenge em JSON) | `produces = MediaType.TEXT_PLAIN_VALUE` no @GetMapping | `get_hub_challenge_..._retorna_plain_text` valida `content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)` + `content().string("abc123")` literal |
| T-06-05 (DoS: body grande em CachedBodyHttpServletRequest) | accept per plan — Tomcat default ja limita; Phase 6 podera adicionar limite explicito | n/a |
| T-06-06 (Spoofing: ApiKeyFilter permitir /webhook) | accept — webhook E publico (validado por HMAC), Set.of("/webhook") deliberado | gate `Set.of("/webhook")` enforce |
| T-06-07 (InfoDisclosure: POST body em DEBUG) | `log.debug("Webhook POST recebido com HMAC valido (stub Phase 1)")` sem incluir body | accept per plan — verificado por inspecao do codigo |
| T-06-08 (Spoofing: filter ordem trocada) | `setOrder(Ordered.HIGHEST_PRECEDENCE)` HMAC vs `HIGHEST_PRECEDENCE+10` ApiKey | gate `Ordered.HIGHEST_PRECEDENCE` aparece em ambos beans com ordens distintas |

Todas as 5 ameacas com disposition `mitigate` (T-06-01 a 04, T-06-08) estao enforced + test-validated. T-06-05 / T-06-06 / T-06-07 (`accept`) verificadas por inspection.

## Risks Resolved

- **A4 (RESEARCH §13): `@RequestParam("hub.mode")` com ponto pode falhar em Spring 3.5.x** — RESOLVIDO POSITIVAMENTE. Spring Boot 3.5.9 suporta nativamente, sem precisar de fallback `HttpServletRequest.getParameter`. Confirmado empiricamente pelos 3 GET tests do WebhookController (todos sucesso esperado nos 3 cenarios: token correto 200, token errado 403, mode errado 403). Zero round-trips para resolver — funcionou na primeira tentativa.
- **Filter ordering pode ser ignorada por SecurityAutoConfiguration** — `spring-boot-starter-security` NAO esta no classpath de api-whatsapp (verificado em pom.xml — so spring-boot-starter-web/validation/data-jpa). ApiKeyFilter custom (lib-shared) respeita order via FilterRegistrationBean. Sem risco.
- **HealthController choca com Spring Boot Actuator** — Actuator NAO esta no classpath de api-whatsapp. Sem conflito. Phase 4/6 quando trouxer Actuator (se trouxer), renomear para `/health-custom` ou desabilitar `/actuator/health` para evitar colisao.

## Concerns para Wave 7 (PLAN-07: integration tests MockMvc end-to-end)

1. **Wave 7 deve usar `@SpringBootTest(webEnvironment = MOCK)` + `@AutoConfigureMockMvc`** — diferente do `@WebMvcTest` desta wave que NAO carrega `FilterRegistrationBean`. Precisamos de SpringBootTest para que os 2 filters sejam instanciados via Spring context com beans `HmacValidator` e `WhatsAppProperties` reais. Wave 7 pode reutilizar o helper `computeHeader(byte[], String)` do `HmacSignatureFilterTest` para gerar signatures vivas.

2. **Wave 7 pode validar empiricamente que ApiKeyFilter NAO bloqueia /webhook** — POST `/webhook/whatsapp` com HMAC valido + sem `X-API-Key` deve retornar 200. Sem o `Set.of("/webhook")` no construtor, ApiKeyFilter retornaria 401. Esse teste prova D-02 + Wave 6 wire-up corretos.

3. **Charset UTF-8 em MockMvc** — Wave 5 SUMMARY warning ja capturou que MockMvc default e ISO-8859-1. Wave 7 com payload portugues precisa setar `MockHttpServletRequestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8)` ou equivalente. Wave 6 testes do filter unit ja usam `StandardCharsets.UTF_8` explicitos no `body.getBytes(...)`, mas WebMvcTest do Webhook ainda nao testa POST com body real (so com `{}` minimal, e pre-filter).

4. **Resposta 401 do filter contem JSON com timestamp** — `ErrorResponse(401, "Nao autorizado", "Assinatura HMAC invalida ou ausente")` + auto-populated `timestamp = LocalDateTime.now()`. Wave 7 integration test pode usar `jsonPath("$.status").value(401)` em vez de comparar JSON inteiro (timestamp varia).

5. **Smoke test manual (Task 6 opcional desta wave) ainda nao executado** — Wave 7 pode incluir um boot real `mvnw spring-boot:run -pl api-whatsapp -Dspring-boot.run.profiles=test` antes dos integration tests, ou simplesmente confiar nos integration tests com `@SpringBootTest` que ja cobrem o boot.

6. **OpenAPI/Swagger UI** — `springdoc-openapi-starter-webmvc-ui` esta no pom mas Wave 6 nao testou se `/v3/api-docs` e `/swagger-ui.html` ficam acessiveis sem API key (estao em `DEFAULT_PUBLIC_PATHS` do ApiKeyFilter). Wave 7 pode incluir um smoke check (ou Wave 6+/Phase 6 com OpenAPI exhaustivo).

7. **SecurityConfig em test-context atualmente tem 2 filters criados** — Wave 7 com `@SpringBootTest` vera-os no application context. Se algum teste future precisar mock-bear `HmacValidator`, usar `@MockBean` para nao quebrar o registro do filter (filter ja instanciado, mock substitui o validator dentro dele).

## Self-Check: PASSED

- [x] `HmacSignatureFilter.java` existe em `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/`
- [x] `SecurityConfig.java` existe em `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/`
- [x] `WebhookController.java` existe em `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/`
- [x] `HealthController.java` existe em `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/`
- [x] `HmacSignatureFilterTest.java` existe em `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/web/` com 6 cenarios
- [x] `WebhookControllerTest.java` existe em `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/` com 4 cenarios
- [x] `HealthControllerTest.java` existe em `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/` com 1 cenario
- [x] Gate 1: `Ordered.HIGHEST_PRECEDENCE` aparece em SecurityConfig (4x — uso + JavaDoc)
- [x] Gate 2: `addUrlPatterns("/webhook/*")` aparece 1x em SecurityConfig
- [x] Gate 3: `Set.of("/webhook")` aparece em SecurityConfig (3x — uso + JavaDoc)
- [x] Gate 4: `TEXT_PLAIN_VALUE` aparece 1x em WebhookController (PITFALLS C-10)
- [x] Gate 5: `MessageDigest.isEqual` aparece em WebhookController (3x — uso + JavaDoc)
- [x] Gate 6: `@RequestBody` aparece 0x em WebhookController (D-04 enforce)
- [x] Gate 7: `new CachedBodyHttpServletRequest` aparece 1x em HmacSignatureFilter
- [x] Gate 8: `@GetMapping("/health")` aparece 1x em HealthController
- [x] HmacSignatureFilterTest passou: 6 tests, 0 failures
- [x] WebhookControllerTest passou: 4 tests, 0 failures
- [x] HealthControllerTest passou: 1 test, 0 failures
- [x] api-whatsapp suite: 43 tests verdes (vs 32 antes — +11 novos)
- [x] Reator inteiro: BUILD SUCCESS — 117 tests verdes em ~22.8s, zero regressao vs PLAN-05 baseline
- [x] Test `get_hub_challenge_com_verify_token_correto_retorna_plain_text` valida content-type `text/plain` + body literal `"abc123"` (PITFALLS C-10 enforce)
- [x] Test `post_sem_header_retorna_401_e_nao_chama_chain` enforce que `chain.getRequest() == null` (chain nao invocada)
- [x] Test `post_com_body_portugues_utf8_passa` valida UTF-8 do filter (PITFALLS C-04 enforce)
- [x] Commit `51ba38a` existe no historico (`git log --oneline -3` confirma)
- [x] Pos-commit deletion check: 0 deletions

---
*Phase: 01-fundacao-hmac-webhook*
*Completed: 2026-05-05*
