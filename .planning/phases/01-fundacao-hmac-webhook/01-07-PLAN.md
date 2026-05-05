---
phase: 01-fundacao-hmac-webhook
plan: 07
type: execute
wave: 7
depends_on:
  - "01-06"
files_modified:
  - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookControllerIntegrationTest.java  # NEW
autonomous: true
requirements:
  - WEB-01  # COMPLETO via integration test cobrindo GET handshake plain text + 403
  - WEB-02  # COMPLETO via integration test cobrindo POST HMAC valido/invalido
  - WEB-03  # COMPLETO via integration test cobrindo POST com payload portugues UTF-8
  - WEB-04  # parcial — assertion de 200 retorna em <1s e DEFERRED para Phase 6 (load test com WireMock); aqui apenas verificamos retorno 200
tags:
  - api-whatsapp
  - integration-test
  - mockmvc
  - phase-gate

must_haves:
  truths:
    - "WebhookControllerIntegrationTest usa @SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles(test) — contexto completo, filters reais, sem mocking parcial"
    - "Test GET handshake com verifyToken correto retorna 200 + Content-Type text/plain + body literal challenge (sem aspas, sem JSON)"
    - "Test GET handshake com verifyToken errado retorna 403"
    - "Test GET handshake com mode=unsubscribe retorna 403"
    - "Test POST com X-Hub-Signature-256 valido (computado contra dummy appSecret de application-test.yml) retorna 200"
    - "Test POST com X-Hub-Signature-256 de body diferente retorna 401"
    - "Test POST sem header X-Hub-Signature-256 retorna 401"
    - "Test POST com payload portugues UTF-8 (Olá, gostaria de um orçamento) + signature correta retorna 200 — fecha PITFALLS C-04 end-to-end"
    - "5 ROADMAP success criteria de Phase 1 estao ALL observable via test output"
    - "mvnw verify -pl api-whatsapp BUILD SUCCESS — total Tests run >= 28 (21 anteriores + 7 novos)"
  artifacts:
    - path: "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookControllerIntegrationTest.java"
      provides: "End-to-end integration test cobrindo todo o stack: HmacSignatureFilter + ApiKeyFilter + CachedBodyHttpServletRequest + HmacValidator + WebhookController"
      contains: "post_com_payload_portugues_e_hmac_valido_retorna_200"
  key_links:
    - from: "@SpringBootTest"
      to: "todo o ApplicationContext (filters reais)"
      via: "NAO @WebMvcTest porque filters precisam rodar"
      pattern: "@SpringBootTest"
    - from: "MockMvc + perform"
      to: "Filter chain real → Controller → Response"
      via: "mockMvc.perform(post(\"/webhook/whatsapp\").header(...).content(...))"
      pattern: "mockMvc.perform"
    - from: "test setup"
      to: "appSecret = test-app-secret (de application-test.yml)"
      via: "Mac.getInstance(\"HmacSHA256\") para computar header valido em runtime"
      pattern: "Mac\\.getInstance\\(\"HmacSHA256\"\\)"
---

<objective>
Criar `WebhookControllerIntegrationTest` (`@SpringBootTest` + `@AutoConfigureMockMvc`, NAO `@WebMvcTest` — filters precisam rodar com contexto completo) cobrindo os 5 success criteria do ROADMAP Phase 1 via 7 cenarios MockMvc end-to-end. Este e o **phase gate** — quando este test passa verde, a Phase 1 esta completa.

Purpose: ROADMAP Phase 1 success criteria 1-3 + 5 sao verificados aqui (criterio 4 — boot fail-fast — ja foi por PLAN-03; criterio 5 — Flyway + mvnw verify — implicito pelo fato do test bootar com schema valido). PITFALLS C-04 (UTF-8) e C-10 (plain text) tem cobertura end-to-end via os tests `post_com_payload_portugues_*` e assertion de Content-Type literal `text/plain`.

Output:
- `WebhookControllerIntegrationTest.java` com 7 cenarios verdes
- `mvnw verify -pl api-whatsapp` BUILD SUCCESS — Tests run >= 28
- 5 ROADMAP success criteria observable via test output
- Phase 1 100% fechada
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/STATE.md
@.planning/ROADMAP.md
@.planning/phases/01-fundacao-hmac-webhook/01-CONTEXT.md
@.planning/phases/01-fundacao-hmac-webhook/01-RESEARCH.md
@.planning/phases/01-fundacao-hmac-webhook/01-06-SUMMARY.md
@api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WebhookController.java
@api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/HmacSignatureFilter.java
@api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/HmacValidator.java
@api-whatsapp/src/test/resources/application-test.yml

