---
phase: 01-fundacao-hmac-webhook
plan: 06
type: execute
wave: 6
depends_on:
  - "01-05"
files_modified:
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/HmacSignatureFilter.java  # NEW
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/SecurityConfig.java  # NEW
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WebhookController.java  # NEW
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/HealthController.java  # NEW
autonomous: true
requirements:
  - WEB-01  # GET /webhook/whatsapp ecoa hub.challenge plain text + 403 em token errado
  - WEB-02  # COMPLETO — POST valida HMAC via filter (HmacValidator de PLAN-05) e retorna 401 se invalido
  - WEB-03  # COMPLETO — Filter usa CachedBodyHttpServletRequest no doFilterInternal antes de validar
  - WEB-04  # COMPLETO — POST stub retorna 200 imediatamente apos filter validar (D-04: sem parsing)
tags:
  - api-whatsapp
  - filter
  - controller
  - webhook
  - security-config

must_haves:
  truths:
    - "HmacSignatureFilter extends OncePerRequestFilter, doFilterInternal aplicado a POST /webhook/*"
    - "Filter wrappa request em CachedBodyHttpServletRequest antes de validar (HIGHEST_PRECEDENCE)"
    - "Filter delega validacao a HmacValidator passando rawBody + header X-Hub-Signature-256 + appSecret"
    - "Em HMAC invalido: response 401 + ErrorResponse JSON, NUNCA chama chain.doFilter"
    - "Em HMAC valido: chain.doFilter(cached, response) — controller recebe request com body cacheado"
    - "GET requests passam direto pelo filter (sem validacao HMAC; verifyToken e validado no controller)"
    - "SecurityConfig registra HmacSignatureFilter via FilterRegistrationBean com addUrlPatterns(/webhook/*) + setOrder(HIGHEST_PRECEDENCE)"
    - "SecurityConfig registra ApiKeyFilter via FilterRegistrationBean com (apiKey, Set.of(/webhook)) — webhook fica publico de API Key"
    - "WebhookController GET /webhook/whatsapp produces=TEXT_PLAIN_VALUE, retorna challenge ou 403 (PITFALLS C-10)"
    - "WebhookController GET valida verifyToken via MessageDigest.isEqual (UTF-8 bytes) — constant-time alinhado com HMAC"
    - "WebhookController POST /webhook/whatsapp retorna ResponseEntity.ok().build() (stub minimo D-04, sem parsing nem log de body)"
    - "HealthController existe com GET /health retornando 200 (espelha api-consultas pattern)"
    - "mvnw verify -pl api-whatsapp BUILD SUCCESS"
  artifacts:
    - path: "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/HmacSignatureFilter.java"
      provides: "Filter que valida HMAC-SHA256 antes de qualquer processamento Spring MVC"
      contains: "extends OncePerRequestFilter"
    - path: "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/SecurityConfig.java"
      provides: "FilterRegistrationBeans para HmacSignatureFilter (HIGHEST_PRECEDENCE em /webhook/*) e ApiKeyFilter (ordem 1, /webhook como path publico)"
      contains: "Ordered.HIGHEST_PRECEDENCE"
    - path: "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WebhookController.java"
      provides: "GET hub.challenge plain text + POST stub apos HMAC validation"
      contains: "produces = MediaType.TEXT_PLAIN_VALUE"
    - path: "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/HealthController.java"
      provides: "GET /health retornando 200 (path publico no ApiKeyFilter default)"
      contains: "@GetMapping(\"/health\")"
  key_links:
    - from: "HmacSignatureFilter.doFilterInternal"
      to: "CachedBodyHttpServletRequest + HmacValidator.isValid"
      via: "wrap → getCachedBody → validator.isValid → chain.doFilter|writeUnauthorized"
      pattern: "new CachedBodyHttpServletRequest\\(request\\)"
    - from: "SecurityConfig.hmacSignatureFilter()"
      to: "FilterRegistrationBean"
      via: "addUrlPatterns(/webhook/*) + setOrder(Ordered.HIGHEST_PRECEDENCE)"
      pattern: "Ordered\\.HIGHEST_PRECEDENCE"
    - from: "SecurityConfig.apiKeyFilter()"
      to: "new ApiKeyFilter(apiKey, Set.of(/webhook))"
      via: "construtor de 2 args criado em PLAN-01"
      pattern: "new ApiKeyFilter\\(apiKey, Set\\.of"
    - from: "WebhookController.GET"
      to: "verifyToken comparison via MessageDigest.isEqual"
      via: "consistencia com HMAC (PITFALLS C-10 + custo zero)"
      pattern: "MessageDigest\\.isEqual"