<interfaces>
<!-- application-test.yml fornece (PLAN-03/04): -->
- `app.modulos.whatsapp.appSecret = test-app-secret`
- `app.modulos.whatsapp.verifyToken = test-verify-token`
- (outros 3 dummy values)

<!-- 7 cenarios per RESEARCH §12.2 (linhas 1080-1088) -->

| Test | Comportamento esperado |
|------|------------------------|
| `get_handshake_com_token_correto_retorna_challenge_plain_text` | GET ?hub.mode=subscribe&hub.verify_token=test-verify-token&hub.challenge=abc123 → 200 + Content-Type text/plain + body literal "abc123" |
| `get_handshake_com_token_errado_retorna_403` | GET ?hub.verify_token=wrong → 403 |
| `get_handshake_com_mode_diferente_de_subscribe_retorna_403` | GET ?hub.mode=unsubscribe&... → 403 |
| `post_com_hmac_valido_retorna_200` | POST com header valido + body conhecido → 200 + body resposta vazio |
| `post_com_hmac_invalido_retorna_401` | POST com signature de body diferente → 401 + ErrorResponse JSON |
| `post_sem_header_signature_retorna_401` | POST sem X-Hub-Signature-256 → 401 |
| `post_com_payload_portugues_e_hmac_valido_retorna_200` | POST body com "Olá, orçamento" UTF-8 + signature correta → 200 (PITFALLS C-04 end-to-end) |
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Criar WebhookControllerIntegrationTest com 7 cenarios MockMvc</name>
  <files>api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookControllerIntegrationTest.java</files>
  <behavior>
    7 testes integrando filters + controller via MockMvc. Cobertura end-to-end dos 5 success criteria de ROADMAP Phase 1.

    **Critico:** usar `@SpringBootTest` (NAO `@WebMvcTest`) — filters customizados (HmacSignatureFilter, ApiKeyFilter) so rodam com contexto completo. `@WebMvcTest` carrega so o controller e filters auto-detectados, NAO os FilterRegistrationBean de SecurityConfig.

    **Setup:**
    - Helper estatico ou method `computeSignature(byte[] body, String secret)` que computa `"sha256=" + hex(HMAC-SHA256(body, secret))`
    - appSecret e verifyToken vem de `application-test.yml` ("test-app-secret", "test-verify-token")
  </behavior>
  <action>
    Criar `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookControllerIntegrationTest.java`:

    ```java
    package br.com.erpkit.whatsapp.controller;

    import br.com.erpkit.whatsapp.WhatsAppApplication;
    import org.junit.jupiter.api.DisplayName;
    import org.junit.jupiter.api.Test;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.boot.test.context.SpringBootTest;
    import org.springframework.http.MediaType;
    import org.springframework.test.context.ActiveProfiles;
    import org.springframework.test.web.servlet.MockMvc;
    import org.springframework.test.web.servlet.setup.MockMvcBuilders;
    import org.springframework.web.context.WebApplicationContext;
    import org.springframework.beans.factory.annotation.Value;

    import javax.crypto.Mac;
    import javax.crypto.spec.SecretKeySpec;
    import java.nio.charset.StandardCharsets;
    import java.util.HexFormat;

    import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
    import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
    import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
    import static org.hamcrest.Matchers.containsString;

    /**
     * Phase 1 gate. Cobre os 5 ROADMAP success criteria via MockMvc end-to-end.
     *
     * @SpringBootTest (NAO @WebMvcTest) — filters customizados so rodam com contexto completo.
     */
    @SpringBootTest(classes = WhatsAppApplication.class)
    @ActiveProfiles("test")
    class WebhookControllerIntegrationTest {

        @Autowired
        private WebApplicationContext context;

        @Value("${app.modulos.whatsapp.appSecret}")
        private String appSecret;

        @Value("${app.modulos.whatsapp.verifyToken}")
        private String verifyToken;

        private MockMvc mockMvc;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            // springSecurity() inutil porque nao usamos Spring Security; webAppContextSetup
            // garante que TODOS os filters registrados (HmacSignatureFilter via FilterRegistrationBean,
            // ApiKeyFilter via FilterRegistrationBean) entram na chain. addFilters() e necessario
            // pra MockMvc nao pular filters por default em alguns setups.
            mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        }

        /** Helper: computa header X-Hub-Signature-256 para um body+secret. */
        private String computeSignature(byte[] body, String secret) throws Exception {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmac = mac.doFinal(body);
            return "sha256=" + HexFormat.of().formatHex(hmac);
        }

        // -------- ROADMAP Phase 1 success criterion 1 (GET handshake) --------

        @Test
        @DisplayName("GET handshake com token correto retorna challenge plain text (PITFALLS C-10)")
        void get_handshake_com_token_correto_retorna_challenge_plain_text() throws Exception {
            mockMvc.perform(get("/webhook/whatsapp")
                    .param("hub.mode", "subscribe")
                    .param("hub.verify_token", verifyToken)
                    .param("hub.challenge", "abc123"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("abc123")); // literal, sem aspas, sem JSON
        }

        @Test
        void get_handshake_com_token_errado_retorna_403() throws Exception {
            mockMvc.perform(get("/webhook/whatsapp")
                    .param("hub.mode", "subscribe")
                    .param("hub.verify_token", "wrong-token")
                    .param("hub.challenge", "abc123"))
                .andExpect(status().isForbidden());
        }

        @Test
        void get_handshake_com_mode_diferente_de_subscribe_retorna_403() throws Exception {
            mockMvc.perform(get("/webhook/whatsapp")
                    .param("hub.mode", "unsubscribe")
                    .param("hub.verify_token", verifyToken)
                    .param("hub.challenge", "abc123"))
                .andExpect(status().isForbidden());
        }

        // -------- ROADMAP Phase 1 success criterion 2 (POST HMAC) --------

        @Test
        @DisplayName("POST com HMAC valido retorna 200")
        void post_com_hmac_valido_retorna_200() throws Exception {
            byte[] body = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);
            String signature = computeSignature(body, appSecret);
            mockMvc.perform(post("/webhook/whatsapp")
                    .header("X-Hub-Signature-256", signature)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST com HMAC de body diferente retorna 401 (1-byte modificado)")
        void post_com_hmac_invalido_retorna_401() throws Exception {
            byte[] originalBody = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);
            String signatureOriginal = computeSignature(originalBody, appSecret);
            byte[] modifiedBody = originalBody.clone();
            modifiedBody[0] ^= 0x01; // 1-byte flip
            mockMvc.perform(post("/webhook/whatsapp")
                    .header("X-Hub-Signature-256", signatureOriginal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(modifiedBody))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("Assinatura HMAC invalida")));
        }

        @Test
        void post_sem_header_signature_retorna_401() throws Exception {
            byte[] body = "{\"object\":\"x\"}".getBytes(StandardCharsets.UTF_8);
            mockMvc.perform(post("/webhook/whatsapp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnauthorized());
        }

        // -------- ROADMAP Phase 1 success criterion 3 (UTF-8 portugues) --------

        @Test
        @DisplayName("POST com payload portugues UTF-8 (Olá, gostaria de um orçamento) + HMAC valido retorna 200 (PITFALLS C-04)")
        void post_com_payload_portugues_e_hmac_valido_retorna_200() throws Exception {
            byte[] body = "{\"text\":\"Olá, gostaria de um orçamento\"}".getBytes(StandardCharsets.UTF_8);
            String signature = computeSignature(body, appSecret);
            mockMvc.perform(post("/webhook/whatsapp")
                    .header("X-Hub-Signature-256", signature)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk());
        }
    }
    ```

    **Detalhes criticos:**

    1. **`@SpringBootTest(classes = WhatsAppApplication.class)`** — carrega contexto completo, todos os filters reais.
    2. **`MockMvcBuilders.webAppContextSetup(context).build()`** — diferente de `standaloneSetup`, esse caminho **inclui** os FilterRegistrationBean. Verificacao se `addFilters` adicional e necessario: NAO se webAppContextSetup esta ativo. Se POST falhar (200 quando deveria ser 401, sinal de que filter nao rodou), adicionar `.addFilters(...)` ou usar `MockMvcBuilders.webAppContextSetup(context).addFilters(filterRegistrationBean.getFilter()).build()` — porem o caminho mais limpo e webAppContextSetup auto-discovery.
    3. **HexFormat.of().formatHex** — Java 17+ (projeto e Java 21).
    4. **Content-Type assertion `TEXT_PLAIN`** — `contentTypeCompatibleWith(MediaType.TEXT_PLAIN)` aceita `text/plain;charset=UTF-8` e `text/plain` igualmente. Use `contentType` exato so se quiser amarrar charset.
    5. **Body literal "abc123"** — `content().string("abc123")` faz assertion exata. Se Spring serializar como `"abc123"` (com aspas) por causa de Jackson, este assert falha. Esse e o gate de PITFALLS C-10.
    6. **POST sem `@RequestBody` no controller** — controller atual recebe POST sem `@RequestBody String corpo`. MockMvc fornece body via `.content(body)` e o `CachedBodyHttpServletRequest` no filter le os bytes brutos para HMAC, sem o controller precisar parsear. Phase 2 adiciona parsing.
    7. **WhatsAppPropertiesValidationTest e FlywayMigrationTest sao SpringBootTest separados** — cada um carrega contexto independente. Tempo total Phase 1: ~30-60s.
  </action>
  <verify>
    <automated>./mvnw -pl api-whatsapp test -Dtest=WebhookControllerIntegrationTest -q</automated>
  </verify>
  <done>
    - Surefire: "Tests run: 7, Failures: 0"
    - Output do log mostra que ambos os filters foram chamados (HmacSignatureFilter para POST, ApiKeyFilter para todos)
    - Test `get_handshake_com_token_correto_retorna_challenge_plain_text` passa — content type text/plain confirmado
    - Test `post_com_payload_portugues_e_hmac_valido_retorna_200` passa — UTF-8 end-to-end OK
  </done>
</task>

<task type="auto">
  <name>Task 2: Verificar build do reator inteiro (phase gate)</name>
  <files>(nenhum modificado)</files>
  <action>
    Rodar `./mvnw verify -pl api-whatsapp -q`. Esperado: BUILD SUCCESS com Tests run >= 28:
    - 11 do HmacValidatorTest (PLAN-05 unit)
    - 7 do WhatsAppPropertiesValidationTest (PLAN-03)
    - 3 do FlywayMigrationTest (PLAN-04)
    - 7 do WebhookControllerIntegrationTest (este plan)

    Em paralelo, rodar `./mvnw verify -q` (raiz) para confirmar que os 6 modulos estao todos verdes — phase gate completo.

    **Se WebhookControllerIntegrationTest falhar:**

    Sintoma → causa provavel:

    - **GET retorna 200 com body `"abc123"` (com aspas)** → @RestController serializou via Jackson; verifying que `produces = MediaType.TEXT_PLAIN_VALUE` esta no GET mapping (PLAN-06 Task 3).
    - **POST retorna 200 sem header HMAC (deveria retornar 401)** → HmacSignatureFilter nao foi registrado ou setOrder esta errada; verificar SecurityConfig de PLAN-06.
    - **POST com payload portugues retorna 401 (deveria 200)** → CachedBodyHttpServletRequest nao esta no chain ou esta convertendo para String; verificar PLAN-05 Task 2.
    - **GET handshake retorna 400 (Bad Request)** → @RequestParam("hub.mode") com ponto nao funciona; A4 do RESEARCH §13. Fallback: trocar por HttpServletRequest.getParameter("hub.mode").
    - **Filter chain order errada (POST passa pelo ApiKeyFilter primeiro)** → ApiKeyFilter retornaria 200 (porque /webhook esta em publicPaths) ao inves de 401, mas como HmacSignatureFilter so processa POST a sequencia normal seria HMAC ordem 0 → ApiKey ordem 1 → controller. Se ordem inverteu, ainda funciona porque ApiKey libera /webhook e HMAC processa em seguida. Mas ordem reversa pode quebrar se algum reset state. Verificar setOrder em SecurityConfig.

    **Se ainda falhar apos investigacao:**
    - Considerar rodar com `--debug` no mvn para ver chain de filters ativada
    - Verificar `application-test.yml` ainda tem `appSecret: test-app-secret` (PLAN-03 Task 4)
  </action>
  <verify>
    <automated>./mvnw verify -pl api-whatsapp -q</automated>
  </verify>
  <done>
    - BUILD SUCCESS no api-whatsapp
    - Tests run total >= 28
    - 4 success criteria do ROADMAP Phase 1 cobertos por test (criterio 5 — `mvnw verify` verde — implicito pelo proprio comando)
  </done>