---

<objective>
Wire-up das pecas de PLAN-05 (HmacValidator + CachedBodyHttpServletRequest) atraves de:
1. `HmacSignatureFilter` — OncePerRequestFilter que aplica HMAC validation a POST /webhook/* (HIGHEST_PRECEDENCE)
2. `SecurityConfig` — registra ambos os filters (HmacSignatureFilter ordem 0, ApiKeyFilter ordem 1 com /webhook como path publico extra via construtor 2-args criado em PLAN-01)
3. `WebhookController` — GET emite hub.challenge em plain text (PITFALLS C-10) com verifyToken comparado via MessageDigest.isEqual; POST e stub minimo (D-04) que retorna 200 imediatamente
4. `HealthController` — espelha pattern de api-consultas para `/health` (path publico default no ApiKeyFilter)

Purpose: Decisao D-01 (Filter pattern + service) + D-02 (uso do construtor 2-args do ApiKeyFilter) + D-04 (POST stub sem parsing). Este plan fecha os 4 success criteria de WEB-01..04 (modulo o teste integration de PLAN-07).

Output:
- 4 novos arquivos Java no api-whatsapp
- mvnw verify -pl api-whatsapp BUILD SUCCESS (sem novos tests neste plan; PLAN-07 adiciona integration tests)
- App pode bootar localmente (`mvnw spring-boot:run -pl api-whatsapp -Dspring-boot.run.profiles=test` deveria boot — opcional verificar)
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/STATE.md
@.planning/phases/01-fundacao-hmac-webhook/01-CONTEXT.md
@.planning/phases/01-fundacao-hmac-webhook/01-RESEARCH.md
@.planning/phases/01-fundacao-hmac-webhook/01-05-SUMMARY.md
@.planning/research/PITFALLS.md
@api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/HmacValidator.java
@api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/CachedBodyHttpServletRequest.java
@api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/WhatsAppProperties.java
@api-consultas/src/main/java/br/com/erpkit/consultas/config/SecurityConfig.java
@lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java
@lib-shared/src/main/java/br/com/erpkit/shared/dto/ErrorResponse.java

<interfaces>
<!-- HmacSignatureFilter (RESEARCH §3.2 linhas 309-384) -->

```java
package br.com.erpkit.whatsapp.web;

public class HmacSignatureFilter extends OncePerRequestFilter {
    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final HmacValidator validator;
    private final WhatsAppProperties properties;

    public HmacSignatureFilter(HmacValidator validator, WhatsAppProperties properties) { ... }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
        // GET passa direto (handshake handled no controller)
        if (!HttpMethod.POST.matches(request.getMethod())) { chain.doFilter(request, response); return; }

        // Wrap PRIMEIRO
        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
        byte[] body = cached.getCachedBody();
        String signature = request.getHeader(SIGNATURE_HEADER);

        if (!validator.isValid(body, signature, properties.getAppSecret())) {
            log.warn("HMAC invalido em POST {} — rejeitado com 401", request.getRequestURI());
            writeUnauthorized(response);
            return;
        }

        chain.doFilter(cached, response);
    }

    private void writeUnauthorized(HttpServletResponse response) { /* 401 + ErrorResponse JSON */ }
}
```

<!-- SecurityConfig (RESEARCH §3.3 linhas 388-427) -->

```java
@Configuration
public class SecurityConfig {
    @Bean
    public FilterRegistrationBean<HmacSignatureFilter> hmacSignatureFilter(HmacValidator v, WhatsAppProperties p) {
        FilterRegistrationBean<HmacSignatureFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new HmacSignatureFilter(v, p));
        reg.addUrlPatterns("/webhook/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(@Value("${modulo.api-key:}") String apiKey) {
        FilterRegistrationBean<ApiKeyFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new ApiKeyFilter(apiKey, Set.of("/webhook")));
        reg.addUrlPatterns("/*");
        reg.setOrder(1);
        return reg;
    }
}
```

<!-- WebhookController (RESEARCH §4 linhas 434-500) -->

```java
@RestController
@RequestMapping("/webhook")
public class WebhookController {
    private final WhatsAppProperties properties;