</task>

<task type="auto">
  <name>Task 3: Validar reator completo (phase gate final)</name>
  <files>(nenhum modificado)</files>
  <action>
    Rodar `./mvnw verify -q` na raiz para gate final da Phase 1. Esperado: 6 modulos todos verdes (lib-shared, lib-consultas-client, api-email, api-storage, api-consultas, api-whatsapp). Total tests do reator >> 28 (somando os 4 modulos antigos com os tests do api-whatsapp).

    Confirmar:
    - Reactor Summary mostra 6 modulos com BUILD SUCCESS
    - Tempo total razoavel (<3 minutos)
    - Sem WARN/ERROR de Spring Boot/Jakarta no log

    Se algum modulo antigo quebrou (improvavel a essa altura), investigar — provavel regressao em ApiKeyFilter de PLAN-01 (passou em PLAN-01 Task 3 mas pode ter regredido se algo mais mudou em lib-shared apos).
  </action>
  <verify>
    <automated>./mvnw verify -q</automated>
  </verify>
  <done>
    - BUILD SUCCESS no reator inteiro
    - 6 modulos verdes
    - Phase 1 100% completa
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Test (mock) → Filter chain real | MockMvc + webAppContextSetup carrega filters reais; assertions validam que cada filter rodou correto |
| Test fixtures → application-test.yml | dummy values nao secret-leaking; appSecret = "test-app-secret" e safe pra reusar (nao e Meta secret real) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-07-01 | Spoofing | Test usa `@WebMvcTest` ao inves de `@SpringBootTest` (filters nao rodam) | mitigate | Comentario inline + uso explicito de `@SpringBootTest`. Se algum dev futuro tentar reescrever pra `@WebMvcTest` por "performance", os tests `post_*_retorna_401` falharao (filter nao roda → 200), gate empirico. |
| T-07-02 | Tampering | Test fixture com signature hardcoded fica stale se appSecret mudar | mitigate | Tests COMPUTAM signature em runtime via Mac.getInstance — automaticamente alinhada com appSecret de application-test.yml. Sem hardcoded hex. |
| T-07-03 | Information Disclosure | Test logs imprimem appSecret real | accept | appSecret de test e "test-app-secret" (dummy). Mesmo se logado, sem risco. Production usa `${WHATSAPP_APP_SECRET}` env var. |
| T-07-04 | DoS | Test demora muito (multiple SpringBootTest contexts) | accept | 4 test classes carregam mesmo contexto via @SpringBootTest cache do Spring; tempo total ~30-60s aceitavel para Phase 1. Se quiser otimizar, Phase 6 pode introduzir base test class @SpringBootTest cached. |
</threat_model>