    @GetMapping(value = "/whatsapp", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verificar(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {
        boolean modeOk = "subscribe".equals(mode);
        byte[] expected = properties.getVerifyToken().getBytes(StandardCharsets.UTF_8);
        byte[] received = (verifyToken == null ? new byte[0] : verifyToken.getBytes(StandardCharsets.UTF_8));
        boolean tokenOk = MessageDigest.isEqual(expected, received);
        if (modeOk && tokenOk) { return ResponseEntity.ok(challenge); }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping("/whatsapp")
    public ResponseEntity<Void> receber() {
        log.debug("Webhook POST recebido com HMAC valido (stub Phase 1)");
        return ResponseEntity.ok().build();
    }
}
```

<!-- HealthController — espelhar api-consultas/HealthController se existir, ou criar minimo -->

```java
@RestController
public class HealthController {
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "modulo", "api-whatsapp"));
    }
}
```

<!-- ApiKeyFilter de PLAN-01 (lib-shared) -->
- 2-arg constructor: `new ApiKeyFilter(apiKey, Set.of("/webhook"))`
- Default public paths: /health, /api/info, /swagger-ui, /v3/api-docs
- Combinado com /webhook → todos publicos (sem X-API-Key)

<!-- ErrorResponse (lib-shared) -->
- Construtor: `new ErrorResponse(int status, String erro, String mensagem)`
- Usado pelo HmacSignatureFilter para escrever 401 JSON
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Criar HmacSignatureFilter.java</name>
  <files>api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/HmacSignatureFilter.java</files>
  <action>
    Copiar integralmente o codigo Java da secao 3.2 do `01-RESEARCH.md` (linhas 309-384) para `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/HmacSignatureFilter.java`.

    **Verificar imports:**
    - `br.com.erpkit.shared.dto.ErrorResponse` (lib-shared)
    - `br.com.erpkit.whatsapp.config.WhatsAppProperties`
    - `br.com.erpkit.whatsapp.service.HmacValidator`
    - `com.fasterxml.jackson.databind.ObjectMapper`
    - `com.fasterxml.jackson.datatype.jsr310.JavaTimeModule`
    - `jakarta.servlet.{FilterChain, ServletException}` (Jakarta, NAO javax)
    - `jakarta.servlet.http.{HttpServletRequest, HttpServletResponse}`
    - `org.springframework.http.{HttpMethod, MediaType}`
    - `org.springframework.web.filter.OncePerRequestFilter`

    **Logica critica (per RESEARCH + PITFALLS C-02):**
    - GET passa direto sem validacao (verifyToken vai pro controller)
    - Wrap em CachedBodyHttpServletRequest **PRIMEIRO** (antes de qualquer leitura)
    - Read header `X-Hub-Signature-256` via `request.getHeader` (nao via cached — header nao mexe no body)
    - Delega `validator.isValid(body, signature, properties.getAppSecret())`
    - Em invalido: `writeUnauthorized(response)` + `return` (NAO chama `chain.doFilter`)
    - Em valido: `chain.doFilter(cached, response)` (passa o wrapper para downstream poder ler body de novo se quiser)
    - **NAO logar** body, signature header (PITFALLS — anti-pattern documentado em RESEARCH §3.2 + PITFALLS Security Mistakes)

    **writeUnauthorized:** seta status 401, content-type application/json, escreve `new ErrorResponse(401, "Nao autorizado", "Assinatura HMAC invalida")` via Jackson.
  </action>
  <verify>
    <automated>./mvnw -pl api-whatsapp compile -q && grep -c "Ordered.HIGHEST_PRECEDENCE\|new CachedBodyHttpServletRequest" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/HmacSignatureFilter.java</automated>
  </verify>
  <done>
    - Compila sem erros
    - `new CachedBodyHttpServletRequest(request)` aparece no doFilterInternal
    - GET requests sao puladas (early return com chain.doFilter)
    - Em invalido, `writeUnauthorized` chama `response.setStatus(401)` + ErrorResponse JSON
  </done>
</task>

<task type="auto">
  <name>Task 2: Criar SecurityConfig.java</name>
  <files>api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/SecurityConfig.java</files>
  <action>
    Copiar integralmente o codigo Java da secao 3.3 do `01-RESEARCH.md` (linhas 388-427) para `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/SecurityConfig.java`.

    **Verificar imports:**
    - `br.com.erpkit.shared.security.ApiKeyFilter` (lib-shared)
    - `br.com.erpkit.whatsapp.service.HmacValidator`
    - `br.com.erpkit.whatsapp.web.HmacSignatureFilter`
    - `org.springframework.beans.factory.annotation.Value`
    - `org.springframework.boot.web.servlet.FilterRegistrationBean`
    - `org.springframework.context.annotation.{Bean, Configuration}`
    - `org.springframework.core.Ordered`
    - `java.util.Set`

    **2 beans criticos (per D-01 e D-02):**

    1. `hmacSignatureFilter` — `FilterRegistrationBean<HmacSignatureFilter>`:
       - `setFilter(new HmacSignatureFilter(validator, properties))` (DI por argument)
       - `addUrlPatterns("/webhook/*")` — restringe a webhook only (endpoints internos do ERP nao precisam HMAC)
       - `setOrder(Ordered.HIGHEST_PRECEDENCE)` — gate mais cedo possivel no pipeline (PITFALLS C-02)

    2. `apiKeyFilter` — `FilterRegistrationBean<ApiKeyFilter>`:
       - `@Value("${modulo.api-key:}") String apiKey` injetado
       - `setFilter(new ApiKeyFilter(apiKey, Set.of("/webhook")))` — usa construtor de 2 args (PLAN-01)
       - `addUrlPatterns("/*")` — aplica a todos os paths
       - `setOrder(1)` — depois do HMAC filter

    **Comportamento esperado em runtime:**
    - GET/POST `/webhook/whatsapp` → HMAC filter (POST valida; GET passa) → ApiKeyFilter ve `/webhook` em publicPaths e libera → controller
    - GET `/api/whatsapp/status` (futuro Phase 4) → HMAC filter pula (URL pattern nao casa) → ApiKeyFilter exige X-API-Key → controller
    - GET `/health` → ApiKeyFilter ve `/health` em DEFAULT_PUBLIC_PATHS e libera → HealthController
  </action>
  <verify>
    <automated>./mvnw -pl api-whatsapp compile -q && grep -c "Ordered.HIGHEST_PRECEDENCE\|Set.of.\"/webhook\"" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/SecurityConfig.java</automated>
  </verify>
  <done>
    - Compila sem erros
    - 2 `@Bean` declarations
    - HmacSignatureFilter registrado em /webhook/* com HIGHEST_PRECEDENCE
    - ApiKeyFilter registrado em /* com /webhook como path publico extra
    - `new ApiKeyFilter(apiKey, Set.of("/webhook"))` confirma uso do construtor de 2 args de PLAN-01
  </done>
</task>

<task type="auto">
  <name>Task 3: Criar WebhookController.java</name>
  <files>api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WebhookController.java</files>
  <action>
    Copiar integralmente o codigo Java da secao 4 do `01-RESEARCH.md` (linhas 434-500) para `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WebhookController.java`.

    **Verificar imports:**
    - `br.com.erpkit.whatsapp.config.WhatsAppProperties`
    - `org.springframework.http.{HttpStatus, MediaType, ResponseEntity}`
    - `org.springframework.web.bind.annotation.*` (Get/PostMapping, RequestMapping, RequestParam, RestController)
    - `java.nio.charset.StandardCharsets`
    - `java.security.MessageDigest`

    **Endpoint GET — pontos criticos (PITFALLS C-10):**
    - `produces = MediaType.TEXT_PLAIN_VALUE` — essencial. Sem isso, `@RestController` serializa via Jackson e o challenge sai como `"abc"` (com aspas) — Meta rejeita.
    - `@RequestParam("hub.mode")` literal com ponto — Spring MVC suporta porem precisa do nome explicito (RESEARCH §4 Notas + Assumption A4).
    - Comparacao do verifyToken via `MessageDigest.isEqual(expected.getBytes(UTF_8), received.getBytes(UTF_8))` — constant-time, consistente com HMAC. Custo zero, defesa em profundidade.
    - Em sucesso: `ResponseEntity.ok(challenge)` (string solta, content-type ja definido por produces).
    - Em falha (mode != "subscribe" OU token nao bate): `ResponseEntity.status(HttpStatus.FORBIDDEN).build()`.
    - Logs: INFO em sucesso ("Webhook verificado pelo Meta — hub.challenge ecoado"), WARN em falha ("Verificacao do webhook rejeitada — mode=X"). NUNCA logar verifyToken received.

    **Endpoint POST — stub minimo (D-04):**
    - HMAC ja foi validado pelo HmacSignatureFilter — se chegou aqui, e valido. Confiar.
    - `return ResponseEntity.ok().build();` — corpo vazio, 200.
    - Log nivel `DEBUG` apenas: `log.debug("Webhook POST recebido com HMAC valido (stub Phase 1)")`. **NAO** logar body, header, wamid (Phase 2 territory).
    - **NAO usar `@RequestBody`** — body ja foi consumido pelo CachedBodyHttpServletRequest no filter; o controller nao precisa parsear. Phase 2 vai mudar para `@RequestBody String corpo` quando o parser for adicionado.
  </action>
  <verify>
    <automated>./mvnw -pl api-whatsapp compile -q && grep -c "TEXT_PLAIN_VALUE\|MessageDigest.isEqual" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WebhookController.java</automated>
  </verify>
  <done>
    - Compila sem erros
    - `produces = MediaType.TEXT_PLAIN_VALUE` no GET (PITFALLS C-10)
    - `MessageDigest.isEqual` na comparacao do verifyToken
    - POST retorna `ResponseEntity.ok().build()` (sem body, sem parsing, sem log de body — D-04)
    - `@RequestParam("hub.mode")` com nome literal entre aspas
  </done>
</task>

<task type="auto">
  <name>Task 4: Criar HealthController.java</name>
  <files>api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/HealthController.java</files>
  <action>
    Criar `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/HealthController.java` espelhando o pattern simples de `api-consultas` (se existir um HealthController la) ou seguindo um stub minimo.

    Implementacao recomendada:

    ```java
    package br.com.erpkit.whatsapp.controller;

    import org.springframework.http.ResponseEntity;
    import org.springframework.web.bind.annotation.GetMapping;
    import org.springframework.web.bind.annotation.RestController;

    import java.util.Map;

    /**
     * Liveness probe. Path /health e parte dos DEFAULT_PUBLIC_PATHS do ApiKeyFilter
     * (lib-shared) — nao precisa de X-API-Key.
     *
     * Phase 1 retorna estado static. Phase 4 (WHATS-17) podera incluir validacao
     * de WABA subscription via Graph API (PITFALLS C-12 — shadow delivery).
     */
    @RestController
    public class HealthController {

        @GetMapping("/health")
        public ResponseEntity<Map<String, String>> health() {
            return ResponseEntity.ok(Map.of(
                "status", "UP",
                "modulo", "api-whatsapp"
            ));
        }
    }
    ```

    **Justificativa:** Tasks anteriores (PLAN-02, PLAN-03) configuraram `application.yml` e `SecurityConfig` que assumem `/health` existe. Sem o controller, GET /health retorna 404 e qualquer test ou monitor que verifica liveness falha. Health check completo (PITFALLS C-12 — verificar `subscribed_apps`) e Phase 4 territory; aqui e so um stub.

    **NAO usar Spring Boot Actuator** — adiciona dep extra sem justificativa em Phase 1 (RESEARCH §13 Open Q1). Controller manual e suficiente.
  </action>
  <verify>
    <automated>./mvnw -pl api-whatsapp compile -q && grep "@GetMapping(\"/health\")" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/HealthController.java</automated>
  </verify>
  <done>
    - Compila sem erros
    - `GET /health` retorna 200 + JSON com `status: UP`
    - Sem dep de Spring Boot Actuator
  </done>
</task>

<task type="auto">
  <name>Task 5: Verificar build do reator</name>
  <files>(nenhum modificado)</files>
  <action>
    Rodar `./mvnw verify -pl api-whatsapp -q`. Esperado: BUILD SUCCESS com Tests run >= 21 (sem novos tests neste plan; PLAN-07 adiciona integration tests). O build verifica que:
    - Todas as classes compilam (4 novas + as de PLAN-03..05)
    - SpringBootTest do FlywayMigrationTest e WhatsAppPropertiesValidationTest continuam passando (contexto sobe com os 2 filters registrados)
    - Application context loads — ou seja, os beans de SecurityConfig sao criados sem erro

    Se algum teste falhar:
    - "ApiKeyFilter constructor not found" → PLAN-01 nao foi completado (construtor de 2 args ausente)
    - "Bean creation failed: HmacValidator" → HmacValidator de PLAN-05 nao tem `@Service` ou nao foi escaneado
    - "Bean creation failed: WhatsAppProperties" → PLAN-03 `@EnableConfigurationProperties` ausente
    - "FilterRegistrationBean already registered" → conflito com algum auto-config; verificar que `@Configuration` SecurityConfig nao tem `@Order` redundante
  </action>
  <verify>
    <automated>./mvnw verify -pl api-whatsapp -q</automated>
  </verify>
  <done>
    - BUILD SUCCESS
    - Tests run >= 21 (existentes), Failures: 0
    - Output do Spring Boot ao iniciar contexto mostra 2 FilterRegistrationBean criados
  </done>
</task>

<task type="auto">
  <name>Task 6 (smoke test opcional): Boot manual via spring-boot:run</name>
  <files>(nenhum modificado)</files>
  <action>
    OPCIONAL — verificacao manual de boot (nao obrigatorio para fechar o plano, mas util para detectar problemas que tests automaticos nao pegam).

    Em uma shell separada (ou em background):
    ```bash
    SPRING_PROFILES_ACTIVE=test ./mvnw spring-boot:run -pl api-whatsapp -q
    ```

    Em outra shell:
    ```bash
    # GET hub.challenge happy path
    curl -i "http://localhost:9193/webhook/whatsapp?hub.mode=subscribe&hub.verify_token=test-verify-token&hub.challenge=abc123"
    # Esperado: HTTP/1.1 200 + Content-Type: text/plain + body literal "abc123" (sem aspas)

    # GET com token errado
    curl -i "http://localhost:9193/webhook/whatsapp?hub.mode=subscribe&hub.verify_token=WRONG&hub.challenge=abc123"
    # Esperado: HTTP/1.1 403

    # GET /health
    curl -i "http://localhost:9193/health"
    # Esperado: HTTP/1.1 200 + JSON {"status":"UP","modulo":"api-whatsapp"}

    # POST sem header HMAC
    curl -i -X POST "http://localhost:9193/webhook/whatsapp" -H "Content-Type: application/json" -d '{}'
    # Esperado: HTTP/1.1 401 + JSON ErrorResponse
    ```

    Killar o processo apos verificacao.

    **Esta task e OPCIONAL** — se o ambiente nao permitir port-binding em 9193 (ja em uso, etc), pular. Os tests automaticos de PLAN-07 cobrem todos esses fluxos via MockMvc.
  </action>
  <verify>
    <automated>echo "Manual smoke test — pular se ambiente nao permite port binding"</automated>
  </verify>
  <done>
    - Smoke test manual confirma comportamento correto OU foi pulado por ambiente
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Internet (Cloudflare Tunnel) → Tomcat | Trafico nao confiavel; HmacSignatureFilter e o gate de autenticidade |
| Filter chain → Controller | Apos passar pelos 2 filters, request e considerada autenticada |
| Controller → ApplicationContext | WhatsAppProperties.appSecret/verifyToken consumidos so pelo HmacSignatureFilter e WebhookController, nunca expostos |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-06-01 | Spoofing | Attacker POST /webhook/whatsapp sem assinatura | mitigate | HmacSignatureFilter HIGHEST_PRECEDENCE valida e retorna 401 antes de chegar no MVC. Test PLAN-07 `post_sem_header_signature_retorna_401`. |
| T-06-02 | Spoofing | Attacker POST com signature de body diferente | mitigate | HmacSignatureFilter usa MessageDigest.isEqual constant-time (de PLAN-05). Test PLAN-07 `post_com_hmac_invalido_retorna_401`. |
| T-06-03 | Information Disclosure | verifyToken comparado via String.equals (timing oracle) | mitigate | WebhookController GET usa MessageDigest.isEqual (consistencia com HMAC). Custo zero. Test PLAN-07 `get_handshake_*`. |
| T-06-04 | Tampering | hub.challenge wrapped em JSON pelo Jackson auto | mitigate | `produces = MediaType.TEXT_PLAIN_VALUE` (PITFALLS C-10) — Spring nao serializa, retorna body bruto. Test PLAN-07 verifica content-type literal `text/plain`. |
| T-06-05 | DoS | Body grande consumindo memoria no CachedBodyHttpServletRequest | accept | Webhook do Meta tem payload pequeno (<10KB tipico). Tomcat default `max-http-form-post-size` ja limita; sem mitigacao adicional em Phase 1. Phase 6 pode adicionar limite explicito. |
| T-06-06 | Spoofing | ApiKeyFilter permitir /webhook sem API key (intencional) | accept | Webhook **e** publico (validado por HMAC, nao por API key). Configurado deliberadamente via construtor de 2 args (D-02). Defesa: HMAC como auth real. |
| T-06-07 | Information Disclosure | POST body logado em DEBUG | mitigate | log.debug sem incluir body (RESEARCH §4 + PITFALLS Security Mistakes). NUNCA upgradar level pra DEBUG do `org.springframework.web` (PITFALLS C-09 + RESEARCH application.yml `org.springframework.web: INFO`). |
| T-06-08 | Spoofing | Filter ordem trocada (ApiKeyFilter antes do HMAC) | mitigate | `setOrder(Ordered.HIGHEST_PRECEDENCE)` no HmacSignatureFilter vs `setOrder(1)` no ApiKeyFilter — HMAC sempre primeiro. Test PLAN-07 confirma indiretamente (POST /webhook sem HMAC retorna 401, nao passa pelo controller). |
</threat_model>

<verification>
## Phase Checks

1. `grep "Ordered.HIGHEST_PRECEDENCE" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/SecurityConfig.java` retorna 1
2. `grep 'addUrlPatterns("/webhook/\\*")' api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/SecurityConfig.java` retorna 1
3. `grep "Set.of(\"/webhook\")" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/SecurityConfig.java` retorna 1 (uso do construtor de 2 args)
4. `grep "TEXT_PLAIN_VALUE" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WebhookController.java` retorna 1 (PITFALLS C-10)
5. `grep "MessageDigest.isEqual" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WebhookController.java` retorna 1 (verifyToken constant-time)
6. `grep "@RequestBody" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WebhookController.java` retorna 0 (POST stub D-04 nao parsear body)
7. `grep "new CachedBodyHttpServletRequest" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/HmacSignatureFilter.java` retorna 1
8. `./mvnw verify -pl api-whatsapp` BUILD SUCCESS, Tests run >= 21
</verification>

<success_criteria>
- HmacSignatureFilter aplica HMAC validation a POST /webhook/* com HIGHEST_PRECEDENCE
- SecurityConfig registra 2 FilterRegistrationBean (HMAC ordem 0, ApiKey ordem 1 com /webhook publico)
- WebhookController GET retorna challenge plain text (PITFALLS C-10) com MessageDigest.isEqual no verifyToken
- WebhookController POST e stub minimo (D-04) — apenas ResponseEntity.ok().build() apos HMAC valido
- HealthController atende GET /health com 200 (path publico default no ApiKeyFilter)
- mvnw verify -pl api-whatsapp BUILD SUCCESS (tests existentes continuam verdes; PLAN-07 adiciona integration tests)
- 1 commit atomico
</success_criteria>

<commit>
Mensagem (Conventional Commits PT-BR):

```
feat(api-whatsapp): adicionar HmacSignatureFilter + SecurityConfig + WebhookController