<verification>
## Phase Checks (MANDATORY — este e o phase gate)

1. `./mvnw -pl api-whatsapp test -Dtest=WebhookControllerIntegrationTest` — Tests run: 7, Failures: 0
2. `./mvnw verify -pl api-whatsapp` — BUILD SUCCESS, Tests run >= 28
3. `./mvnw verify` (root) — BUILD SUCCESS, 6 modulos verdes

## ROADMAP Phase 1 Success Criteria — Mapeamento Final

| ROADMAP Criterion | Como verificado |
|-------------------|------------------|
| 1. GET hub.challenge plain text + 403 | `WebhookControllerIntegrationTest.get_handshake_*` (3 testes) |
| 2. POST HMAC valido 200 + body modificado 401 + MessageDigest.isEqual | `WebhookControllerIntegrationTest.post_com_hmac_*` (3 testes) + `HmacValidatorTest` (11 testes) |
| 3. CachedBodyHttpServletRequest eager + UTF-8 portugues | `WebhookControllerIntegrationTest.post_com_payload_portugues_e_hmac_valido_retorna_200` |
| 4. Boot fail-fast em env var ausente + secrets nao em logs | `WhatsAppPropertiesValidationTest` (7 testes) — fechado em PLAN-03 |
| 5. Flyway V1-V4 + mvnw verify verde com H2 | `FlywayMigrationTest` (3 testes) — fechado em PLAN-04; reforco aqui pelo proprio comando passar |
</verification>