HmacSignatureFilter (OncePerRequestFilter, HIGHEST_PRECEDENCE em /webhook/*):
- Embrulha HttpServletRequest em CachedBodyHttpServletRequest (eager body read)
- Delega validacao a HmacValidator (PLAN-05) com appSecret de WhatsAppProperties
- 401 + ErrorResponse JSON em invalido (sem chain.doFilter)
- GET pula direto (verifyToken validado no controller)

SecurityConfig:
- HmacSignatureFilter ordem HIGHEST_PRECEDENCE em /webhook/*
- ApiKeyFilter (lib-shared, construtor 2-arg de PLAN-01) ordem 1 em /* com /webhook como publicPath extra

WebhookController:
- GET /webhook/whatsapp produces TEXT_PLAIN_VALUE (PITFALLS C-10), comparacao verifyToken via MessageDigest.isEqual
- POST stub minimo (D-04) — sem parsing, sem log de body, retorna 200 imediato

HealthController stub para liveness (path /health publico no ApiKeyFilter default).

Refs: D-01 + D-02 + D-04 (CONTEXT.md), WEB-01..04 (REQUIREMENTS.md), 01-RESEARCH.md §3 §4
PITFALLS C-02 / C-03 / C-04 / C-09 / C-10 / C-11
```

Comando:
```bash
node $HOME/.claude/get-shit-done/bin/gsd-tools.cjs commit "feat(api-whatsapp): adicionar HmacSignatureFilter + SecurityConfig + WebhookController" --files \
  api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/HmacSignatureFilter.java \
  api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/SecurityConfig.java \
  api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WebhookController.java \
  api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/HealthController.java
```
</commit>

<risks>
- **A4 (RESEARCH §13): `@RequestParam("hub.mode")` com ponto pode falhar em Spring 3.5.x** — risco de boot OK mas request retornar 400. Mitigacao: smoke test (Task 6 opcional) ou integration test (PLAN-07) detecta. Fallback: ler via `HttpServletRequest.getParameter("hub.mode")` injetado como argumento.
- **Ordem de Filter pode ser ignorada por SecurityAutoConfiguration** — Spring Security esta auto-ativada se starter-security estiver no classpath. Verificar pom (PLAN-02): NAO temos `spring-boot-starter-security` em api-whatsapp. ApiKeyFilter e custom (lib-shared), nao Spring Security — ordem e respeitada via FilterRegistrationBean.
- **HealthController choca com Spring Boot Actuator** — RESEARCH descartou actuator em Phase 1 (Open Q1). Custom controller em /health funciona porque actuator nao esta no classpath. Se Phase 4 trouxer actuator, renomear para /health-custom ou desabilitar actuator's /actuator/health para evitar conflito.
- **HmacSignatureFilter ObjectMapper static** — RESEARCH §3.2 codigo cria new ObjectMapper static. Este e o mesmo padrao do ApiKeyFilter. Risco: ObjectMapper nao thread-safe? Na verdade ObjectMapper E thread-safe apos config (Jackson docs). Sem risco.
- **Smoke test (Task 6) pode falhar se port 9193 ja em uso na maquina dev** — opcional, pular. Integration tests (PLAN-07) usam @SpringBootTest com RANDOM_PORT.
</risks>

<output>
Apos completar, criar `.planning/phases/01-fundacao-hmac-webhook/01-06-SUMMARY.md` com:
- HmacSignatureFilter + SecurityConfig + WebhookController + HealthController criados
- Reactor `mvnw verify -pl api-whatsapp` BUILD SUCCESS
- Smoke test (opcional) status: passou / pulou
- Confirmacao: 4 endpoints registrados (GET /webhook/whatsapp, POST /webhook/whatsapp, GET /health, e 2 endpoints de springdoc /v3/api-docs e /swagger-ui)
- Commit hash
- Reminder: PLAN-07 vai integration-testar todo o stack via MockMvc, fechando os 5 success criteria do ROADMAP Phase 1
</output>