<success_criteria>
- WebhookControllerIntegrationTest 7 cenarios verdes
- mvnw verify -pl api-whatsapp BUILD SUCCESS — Tests run >= 28
- mvnw verify (root) BUILD SUCCESS — 6 modulos
- Os 5 ROADMAP success criteria de Phase 1 todos verificaveis via test output
- 1 commit atomico
- Phase 1 completa
</success_criteria>

<commit>
Mensagem (Conventional Commits PT-BR):

```
test(api-whatsapp): adicionar integration test end-to-end fechando Phase 1

WebhookControllerIntegrationTest (@SpringBootTest + @AutoConfigureMockMvc)
cobre os 5 ROADMAP success criteria de Phase 1 atraves de 7 cenarios
end-to-end com filters reais:

- GET handshake com token correto retorna challenge plain text (PITFALLS C-10)
- GET handshake com token errado / mode != subscribe retorna 403
- POST com HMAC valido retorna 200
- POST com HMAC de body diferente (1 byte flip) retorna 401
- POST sem header X-Hub-Signature-256 retorna 401
- POST com payload portugues UTF-8 (Olá, gostaria de um orçamento) +
  HMAC valido retorna 200 — fecha PITFALLS C-04 end-to-end

Phase gate: 28+ tests verdes em mvnw verify -pl api-whatsapp; reator
inteiro mvnw verify (root) BUILD SUCCESS com 6 modulos.

Refs: WEB-01..04 (REQUIREMENTS.md), 01-RESEARCH.md §12.2, ROADMAP Phase 1
PITFALLS C-04 / C-10
```

Comando:
```bash
node $HOME/.claude/get-shit-done/bin/gsd-tools.cjs commit "test(api-whatsapp): adicionar integration test end-to-end fechando Phase 1" --files \
  api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookControllerIntegrationTest.java
```
</commit>

<risks>
- **MockMvc + filters customizados drift** — `MockMvcBuilders.webAppContextSetup(context).build()` deve auto-incluir filters do FilterRegistrationBean. Se algum filter nao rodar (sintoma: POST sem HMAC retorna 200), opcoes:
  1. Adicionar explicitamente: `.addFilters(filterRegistrationBean.getFilter())`
  2. Usar `org.springframework.test.web.servlet.setup.MockMvcConfigurers` se disponivel
  3. Em ultima instancia, usar `@AutoConfigureMockMvc` annotation alternative ao `MockMvcBuilders` builder manual
- **Time-out / contexto Spring lento** — 4 test classes (Properties, Flyway, HmacValidator, WebhookIntegration) carregam contexto SpringBoot. Cache do Spring deve reusar contexto se config identica. Se demora exagerada (>2min), considerar marcar Properties + Flyway + WebhookIntegration com mesmas annotations + profile pra cachear.
- **A4 (RESEARCH): @RequestParam("hub.mode") com ponto** — falha aqui apareceria como GET retornando 400 ao inves de 200. Test `get_handshake_*` e o gate. Fallback: ler HttpServletRequest direto no controller GET (mais codigo, mas robusto contra esse risco).
- **content().string("abc123") vs content().string(equalTo("abc123"))** — primeira forma faz assertion exata. Se controller adicionar whitespace/newline final (improvavel mas), test quebra. Prefer assertion exata para gate ser estrito (Meta rejeita qualquer ruido).
- **HexFormat.of().formatHex** vs uppercase — Meta computa lowercase hex. Java HexFormat.of() default e lowercase, ok.
</risks>

<output>
Apos completar, criar `.planning/phases/01-fundacao-hmac-webhook/01-07-SUMMARY.md` com:
- 7 tests integration verdes
- mvnw verify -pl api-whatsapp BUILD SUCCESS — Tests run >= 28
- mvnw verify (root) BUILD SUCCESS — 6 modulos
- Mapeamento explicito dos 5 ROADMAP success criteria → test methods
- Confirmacao de PITFALLS cobertos: C-02 (CachedBody eager), C-03 (MessageDigest.isEqual via HmacValidatorTest), C-04 (UTF-8 end-to-end), C-10 (plain text response), C-11 (accesslog disabled em yml)
- Commit hash
- Phase 1 status: COMPLETA — pronto para `/gsd-verify-phase 1` e `/gsd-transition`
</output>
