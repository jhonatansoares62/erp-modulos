# Phase 1: Fundacao HMAC + Webhook - Research

**Researched:** 2026-05-05
**Domain:** Spring Boot 3.5.9 Servlet Filter + HMAC-SHA256 + Flyway PostgreSQL/H2 portavel
**Confidence:** HIGH (codigo de referencia lido no monorepo + decisoes ja locked em CONTEXT.md + PITFALLS validados)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 — HMAC validation via Servlet Filter at HIGHEST_PRECEDENCE delegando a HmacValidator service:**
  - `HmacSignatureFilter extends OncePerRequestFilter`, registrado via `FilterRegistrationBean.addUrlPatterns("/webhook/*")` + `setOrder(Ordered.HIGHEST_PRECEDENCE)`. Embrulha request em `CachedBodyHttpServletRequest`, le header `X-Hub-Signature-256`, delega ao `HmacValidator`.
  - `HmacValidator` (`@Service`, sem dependencia de Servlet API) — pure function `boolean isValid(byte[] rawBody, String signatureHeader, String appSecret)`, comparacao via `MessageDigest.isEqual`, NUNCA lanca excecao por input malformado.

- **D-02 — Modificar lib-shared/ApiKeyFilter para aceitar `Set<String> additionalPublicPaths`:**
  - Construtor de 1 arg preservado (backward-compat com api-email/api-storage/api-consultas).
  - Construtor de 2 args novo: `new ApiKeyFilter(apiKey, additionalPublicPaths)`.
  - `api-whatsapp/SecurityConfig` instancia com `Set.of("/webhook")`.

- **D-03 — Fail-fast via Bean Validation:**
  - `WhatsAppProperties` com `@ConfigurationProperties("app.modulos.whatsapp")` + `@Validated` + `@NotBlank` em todos os 5 campos secretos. Mensagens em PT-BR nomeando a env var faltante. `toString()` mascara `accessToken`/`appSecret`/`verifyToken`.
  - `Duration callbackTimeout` opcional, default `Duration.ofSeconds(5)`.

- **D-04 — POST /webhook stub retorna 200 vazio apos HMAC valido:**
  - Sem parsing, sem log do body, sem persistencia, sem callback. Apenas `ResponseEntity.ok().build()`. Phase 2 substitui stub por parse + idempotency + async dispatch.

- **D-05 — Logging strategy:**
  - `CommonsRequestLoggingFilter` desligado.
  - `logging.level.org.springframework.web: INFO` (nao DEBUG).
  - `server.tomcat.accesslog.enabled: false` (explicito mesmo sendo default).
  - `management.endpoint.env.keys-to-sanitize` cobrindo `accessToken`, `appSecret`, `verifyToken`.

- **D-06 — Migrations V1-V4 em SQL portavel PostgreSQL + H2 PostgreSQL-mode:**
  - `BIGINT GENERATED ALWAYS AS IDENTITY` (NAO `BIGSERIAL`).
  - `CREATE SCHEMA IF NOT EXISTS whatsapp;` na V1, antes do CREATE TABLE.
  - V4 = placeholder minimo (`telefone PK`, `ultima_atualizacao`).

### Claude's Discretion

User delegou todas as 4 areas (definidas no menu de discussao) — todas as decisoes acima sao defaults recomendados, reversiveis em phases futuras se a implementacao mostrar atrito. Esta RESEARCH propoe os detalhes de implementacao concretos consistentes com cada decisao locked.

### Deferred Ideas (OUT OF SCOPE)

- Parser de `WebhookPayloadDTO` (`message.text`, `interactive.button_reply`, `interactive.list_reply`, `message.document`, `statuses.status`) — Phase 2 (WEB-07).
- Idempotencia por wamid — Phase 2 (WEB-05/WEB-06).
- Async dispatch (`@Async`, `@TransactionalEventListener(AFTER_COMMIT)`) — Phase 3 (ROU-01..05).
- Health check que valida WABA subscription via Graph API (PITFALLS C-12) — Phase 4 ou Phase 6 (WHATS-17).
- Mascarar `Authorization: Bearer` em logs do RestClient (PITFALLS C-09) — Phase 4 quando `WhatsAppCloudClient` for criado.
- Testes de carga / verificar P95 < 1s do POST sob delay do ERP (PITFALLS C-05) — Phase 6 com WireMock.
- Testcontainers para PostgreSQL real em CI — adiavel se H2 PostgreSQL-mode rodar bem; revisitar Phase 6 se houver gap de portabilidade.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| WEB-01 | `GET /webhook/whatsapp` ecoa `hub.challenge` em `text/plain` (sem JSON), 200 se verifyToken bate, senao 403 | Secao 4 — assinatura `produces=MediaType.TEXT_PLAIN_VALUE`, comparacao via `MessageDigest.isEqual` em UTF-8 bytes |
| WEB-02 | `POST /webhook/whatsapp` valida HMAC-SHA256 timing-safe; 401 sem persistir se body modificado | Secao 3 — `HmacValidator.isValid()` com `Mac.getInstance("HmacSHA256")` + `MessageDigest.isEqual` |
| WEB-03 | HMAC validation usa custom `HttpServletRequestWrapper` que le bytes eagerly na construcao (nao `ContentCachingRequestWrapper`) | Secao 2 — `CachedBodyHttpServletRequest` com `StreamUtils.copyToByteArray` no construtor |
| WEB-04 | Webhook responde 200 ao Meta em <1s (margem do limite real de 5s); apenas HMAC validation no caminho sincrono | Secao 4 — POST stub retorna `ResponseEntity.ok().build()` imediatamente apos Filter validar |
| PER-01 | Schema PostgreSQL `whatsapp` usado pelo modulo via `flyway.schemas=whatsapp` + `spring.datasource.url` | Secao 8 — `application.yml` com `spring.flyway.schemas: whatsapp` + `default-schema: whatsapp` |
| CFG-01 | `WhatsAppProperties` com 5 campos `@NotBlank`/required + `callbackTimeout`; falha rapido no boot via Bean Validation | Secao 5 — classe completa com `@Validated` + mensagens PT-BR |
| CFG-02 | `application.yml` com placeholders `${WHATSAPP_*}`; nada hardcoded | Secao 8 — yml com `${WHATSAPP_PHONE_NUMBER_ID}` etc, sem defaults para os 5 secretos |
| CFG-03 | Logs nunca imprimem `accessToken` ou `appSecret`; ofuscar nos `toString()` das classes Properties | Secao 5 — `toString()` retorna `[REDACTED]` para 3 campos sensiveis + secao 8 com `keys-to-sanitize` |
| CFG-04 | Porta default 9193 configuravel via `server.port` | Secao 8 — `server.port: ${SERVER_PORT:9193}` |

</phase_requirements>

---

## Summary

Phase 1 entrega a fundacao de seguranca + infra base do modulo `api-whatsapp`. O pacote saira com: estrutura Maven nova registrada no reator, `WhatsAppApplication` apontando `scanBasePackages = "br.com.erpkit"` (cobre `lib-shared`), 5 secretos validados no boot por `@ConfigurationProperties` + `@Validated`, HMAC-SHA256 validado via `OncePerRequestFilter` em `HIGHEST_PRECEDENCE` aplicado SO em `/webhook/*`, e migrations Flyway V1-V4 aplicadas no schema `whatsapp` (portavel Postgres + H2 PostgreSQL-mode).

A unica modificacao em codigo compartilhado e cirurgica: `lib-shared/ApiKeyFilter` ganha um construtor de 2 args para permitir paths publicos adicionais (default vazio, backward-compat). Todos os 5 success criteria de ROADMAP Phase 1 sao coberts por testes unitarios + um SpringBootTest de integracao com H2.

**Primary recommendation:** Construir na ordem (i) `lib-shared/ApiKeyFilter` modificado + teste de regressao, (ii) `api-whatsapp/pom.xml` + `WhatsAppApplication` + `application.yml` minimo (boot vazio compila), (iii) `WhatsAppProperties` + teste de Bean Validation, (iv) migrations V1-V4 + `application-test.yml` (boot H2 verde), (v) `CachedBodyHttpServletRequest` + `HmacValidator` + testes unitarios isolados, (vi) `HmacSignatureFilter` + `SecurityConfig` + `WebhookController`, (vii) integration test MockMvc end-to-end.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| HMAC validation | API / Backend (Servlet Filter) | — | Gate mais cedo possivel no pipeline; rejeita payload hostil antes do MVC tocar |
| Verify-token check (GET) | API / Backend (Controller) | — | Comparacao simples com `properties.getVerifyToken()`; nao precisa de Filter dedicado |
| Body capture para HMAC | API / Backend (HttpServletRequestWrapper) | — | Eager-read no construtor; necessario porque InputStream so pode ser lido uma vez |
| Fail-fast de secretos | API / Backend (BeanFactory startup) | — | `@Validated` + `BindValidationException` durante refresh do contexto |
| Persistencia (schema) | Database / Storage (PostgreSQL/H2) | — | Apenas DDL via Flyway; sem entities ainda em Phase 1 |
| Path policy (publico vs privado) | API / Backend (lib-shared ApiKeyFilter) | — | Reusa filter existente; webhook entra como `additionalPublicPaths` (HMAC e a auth real) |
| Logging strategy | Cross-cutting (application.yml + Spring Boot defaults) | — | Sanitizacao via `keys-to-sanitize` + level INFO em `org.springframework.web` |

---

## 1. Implementation Strategy Overview

Phase 1 e construida em **7 etapas serializadas** que mantem o reator buildando a cada commit:

1. **lib-shared modificacao + regression test** — adicionar construtor `(String apiKey, Set<String> additionalPublicPaths)` em `ApiKeyFilter`. Construtor de 1 arg delega ao novo. Test cobre ambos. Reator continua verde para api-email/api-storage/api-consultas.
2. **Esqueleto Maven `api-whatsapp/`** — `pom.xml` espelhando `api-email`, `WhatsAppApplication`, `application.yml` minimo (apenas `server.port` + `spring.application.name`). Reator inclui novo modulo. `mvnw verify -pl api-whatsapp` verde mas sem nada util ainda.
3. **`WhatsAppProperties` + Bean Validation** — classe `@ConfigurationProperties` + `@Validated` + 5 `@NotBlank` + `Duration callbackTimeout`. Test verifica que falta de cada campo produz `BindValidationException` no boot.
4. **Migrations V1-V4 + `application-test.yml` H2** — schema `whatsapp` + 4 tabelas. Boot test (H2 PostgreSQL-mode) confirma migrations aplicadas.
5. **`CachedBodyHttpServletRequest` + `HmacValidator`** — wrapper customizado + service unit-testavel. Tests cobrem caminho positivo, body 1-byte modificado, header malformado, body vazio, payload portugues UTF-8.
6. **`HmacSignatureFilter` + `SecurityConfig` + `WebhookController`** — registracao do filter via `FilterRegistrationBean`, controller com 2 endpoints (GET text/plain, POST stub).
7. **Integration test MockMvc** — `@SpringBootTest` carrega contexto completo, dispara GET com challenge correto/errado, POST com HMAC correto/modificado. Cobre os 5 success criteria do ROADMAP.

## 2. CachedBodyHttpServletRequest

> Wrapper customizado que le os bytes do body **eagerly** no construtor (PITFALLS C-02 — `ContentCachingRequestWrapper` NAO funciona aqui).

### Implementacao concreta

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/CachedBodyHttpServletRequest.java
package br.com.erpkit.whatsapp.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * HttpServletRequestWrapper que captura os bytes brutos do body no construtor,
 * permitindo leituras subsequentes (Filter para HMAC + Controller para parsing futuro).
 *
 * NAO use ContentCachingRequestWrapper do Spring — ele e lazy e o cache fica vazio
 * antes do downstream consumir o stream (PITFALLS C-02).
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        // Eager read — bytes brutos preservados para HMAC sobre UTF-8 (PITFALLS C-04)
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    /** Acesso direto aos bytes para HMAC computation (NAO converter para String). */
    public byte[] getCachedBody() {
        return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override
            public int read() {
                return byteStream.read();
            }

            @Override
            public boolean isFinished() {
                return byteStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException("ReadListener nao suportado");
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        // Sempre UTF-8 — webhook do Meta e UTF-8 garantido, nao confiar em getCharacterEncoding()
        return new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(cachedBody), StandardCharsets.UTF_8));
    }
}
```

### Gotchas

- **`DelegatingServletInputStream` so existe em `spring-test`** (`org.springframework.mock.web.DelegatingServletInputStream`). Para producao, use a classe anonima `ServletInputStream` mostrada acima.
- **Multipart requests:** este wrapper NAO funciona corretamente para `multipart/form-data` (consumir o body inteiro quebra o parsing dos parts). Como webhook do Meta sempre chega como `application/json`, isso e nao-problema para Phase 1. Se Phase 4 precisar interceptar uploads, fazer wrapper diferente ou pular para esses paths.
- **`Content-Length`:** o wrapper preserva via `super(request)` — o servlet container ja populou `getContentLength()` antes do construtor rodar. Nao mexer.
- **Charset override em `getReader()`:** UTF-8 hardcoded e deliberado — `getCharacterEncoding()` pode retornar `ISO-8859-1` (default Servlet spec) e quebra HMAC com texto portugues (PITFALLS C-04). HMAC em si nao usa `getReader()`, usa `getCachedBody()` direto.
- **`getCachedBody()` retorna referencia mutavel.** Callers nao devem modificar o array. Em Phase 2 quando o parser ler isso, considerar `Arrays.copyOf` defensivo se houver paranoia.

## 3. HmacValidator + HmacSignatureFilter

> Split em duas camadas: (a) `HmacValidator` puro testavel, (b) `HmacSignatureFilter` que faz wrap + delega.

### 3.1 HmacValidator (service)

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/HmacValidator.java
package br.com.erpkit.whatsapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Valida HMAC-SHA256 do header X-Hub-Signature-256 contra os bytes brutos do body
 * usando o appSecret da WhatsAppProperties. NUNCA lanca excecao por input malformado —
 * sempre retorna boolean (PITFALLS C-02 — empty-array shortcut e bug critico).
 *
 * Comparacao timing-safe via MessageDigest.isEqual (PITFALLS C-03).
 */
@Service
public class HmacValidator {

    private static final Logger log = LoggerFactory.getLogger(HmacValidator.class);
    private static final String ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final int EXPECTED_HEX_LENGTH = 64; // SHA-256 = 32 bytes = 64 hex chars

    /**
     * @param rawBody         bytes brutos do body (NAO converter para String — PITFALLS C-04)
     * @param signatureHeader valor de "X-Hub-Signature-256" (formato esperado: "sha256=<hex>")
     * @param appSecret       App Secret do Meta (de WhatsAppProperties.appSecret)
     * @return true sse HMAC computado bate com signature decodificada
     */
    public boolean isValid(byte[] rawBody, String signatureHeader, String appSecret) {
        // Guards de input — todos retornam false (NAO short-circuit "skip se vazio")
        if (rawBody == null) return false;
        if (signatureHeader == null || signatureHeader.isBlank()) return false;
        if (appSecret == null || appSecret.isBlank()) return false;

        if (!signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            log.warn("HMAC header sem prefixo sha256=");
            return false;
        }

        String hex = signatureHeader.substring(SIGNATURE_PREFIX.length()).trim();
        if (hex.length() != EXPECTED_HEX_LENGTH) {
            log.warn("HMAC header com tamanho hex inesperado: {}", hex.length());
            return false;
        }

        byte[] received;
        try {
            received = hexDecode(hex);
        } catch (IllegalArgumentException ex) {
            log.warn("HMAC header com hex invalido");
            return false;
        }

        byte[] expected;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            expected = mac.doFinal(rawBody);
        } catch (Exception ex) {
            log.error("Erro computando HMAC", ex);
            return false;
        }

        // CONSTANT-TIME comparison — NAO usar Arrays.equals nem String.equals (PITFALLS C-03)
        return MessageDigest.isEqual(expected, received);
    }

    /** Hex decode estrito — caracteres invalidos lancam IllegalArgumentException. */
    private static byte[] hexDecode(String hex) {
        int len = hex.length();
        if ((len & 1) != 0) throw new IllegalArgumentException("hex length impar");
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("char hex invalido");
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
```

**Edge cases cobertos pelo teste unitario:**

| Cenario | Esperado |
|---------|----------|
| `rawBody=null` | `false` |
| `rawBody={}` (empty) | `false` (HMAC computado sobre empty bytes ainda assim — empty-body deve ser rejeitado se nao for HMAC valido de empty body) |
| `signatureHeader=null` ou `""` | `false` |
| `signatureHeader="abc..."` (sem `sha256=`) | `false` |
| `signatureHeader="sha256=zz..."` (hex invalido) | `false` |
| `signatureHeader` com `len != 64` | `false` |
| `appSecret=null` ou `""` | `false` |
| Body valido + HMAC valido + appSecret correto | `true` |
| Body 1-byte modificado | `false` |
| Body com texto portugues UTF-8 (`"Olá, gostaria de um orçamento"`) | `true` (porque NUNCA convertemos para String) |

### 3.2 HmacSignatureFilter (filter)

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/HmacSignatureFilter.java
package br.com.erpkit.whatsapp.web;

import br.com.erpkit.shared.dto.ErrorResponse;
import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.service.HmacValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtra POST /webhook/* validando HMAC-SHA256. So aplicavel a metodos POST —
 * GET (hub.challenge handshake) passa direto para o controller.
 *
 * Registrado em SecurityConfig com FilterRegistrationBean.addUrlPatterns("/webhook/*")
 * + setOrder(Ordered.HIGHEST_PRECEDENCE) — gate mais cedo possivel.
 */
public class HmacSignatureFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HmacSignatureFilter.class);
    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final HmacValidator validator;
    private final WhatsAppProperties properties;

    public HmacSignatureFilter(HmacValidator validator, WhatsAppProperties properties) {
        this.validator = validator;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // GET hub.challenge nao tem body assinado — passa direto
        if (!HttpMethod.POST.matches(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // Wrap PRIMEIRO — qualquer outro filter/MVC vai consumir o cached array (PITFALLS C-02)
        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
        byte[] body = cached.getCachedBody();
        String signature = request.getHeader(SIGNATURE_HEADER);

        if (!validator.isValid(body, signature, properties.getAppSecret())) {
            log.warn("HMAC invalido em POST {} — rejeitado com 401", request.getRequestURI());
            // NAO logar body, NAO logar signature header (potencial PII / dados de attacker)
            writeUnauthorized(response);
            return;
        }

        // HMAC valido — segue para o controller com o body cacheado
        chain.doFilter(cached, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse error = new ErrorResponse(401, "Nao autorizado", "Assinatura HMAC invalida");
        response.getWriter().write(MAPPER.writeValueAsString(error));
    }
}
```

### 3.3 Registracao

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/SecurityConfig.java
package br.com.erpkit.whatsapp.config;

import br.com.erpkit.shared.security.ApiKeyFilter;
import br.com.erpkit.whatsapp.service.HmacValidator;
import br.com.erpkit.whatsapp.web.HmacSignatureFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.Set;

@Configuration
public class SecurityConfig {

    /** HMAC filter — ordem 0 (HIGHEST_PRECEDENCE), so aplicado a /webhook/*. */
    @Bean
    public FilterRegistrationBean<HmacSignatureFilter> hmacSignatureFilter(
            HmacValidator validator, WhatsAppProperties properties) {
        FilterRegistrationBean<HmacSignatureFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new HmacSignatureFilter(validator, properties));
        reg.addUrlPatterns("/webhook/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }

    /** API Key filter (de lib-shared) — ordem 1, /webhook fica como path publico extra. */
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

## 4. WebhookController

> Dois endpoints. GET emite plain text. POST e stub vazio (HMAC ja foi validado pelo Filter).

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WebhookController.java
package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WhatsAppProperties properties;

    public WebhookController(WhatsAppProperties properties) {
        this.properties = properties;
    }

    /**
     * GET handshake do Meta. Retorna hub.challenge como text/plain (PITFALLS C-10).
     * Comparacao do verifyToken via MessageDigest.isEqual em UTF-8 bytes (consistencia com HMAC).
     */
    @GetMapping(value = "/whatsapp", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verificar(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {

        boolean modeOk = "subscribe".equals(mode);
        // Constant-time comparison — verifyToken so aparece em handshake mas custo zero
        byte[] expected = properties.getVerifyToken().getBytes(StandardCharsets.UTF_8);
        byte[] received = (verifyToken == null ? new byte[0] : verifyToken.getBytes(StandardCharsets.UTF_8));
        boolean tokenOk = MessageDigest.isEqual(expected, received);

        if (modeOk && tokenOk) {
            log.info("Webhook verificado pelo Meta — hub.challenge ecoado");
            return ResponseEntity.ok(challenge);
        }
        log.warn("Verificacao do webhook rejeitada — mode={}", mode);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * POST do Meta. HMAC ja validado pelo HmacSignatureFilter (ordem HIGHEST_PRECEDENCE).
     * Phase 1 = stub minimo. Phase 2 substitui por: parse → idempotency → return 200 → @Async.
     *
     * NUNCA logar body (PITFALLS — phone numbers, message content, PII).
     */
    @PostMapping("/whatsapp")
    public ResponseEntity<Void> receber() {
        log.debug("Webhook POST recebido com HMAC valido (stub Phase 1)");
        return ResponseEntity.ok().build();
    }
}
```

### Notas

- **`@RequestParam("hub.mode")`:** o nome do parametro contem ponto, exigindo a string literal. Spring MVC lida bem com isso desde que o nome seja explicito; sem o nome explicito (`@RequestParam String hubMode`) Spring tentaria casar com nome de variavel Java, falhando.
- **`produces = MediaType.TEXT_PLAIN_VALUE`:** essencial. Sem isso, `@RestController` serializa via Jackson e o challenge sai como `"abc"` (com aspas) — Meta rejeita (PITFALLS C-10).
- **POST nao precisa `@RequestBody`:** o Filter ja consumiu o body em `CachedBodyHttpServletRequest`. Em Phase 2 o controller passara a usar `@RequestBody String corpo` — o wrapper permite isso porque `getInputStream()` retorna stream nova baseada no array cacheado.
- **HMAC NAO e re-validado no controller** — confiar no Filter. Se algum dia alguem deletar o `FilterRegistrationBean`, o controller fica vulneravel; mitigado por integration test que verifica que POST sem header `X-Hub-Signature-256` retorna 401.

## 5. WhatsAppProperties

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/config/WhatsAppProperties.java
package br.com.erpkit.whatsapp.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuracao do modulo api-whatsapp. Falha rapido no boot via Bean Validation
 * se qualquer secret estiver ausente — mensagens em PT-BR identificando a env var.
 *
 * toString() mascara accessToken/appSecret/verifyToken (PITFALLS — vaza secret em log de erro do Spring).
 */
@ConfigurationProperties(prefix = "app.modulos.whatsapp")
@Validated
public class WhatsAppProperties {

    @NotBlank(message = "WHATSAPP_PHONE_NUMBER_ID nao definida")
    private String phoneNumberId;

    @NotBlank(message = "WHATSAPP_ACCESS_TOKEN nao definida")
    private String accessToken;

    @NotBlank(message = "WHATSAPP_APP_SECRET nao definida")
    private String appSecret;

    @NotBlank(message = "WHATSAPP_VERIFY_TOKEN nao definida")
    private String verifyToken;

    @NotBlank(message = "WHATSAPP_ERP_CALLBACK_URL nao definida")
    private String erpCallbackUrl;

    /** Timeout do callback ao ERP (Phase 3+). Default 5s. */
    private Duration callbackTimeout = Duration.ofSeconds(5);

    public String getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(String v) { this.phoneNumberId = v; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String v) { this.accessToken = v; }

    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String v) { this.appSecret = v; }

    public String getVerifyToken() { return verifyToken; }
    public void setVerifyToken(String v) { this.verifyToken = v; }

    public String getErpCallbackUrl() { return erpCallbackUrl; }
    public void setErpCallbackUrl(String v) { this.erpCallbackUrl = v; }

    public Duration getCallbackTimeout() { return callbackTimeout; }
    public void setCallbackTimeout(Duration v) { this.callbackTimeout = v; }

    @Override
    public String toString() {
        return "WhatsAppProperties{phoneNumberId=" + phoneNumberId
                + ", accessToken=[REDACTED]"
                + ", appSecret=[REDACTED]"
                + ", verifyToken=[REDACTED]"
                + ", erpCallbackUrl=" + erpCallbackUrl
                + ", callbackTimeout=" + callbackTimeout + "}";
    }
}
```

### Habilitacao

```java
// api-whatsapp/src/main/java/br/com/erpkit/whatsapp/WhatsAppApplication.java
package br.com.erpkit.whatsapp;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "br.com.erpkit")
@EnableConfigurationProperties(WhatsAppProperties.class)
public class WhatsAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhatsAppApplication.class, args);
    }
}
```

**Por que `@EnableConfigurationProperties` explicito** mesmo com Spring Boot 3.5.x? Com `@SpringBootApplication` + `@ConfigurationProperties` + componente scanning ativo, frequentemente o registro automatico funciona, MAS so quando a classe `@ConfigurationProperties` esta anotada com `@Component` (ou similar). Como NAO queremos `@Component` na Properties (Spring Boot 3 desencoraja), o caminho mais limpo e `@EnableConfigurationProperties(WhatsAppProperties.class)`. Garante registro deterministico — vai aparecer em todos os tutoriais oficiais 3.x. Veja "Open Question 4" abaixo.

## 6. lib-shared ApiKeyFilter — Modificacao

> Mudanca cirurgica: 1-arg constructor preservado, 2-arg adicionado.

### Diff conceitual

Arquivo: `lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java`

```java
package br.com.erpkit.shared.security;

import br.com.erpkit.shared.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final Set<String> DEFAULT_PUBLIC_PATHS =
            Set.of("/health", "/api/info", "/swagger-ui", "/v3/api-docs");
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final String apiKey;
    private final Set<String> publicPaths;

    /** Construtor original — preservado para backward-compat. */
    public ApiKeyFilter(String apiKey) {
        this(apiKey, Set.of());
    }

    /** Novo construtor — permite paths publicos adicionais por modulo. */
    public ApiKeyFilter(String apiKey, Set<String> additionalPublicPaths) {
        this.apiKey = apiKey;
        Set<String> merged = new HashSet<>(DEFAULT_PUBLIC_PATHS);
        if (additionalPublicPaths != null) {
            merged.addAll(additionalPublicPaths);
        }
        this.publicPaths = Set.copyOf(merged); // immutable
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank() && !apiKey.equals(key)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            ErrorResponse error = new ErrorResponse(401, "Nao autorizado", "API Key invalida ou ausente");
            response.getWriter().write(MAPPER.writeValueAsString(error));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return publicPaths.stream().anyMatch(path::startsWith);
    }
}
```

### Mudancas vs versao atual

1. `PUBLIC_PATHS` constant renomeado para `DEFAULT_PUBLIC_PATHS`.
2. Campo `publicPaths` (instance) substitui o uso direto da constant em `isPublicPath`.
3. Construtor existente `ApiKeyFilter(String apiKey)` agora delega para `this(apiKey, Set.of())`.
4. Novo construtor `ApiKeyFilter(String apiKey, Set<String> additionalPublicPaths)`.
5. `isPublicPath` usa `this.publicPaths` (em vez da constant).

### Tests a adicionar em `ApiKeyFilterTest`

Ja existem 9 tests cobrindo o construtor de 1 arg (verificados em linhas 16-148 do arquivo de teste). Adicionar:

- `@DisplayName("Construtor de 1 arg continua funcionando")` — regression: `new ApiKeyFilter("k")` rejeita path nao-padrao.
- `@DisplayName("Construtor de 2 args permite path adicional como publico")` — `new ApiKeyFilter("k", Set.of("/webhook"))`, request a `/webhook/whatsapp` retorna 200.
- `@DisplayName("Construtor de 2 args com null/empty additionalPublicPaths nao quebra")` — `new ApiKeyFilter("k", null)` e `new ApiKeyFilter("k", Set.of())` ambos comportam-se como 1-arg.
- `@DisplayName("additionalPublicPaths somam-se aos defaults")` — `/health` e `/webhook` ambos retornam 200 sem API Key.

## 7. api-whatsapp/pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>br.com.erpkit</groupId>
        <artifactId>erp-modulos</artifactId>
        <version>1.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>api-whatsapp</artifactId>
    <name>ERP Kit - API WhatsApp</name>
    <description>Modulo plugavel de integracao com WhatsApp Cloud API (reativo, custo zero)</description>

    <dependencies>
        <!-- Shared lib -->
        <dependency>
            <groupId>br.com.erpkit</groupId>
            <artifactId>lib-shared</artifactId>
        </dependency>

        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Database -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <!-- OpenAPI -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### Notas sobre dependencias

- **Sem Resilience4j em Phase 1** — entra em Phase 4 (`WhatsAppCloudClient`). Resilience4j esta gerenciado no `dependencyManagement` da raiz, basta declarar quando necessario.
- **Sem RestClient deps extras** — `spring-boot-starter-web` ja inclui o que precisamos para o `RestClient` da Phase 4.
- **`spring-boot-starter-data-jpa` ja em Phase 1** — necessario porque migrations Flyway sobem `mensagens_log`/`media_cache`/`clientes_zap` ja em Phase 1. Sem JPA, Hibernate `ddl-auto: validate` nao roda. Em Phase 2 entram as `@Entity`. Em Phase 1, JPA esta no classpath mas nao ha entities — Spring Boot detecta e nao reclama (`spring.jpa.hibernate.ddl-auto: validate` so valida o que existe).
- **`flyway-database-postgresql`** — separado de `flyway-core` desde Flyway 10+. Spring Boot 3.5.x BOM ja gerencia versoes corretas; basta declarar. H2 em test usa o adapter do flyway-core (ja transitivo), nao precisa de modulo adicional.
- **`com.h2database/h2` em test scope** — modo PostgreSQL configurado via JDBC URL; mesmo set de migrations roda.
- **Sem `springdoc` exhaustivo / customizado em Phase 1** — apenas o starter; `application.yml` define os paths default. `/swagger-ui.html` e `/v3/api-docs` ficam acessiveis.

## 8. application.yml

```yaml
# api-whatsapp/src/main/resources/application.yml
server:
  port: ${SERVER_PORT:9193}
  tomcat:
    accesslog:
      # Explicito por defesa em profundidade (PITFALLS C-11):
      # query string com hub.verify_token nao deve ser logada.
      enabled: false

spring:
  application:
    name: api-whatsapp

  datasource:
    # Mesmo servidor PostgreSQL local do ERP-MUDAS (porta 5433) — schema isolado
    url: ${WHATSAPP_DB_URL:jdbc:postgresql://localhost:5433/erp_mudas?currentSchema=whatsapp}
    username: ${WHATSAPP_DB_USERNAME:erp_mudas}
    password: ${WHATSAPP_DB_PASSWORD:erp_mudas_dev}

  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        # default_schema confirma para o Hibernate qual schema validar
        default_schema: whatsapp

  flyway:
    enabled: true
    baseline-on-migrate: true
    schemas: whatsapp
    default-schema: whatsapp
    # Cria o schema antes de aplicar V1 — em DEV/test funciona; em PROD o instalador
    # ja cria o schema, mas a flag e idempotente (CREATE SCHEMA IF NOT EXISTS na V1).
    create-schemas: true

# Modulo: configuracao especifica do WhatsApp — fail fast se falta qualquer um dos 5 secretos
app:
  modulos:
    whatsapp:
      phoneNumberId: ${WHATSAPP_PHONE_NUMBER_ID:}
      accessToken: ${WHATSAPP_ACCESS_TOKEN:}
      appSecret: ${WHATSAPP_APP_SECRET:}
      verifyToken: ${WHATSAPP_VERIFY_TOKEN:}
      erpCallbackUrl: ${WHATSAPP_ERP_CALLBACK_URL:}
      callbackTimeout: ${WHATSAPP_CALLBACK_TIMEOUT:5s}

# API key de endpoints internos do ERP (Phase 4) — webhook e publico, nao precisa
modulo:
  versao: 1.0.0
  api-key: ${API_KEY:}

# OpenAPI / Swagger
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html

# Logging — alinhado com PITFALLS C-09 / C-11 (sem token / sem query string em logs)
logging:
  level:
    br.com.erpkit.whatsapp: INFO
    # NAO subir org.springframework.web para DEBUG — vaza body, header, query string
    org.springframework.web: INFO

# Actuator (presente em Spring Boot 3.5.9 sem dep explicita se nao incluido starter-actuator;
# a chave so tem efeito quando actuator estiver no classpath. Defesa em profundidade.)
management:
  endpoint:
    env:
      keys-to-sanitize:
        - password
        - secret
        - key
        - token
        - accessToken
        - appSecret
        - verifyToken
```

### Decisoes de yml comentadas

- `currentSchema=whatsapp` no JDBC URL + `flyway.schemas` + `flyway.default-schema` — todos os 3 sao defesa em profundidade. Sem o JDBC param, Hibernate buscaria tabelas no schema `public` por default e `validate` falharia.
- **`flyway.create-schemas: true`** — em ambiente dev/test, Flyway cria o schema antes de V1 rodar. Em producao, a V1 tem `CREATE SCHEMA IF NOT EXISTS whatsapp;` como safety net.
- **`org.springframework.web: INFO`** explicito — Spring Boot defaults a INFO mas e comum alguem subir para DEBUG durante debug e esquecer de baixar.
- **`management.endpoint.env.keys-to-sanitize`** — o default de Spring Boot ja sanitiza chaves contendo `password`, `secret`, `key`, `token`. Adicionar os 3 nomes literais (`accessToken`/`appSecret`/`verifyToken`) e redundancia explicita. Ver "Open Question 1" — actuator pode nao estar no classpath em Phase 1 (so e adicionado em Phase 4 via WHATS-17 health check), o que torna a chave inerte mas nao prejudicial.

## 9. application-test.yml

```yaml
# api-whatsapp/src/test/resources/application-test.yml
spring:
  datasource:
    # H2 em modo PostgreSQL — INIT cria schema antes de Flyway aplicar V1
    url: jdbc:h2:mem:testdb_whatsapp;MODE=PostgreSQL;DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS whatsapp
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    database-platform: org.hibernate.dialect.H2Dialect
    properties:
      hibernate:
        default_schema: whatsapp

  flyway:
    enabled: true
    baseline-on-migrate: true
    schemas: whatsapp
    default-schema: whatsapp

# Dummy values pra Bean Validation passar — a maioria dos testes nao usa estes valores
# e os que usam (HmacValidatorTest com appSecret) injetam manualmente.
app:
  modulos:
    whatsapp:
      phoneNumberId: test-phone-id
      accessToken: test-access-token
      appSecret: test-app-secret
      verifyToken: test-verify-token
      erpCallbackUrl: http://localhost:0/test
      callbackTimeout: 5s

modulo:
  versao: 1.0.0-test
  api-key: test-key
```

### JDBC URL params explicados

| Param | Funcao |
|-------|--------|
| `MODE=PostgreSQL` | H2 emula sintaxe PostgreSQL (`BIGINT GENERATED ALWAYS AS IDENTITY`, `NOW()`, `CHECK` constraints) |
| `DATABASE_TO_UPPER=false` | H2 default eleva nomes para uppercase; PostgreSQL e case-sensitive lowercase. Sem isso, `SELECT * FROM clientes_zap` falha |
| `CASE_INSENSITIVE_IDENTIFIERS=true` | Permite `clientes_zap` casar com `CLIENTES_ZAP` quando misturado |
| `DB_CLOSE_DELAY=-1` | Mantem conexao viva entre tests dentro do mesmo SpringContext |
| `INIT=CREATE SCHEMA IF NOT EXISTS whatsapp` | Cria schema antes de Flyway tocar — mais robusto que confiar em `flyway.create-schemas` em H2 |

## 10. Flyway Migrations V1-V4

> SQL ANSI portavel, testado em PostgreSQL 15+ e H2 PostgreSQL-mode.

### V1 — clientes_zap

`api-whatsapp/src/main/resources/db/migration/V1__criar_tabela_clientes_zap.sql`

```sql
-- V1: schema + tabela de clientes WhatsApp
-- IDEMPOTENT: instalador Inno Setup ja cria o schema em prod; em test/dev a migration tambem cria.
CREATE SCHEMA IF NOT EXISTS whatsapp;

CREATE TABLE whatsapp.clientes_zap (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_cliente_erp      BIGINT,
    telefone            VARCHAR(20) NOT NULL UNIQUE,
    ultima_mensagem_em  TIMESTAMP,
    criado_em           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clientes_zap_telefone ON whatsapp.clientes_zap(telefone);
CREATE INDEX idx_clientes_zap_id_cliente_erp ON whatsapp.clientes_zap(id_cliente_erp);
```

### V2 — mensagens_log

`api-whatsapp/src/main/resources/db/migration/V2__criar_tabela_mensagens_log.sql`

```sql
-- V2: log de mensagens recebidas e enviadas (UNIQUE wamid garante idempotencia)
CREATE TABLE whatsapp.mensagens_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    wamid       VARCHAR(255) NOT NULL UNIQUE,
    telefone    VARCHAR(20) NOT NULL,
    direcao     VARCHAR(3) NOT NULL,
    tipo        VARCHAR(50),
    conteudo    TEXT,
    media_id    VARCHAR(255),
    criado_em   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_mensagens_log_direcao CHECK (direcao IN ('in', 'out'))
);

CREATE INDEX idx_mensagens_log_telefone ON whatsapp.mensagens_log(telefone);
CREATE INDEX idx_mensagens_log_criado_em ON whatsapp.mensagens_log(criado_em);
-- wamid ja tem UNIQUE index implicito da constraint
```

### V3 — media_cache

`api-whatsapp/src/main/resources/db/migration/V3__criar_tabela_media_cache.sql`

```sql
-- V3: cache de media_id por sha256 do arquivo (TTL 30 dias, gerenciado pela aplicacao)
CREATE TABLE whatsapp.media_cache (
    arquivo_hash    CHAR(64) PRIMARY KEY,
    media_id        VARCHAR(255) NOT NULL,
    criado_em       TIMESTAMP NOT NULL DEFAULT NOW(),
    expira_em       TIMESTAMP NOT NULL
);

CREATE INDEX idx_media_cache_expira_em ON whatsapp.media_cache(expira_em);
```

### V4 — estado_conversa (placeholder minimo)

`api-whatsapp/src/main/resources/db/migration/V4__criar_tabela_estado_conversa.sql`

```sql
-- V4: placeholder para estado de conversa (Phase 2+ pode estender com colunas adicionais).
-- Phase 1 cria so a estrutura minima — suficiente pra Hibernate validate nao reclamar
-- e suficiente pra futuras phases adicionarem colunas via ALTER TABLE em V5+.
CREATE TABLE whatsapp.estado_conversa (
    telefone            VARCHAR(20) PRIMARY KEY,
    ultima_atualizacao  TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### Notas de portabilidade

- `BIGINT GENERATED ALWAYS AS IDENTITY` funciona em PostgreSQL 10+ e H2 2.x (verificado em training data; PITFALLS nao contradiz). [ASSUMED — validar empiricamente no primeiro `mvnw verify`].
- `NOW()` funciona em ambos.
- `VARCHAR(20)`, `VARCHAR(255)`, `CHAR(64)`, `TEXT`, `TIMESTAMP` — todos standard SQL.
- `CHECK (direcao IN ('in', 'out'))` — funciona em ambos (PostgreSQL nativo, H2 com `MODE=PostgreSQL`).
- `CREATE SCHEMA IF NOT EXISTS` — PostgreSQL nativo; H2 (modo PG) suporta a partir da 1.4.x. Mesmo assim, o `INIT=CREATE SCHEMA IF NOT EXISTS whatsapp` no JDBC URL e safety net.
- **Indices nomeados** (`idx_clientes_zap_telefone` etc) em vez de Postgres-default — garante que Hibernate `ddl-auto: validate` nao reclama (validate so checa tabelas/colunas, mas se Hibernate gerar nome de indice diferente em algum teste futuro, nomes explicitos previnem confusao).

## 11. Root pom.xml — Mudanca

Em `pom.xml` (raiz, linha 21-27), acrescentar `<module>api-whatsapp</module>` apos `api-consultas`:

```xml
    <modules>
        <module>lib-shared</module>
        <module>lib-consultas-client</module>
        <module>api-email</module>
        <module>api-storage</module>
        <module>api-consultas</module>
        <module>api-whatsapp</module>
    </modules>
```

`lib-whatsapp-client` entra em Phase 5 — nao adicionar agora. `<dependencyManagement>` da raiz nao precisa mudar (pom de api-whatsapp herda tudo via parent + `lib-shared` ja esta declarado em `<dependencyManagement>` raiz linha 41-45).

## 12. Test Plan para Phase 1

> Apenas o suficiente para validar os 5 success criteria do ROADMAP. Phase 6 expande com cobertura completa.

### 12.1 HmacValidatorTest (unit, JUnit 5 + AssertJ)

`api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/HmacValidatorTest.java`

| Teste | Assertion |
|-------|-----------|
| `payload_valido_retorna_true` | Body+secret conhecido, signature pre-computada → `isValid` retorna true |
| `body_modificado_em_1_byte_retorna_false` | Mesmo signature, body com 1 byte alterado → `false` |
| `payload_em_portugues_utf8_retorna_true` | Body = `"{\"text\":\"Olá, gostaria de um orçamento\"}"`.getBytes(UTF_8), signature computada em python/curl reference → `true` (PITFALLS C-04) |
| `header_sem_prefixo_sha256_retorna_false` | `signatureHeader = "abc123..."` (sem `sha256=`) → `false` |
| `header_null_retorna_false` | `null` → `false` |
| `header_blank_retorna_false` | `""` → `false` |
| `body_vazio_retorna_false` | `rawBody = new byte[0]`, signature qualquer → `false` (NUNCA short-circuit empty body) |
| `body_null_retorna_false` | `rawBody = null` → `false` |
| `appSecret_blank_retorna_false` | `appSecret = ""` → `false` |
| `hex_invalido_no_header_retorna_false` | `"sha256=zz..."` → `false` (sem excecao) |
| `hex_com_tamanho_errado_retorna_false` | `"sha256=abc"` (3 chars) → `false` |

### 12.2 WebhookControllerTest (integration, MockMvc + @SpringBootTest)

`api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookControllerTest.java`

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookControllerTest {
    // ...
}
```

| Teste | Assertion |
|-------|-----------|
| `get_handshake_com_token_correto_retorna_challenge_plain_text` | GET com `hub.mode=subscribe&hub.verify_token=test-verify-token&hub.challenge=abc123` → status 200, content-type `text/plain`, body == `abc123` (sem aspas, sem JSON) |
| `get_handshake_com_token_errado_retorna_403` | GET com `hub.verify_token=wrong` → 403 |
| `get_handshake_com_mode_diferente_de_subscribe_retorna_403` | GET com `hub.mode=unsubscribe` → 403 |
| `post_com_hmac_valido_retorna_200` | POST com header valido + body conhecido → 200, body resposta vazio |
| `post_com_hmac_invalido_retorna_401` | POST com signature de body diferente → 401 + ErrorResponse JSON |
| `post_sem_header_signature_retorna_401` | POST sem `X-Hub-Signature-256` → 401 |
| `post_com_payload_portugues_e_hmac_valido_retorna_200` | POST body com `"Olá, orçamento"` UTF-8 + signature correta → 200 |

### 12.3 ApiKeyFilterTest (regression, lib-shared)

Adicionar a `lib-shared/src/test/java/br/com/erpkit/shared/security/ApiKeyFilterTest.java`:

| Teste | Assertion |
|-------|-----------|
| `construtor_1_arg_continua_funcionando` | `new ApiKeyFilter("k")` em `/api/anything` sem header → 401 (regression) |
| `construtor_2_args_permite_path_adicional` | `new ApiKeyFilter("k", Set.of("/webhook"))`, request a `/webhook/whatsapp` sem API Key → 200 |
| `construtor_2_args_com_set_vazio_comporta_se_como_1_arg` | `new ApiKeyFilter("k", Set.of())` em `/api/anything` → 401 |
| `construtor_2_args_com_null_nao_quebra` | `new ApiKeyFilter("k", null)` em `/health` → 200 |
| `additional_paths_somam_se_aos_defaults` | `new ApiKeyFilter("k", Set.of("/webhook"))` em `/health` E em `/webhook/x` → 200 nos dois |

### 12.4 WhatsAppPropertiesValidationTest (integration, @SpringBootTest)

`api-whatsapp/src/test/java/br/com/erpkit/whatsapp/config/WhatsAppPropertiesValidationTest.java`

| Teste | Assertion |
|-------|-----------|
| `boot_com_todas_as_5_propriedades_passa` | `@SpringBootTest(properties = {"app.modulos.whatsapp.phoneNumberId=...", ...})` → context loads |
| `boot_sem_phoneNumberId_falha` | properties sem phoneNumberId → `BindValidationException` (asserted via `assertThatThrownBy(SpringApplication::run)`); mensagem contem `"WHATSAPP_PHONE_NUMBER_ID nao definida"` |
| `boot_sem_accessToken_falha` | similar para accessToken |
| `boot_sem_appSecret_falha` | similar para appSecret |
| `boot_sem_verifyToken_falha` | similar para verifyToken |
| `boot_sem_erpCallbackUrl_falha` | similar para erpCallbackUrl |
| `toString_mascara_secrets` | `properties.toString()` nao contem `accessToken` real, contem `[REDACTED]` |

### 12.5 FlywayMigrationTest (integration, @SpringBootTest + JdbcTemplate)

`api-whatsapp/src/test/java/br/com/erpkit/whatsapp/db/FlywayMigrationTest.java`

```java
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void todas_as_4_tabelas_existem_em_schema_whatsapp() {
        List<String> esperadas = List.of("clientes_zap", "mensagens_log", "media_cache", "estado_conversa");
        for (String t : esperadas) {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'whatsapp' AND table_name = ?",
                Integer.class, t);
            assertThat(count).as("Tabela " + t + " deve existir em schema whatsapp").isEqualTo(1);
        }
    }

    @Test
    void mensagens_log_tem_indices_em_telefone_e_criado_em() {
        // info_schema.statistics em H2 modo PG
        Integer telefoneIdx = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.indexes WHERE table_schema='whatsapp' AND table_name='mensagens_log' AND column_name='telefone'",
            Integer.class);
        assertThat(telefoneIdx).isPositive();
    }

    @Test
    void wamid_tem_constraint_unique() {
        jdbc.update("INSERT INTO whatsapp.mensagens_log (wamid, telefone, direcao) VALUES ('w1', '5511999999999', 'in')");
        assertThatThrownBy(() ->
            jdbc.update("INSERT INTO whatsapp.mensagens_log (wamid, telefone, direcao) VALUES ('w1', '5511999999999', 'in')")
        ).isInstanceOf(DataAccessException.class);
    }
}
```

### Mapeamento ROADMAP → Tests

| ROADMAP success criteria | Coberto por |
|--------------------------|-------------|
| 1. GET hub.challenge ecoa plain text + 403 quando token errado | `WebhookControllerTest.get_handshake_*` |
| 2. POST 200 em <1s + body modificado retorna 401 + MessageDigest.isEqual | `WebhookControllerTest.post_*` + `HmacValidatorTest.body_modificado_em_1_byte_retorna_false` |
| 3. HMAC computado sobre bytes brutos via CachedBodyHttpServletRequest + payload portugues | `HmacValidatorTest.payload_em_portugues_utf8_retorna_true` + `WebhookControllerTest.post_com_payload_portugues_e_hmac_valido_retorna_200` |
| 4. Boot falha sem env var + secrets nao em logs | `WhatsAppPropertiesValidationTest.boot_sem_*` + `WhatsAppPropertiesValidationTest.toString_mascara_secrets` |
| 5. Flyway aplica V1-V4 no schema whatsapp; mvnw verify verde com H2 | `FlywayMigrationTest` + execucao do `mvnw verify -pl api-whatsapp` |

## 13. Risks & Open Questions

### Open Question 1: Actuator no classpath?

`management.endpoint.env.keys-to-sanitize` so tem efeito quando `spring-boot-starter-actuator` esta no classpath E `management.endpoints.web.exposure.include` lista `env`. Em Phase 1, NAO incluimos `spring-boot-starter-actuator` no `pom.xml` (so entra em Phase 4 ou 6 com WHATS-17 health check).

**Risco:** A property no `application.yml` fica inerte — sem efeito real. Nao causa erro de boot (Spring Boot ignora properties desconhecidas que estao em namespace estruturado), mas tambem nao protege nada.

**Recomendacao:** Manter a config no yml (defesa em profundidade quando actuator chegar) E confiar primariamente no `toString()` mascarado da `WhatsAppProperties` para Phase 1. Quando WHATS-17 trouxer actuator em Phase 4/6, a config ja estara no lugar.

[ASSUMED] — confirmar empiricamente que Spring Boot 3.5.9 nao reclama de property unknown nesta posicao. Caso reclame, mover para `application.yml` so quando actuator entrar.

### Open Question 2: H2 PostgreSQL-mode JDBC URL params exatos

Os params `MODE=PostgreSQL;DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true` sao a combinacao que documentacao H2 + multiplos blogs Spring Boot recomendam para emular Postgres em testes.

**Risco:** Algum DDL especifico de Postgres pode falhar em H2 (ex: `BIGINT GENERATED ALWAYS AS IDENTITY` funciona em ambos, mas fora isso e empirico).

[ASSUMED] — primeiro `mvnw verify` e o teste empirico real. Se falhar, fallback e Testcontainers (puxado para Phase 6 — over-kill para Phase 1, mas viavel).

### Open Question 3: SpringDoc OpenAPI version constant

Root `pom.xml` linha 31 declara `<springdoc.version>2.8.15</springdoc.version>` em `<properties>` e referencia em `<dependencyManagement>` linha 55-59. Modulos filhos (`api-email`, `api-consultas`) declaram apenas `<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>` sem version — herdado.

**Implicacao para api-whatsapp:** Espelhar — basta declarar artifactId, sem `<version>`. **Decidido: HIGH confidence baseado em codigo lido.**

### Open Question 4: `@EnableConfigurationProperties` necessario em Spring Boot 3.5.x?

`@SpringBootApplication` + `@ConfigurationProperties` SEM `@Component` e SEM `@EnableConfigurationProperties` — funciona?

Documentacao oficial Spring Boot 3.x (verificada em training): `@ConfigurationProperties` por si so NAO registra a classe como bean. As 3 formas de registrar:
1. Anotar a classe com `@Component` (ou `@ConfigurationPropertiesScan` na app principal)
2. `@EnableConfigurationProperties(WhatsAppProperties.class)` em uma `@Configuration` (ou na app)
3. `@ConfigurationPropertiesScan` no `@SpringBootApplication` — varre `@ConfigurationProperties` automaticamente

**Decisao:** Usar `@EnableConfigurationProperties(WhatsAppProperties.class)` no `WhatsAppApplication` (mais explicito, alinhado com tutoriais oficiais 3.x). **Confirmado em training data; HIGH confidence.**

### Risco 1: Modificacao em lib-shared quebra api-email/api-storage/api-consultas

Construtor de 1 arg preservado garante backward-compat de codigo. Como Maven roda o reator inteiro em `mvnw verify` no root, qualquer regressao em compilacao ou tests existentes desses 3 modulos aparece imediatamente.

**Mitigacao:** Plan mandatorio — antes de adicionar codigo novo em api-whatsapp, primeiro tarefa = modificar `ApiKeyFilter` + adicionar 4 tests novos + rodar `./mvnw verify -pl lib-shared,api-email,api-storage,api-consultas`. Se verde, prosseguir.

### Risco 2: `MODE=PostgreSQL` do H2 nao cobre todos os casos

PostgreSQL `BIGSERIAL` foi rejeitado em D-06 justamente porque H2 nao suporta — escolhemos `BIGINT GENERATED ALWAYS AS IDENTITY` que ambos suportam. Outros riscos: `RETURNING`, queries com `::cast`, `JSON` operators.

**Mitigacao:** Phase 1 NAO usa nenhuma dessas features (so DDL portavel). Em Phase 2+ se algum repository custom precisar de PG-specific, adicionar Testcontainers.

### Risco 3: Bean Validation em `@ConfigurationProperties` em Spring Boot 3.5.x exige `spring-boot-starter-validation` E Hibernate Validator

Confirmado: `spring-boot-starter-validation` ja esta no `pom.xml` proposto na secao 7. Hibernate Validator vem transitivo. **HIGH confidence — funcionou em api-email com pattern identico.**

### Risco 4: Encoding de arquivo `.sql` em Windows

Maven Resource filtering pode usar encoding default da plataforma (Windows-1252 em maquina dev) em vez de UTF-8. SQL com texto puro ASCII em V1-V4 nao tem risco (nenhum caractere acentuado).

**Mitigacao:** `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>` ja esta no `pom.xml` raiz (linha 35). HIGH confidence sem risco.

### Risco 5: Conflito de porta 9193 com outros servicos do dev

Default 9193 e configuravel via `${SERVER_PORT:9193}`. Em test, Spring Boot escolhe porta aleatoria (`@SpringBootTest(webEnvironment = RANDOM_PORT)`) — sem conflito.

## 14. Sources

### Codigo lido (HIGH confidence)

- `lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java` (linhas 1-54) — padrao OncePerRequestFilter + estrutura de PUBLIC_PATHS a estender
- `lib-shared/src/main/java/br/com/erpkit/shared/exception/GlobalExceptionHandler.java` (linhas 1-42) — handler de validacao + ModuloException
- `lib-shared/src/main/java/br/com/erpkit/shared/exception/ModuloException.java` (linhas 1-22) — RuntimeException + HttpStatus
- `lib-shared/src/main/java/br/com/erpkit/shared/dto/ErrorResponse.java` (linhas 1-64) — DTO de erro JSON com `(int status, String erro, String mensagem)`
- `lib-shared/src/test/java/br/com/erpkit/shared/security/ApiKeyFilterTest.java` (linhas 1-148) — 9 tests existentes que servem de regression base
- `lib-shared/pom.xml` (linhas 1-49) — estrutura minima da lib (servlet api `provided`, spring-web)
- `api-consultas/src/main/java/br/com/erpkit/consultas/config/SecurityConfig.java` (linhas 1-20) — padrao FilterRegistrationBean + setOrder + addUrlPatterns
- `api-consultas/src/main/java/br/com/erpkit/consultas/ConsultasApplication.java` (linhas 1-14) — `@SpringBootApplication(scanBasePackages = "br.com.erpkit")`
- `api-consultas/src/main/resources/application.yml` (linhas 1-26) — estrutura de port + springdoc + logging
- `api-consultas/pom.xml` (linhas 1-72) — herdar lib-shared + starter-web + starter-validation + springdoc
- `api-email/src/main/resources/application.yml` (linhas 1-43) — datasource + jpa + flyway pattern
- `api-email/src/main/resources/db/migration/V1__criar_tabela_emails.sql` (linhas 1-23) — convencao de naming Flyway + indices
- `api-email/src/test/resources/application-test.yml` (linhas 1-26) — H2 in-memory pattern (sera adaptado para PostgreSQL-mode)
- `api-email/pom.xml` (linhas 1-89) — base direta do `api-whatsapp/pom.xml` proposto
- `api-storage/src/main/resources/db/migration/V1__criar_tabela_arquivos.sql` (linhas 1-19) — convencao adicional Flyway (BIGSERIAL no projeto atual; estamos mudando para `BIGINT GENERATED ALWAYS AS IDENTITY` em api-whatsapp para portabilidade)
- `pom.xml` (raiz, linhas 1-75) — `<modules>` block + `<dependencyManagement>` para springdoc/resilience4j
- `.planning/config.json` — confirma `nyquist_validation: true`, `security_enforcement: true`, `commit_docs: true`

### Documentos canonicos lidos (HIGH confidence)

- `.planning/phases/01-fundacao-hmac-webhook/01-CONTEXT.md` — todas as decisoes D-01 a D-06 + canonical refs
- `.planning/research/PITFALLS.md` §C-02 (HMAC body capture), §C-03 (timing attack), §C-04 (UTF-8 charset), §C-09 (Bearer token logs), §C-10 (challenge plain text), §C-11 (verifyToken in query logs)
- `.planning/research/ARCHITECTURE.md` §"Component Responsibilities" (linhas 80-107) — note discrepancia com CONTEXT.md (Filter vs service no controller; CONTEXT.md vence), §"Recommended Project Structure" (linhas 124-194)
- `.planning/REQUIREMENTS.md` — WEB-01..04, PER-01, CFG-01..04 locked para Phase 1
- `.planning/ROADMAP.md` Phase 1 (linhas 18-29) — 5 success criteria literais

### Spring Framework / Meta docs (CITED)

- Spring Framework — `OncePerRequestFilter` Javadoc (referenciado em PITFALLS C-02; padrao confirmado em `ApiKeyFilter.java`)
- Spring Framework — `ContentCachingRequestWrapper` Javadoc: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/util/ContentCachingRequestWrapper.html (referenciado em PITFALLS C-02 — confirma comportamento lazy)
- Spring Framework — `StreamUtils.copyToByteArray` (HIGH — usado em codebase em multiplos projetos Spring; metodo estavel desde Spring 4.x)
- Spring Boot 3.5.x — `@ConfigurationProperties` + `@Validated` + `@EnableConfigurationProperties` (CITED — docs.spring.io/spring-boot/reference/features/external-config.html)
- Spring Boot 3.5.x — `management.endpoint.env.keys-to-sanitize` (CITED — docs.spring.io/spring-boot/reference/actuator/endpoints.html — so tem efeito com starter-actuator presente)
- Meta — Webhooks setup, hub.challenge contract: https://developers.facebook.com/docs/whatsapp/cloud-api/guides/set-up-webhooks/ (referenciado em PITFALLS C-10)
- Meta — X-Hub-Signature-256 spec: https://developers.facebook.com/docs/graph-api/webhooks/getting-started#payload (referenciado em PITFALLS C-02/C-03/C-04)

### H2 / Flyway (CITED via PITFALLS + training)

- H2 Database — `MODE=PostgreSQL` compatibility: http://www.h2database.com/html/features.html#compatibility
- H2 — `INIT` JDBC URL parameter: http://www.h2database.com/html/features.html#init_script
- Flyway — `flyway.schemas` + `flyway.create-schemas` defaults (HIGH — comportamento confirmado em codebase: api-email tem `baseline-on-migrate: true` e e suficiente porque schema `public` e implicito)

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `BIGINT GENERATED ALWAYS AS IDENTITY` funciona identicamente em PostgreSQL 15 e H2 2.x modo PostgreSQL | 10 (migrations) | Migration falha no primeiro `mvnw verify`. Fallback: trocar para `BIGINT AUTO_INCREMENT` (H2) com profile alternativo OU adotar Testcontainers (overkill Phase 1) |
| A2 | `management.endpoint.env.keys-to-sanitize` no application.yml e ignorado silenciosamente quando actuator nao esta no classpath (sem erro de boot) | 8 + 13 Open Q1 | Boot falha em vez de ignorar. Mitigacao: comentar a chave ate Phase 4 trazer actuator |
| A3 | H2 `MODE=PostgreSQL` cobre `CHECK (direcao IN ('in', 'out'))` | 10 (V2) | CHECK constraint silenciosamente ignorada — testes que insiram `direcao='xx'` passam quando deveriam falhar. Phase 6 com Testcontainers cobre |
| A4 | `@RequestParam("hub.mode")` (com ponto) funciona em Spring MVC 3.5.x sem `@QueryParam` extra | 4 | GET handshake retorna 400. Mitigacao: substituir por `request.getParameter("hub.mode")` lendo `HttpServletRequest` direto |
| A5 | Flyway aplica `CREATE SCHEMA IF NOT EXISTS whatsapp` da V1 ANTES do Hibernate validate rodar | 8 + 10 V1 | Hibernate validate falha porque schema nao existe ainda. Mitigacao: usar `flyway.create-schemas: true` + `flyway.schemas: whatsapp` (ja na config — Flyway cria o schema antes da V1, redundancia) |
| A6 | `spring-boot-starter-data-jpa` em Phase 1 sem nenhuma `@Entity` nao causa erro de boot | 7 (pom) | Boot falha por falta de `EntityManagerFactory` configuravel. Mitigacao: Spring Boot detecta zero entities e avisa em log mas boota; se houver erro fatal, criar entity placeholder vazio em Phase 1 OU adiar JPA para Phase 2 |
| A7 | Modificacao em `lib-shared/ApiKeyFilter` nao quebra os 9 tests existentes (porque construtor de 1 arg delega para `(apiKey, Set.of())`) | 6 | Tests existentes falham. Mitigacao: rodar `./mvnw verify -pl lib-shared` antes de seguir; se falhar, ajustar implementacao ate verde |

**Recomendacao:** A1 e A2 sao os mais prováveis de causar problema empirico. Plan deve incluir checkpoint apos secao 4 das migrations (rodar `./mvnw verify -pl api-whatsapp -Dtest=FlywayMigrationTest` isolado) — se A1 falhar, ajustar agora antes de gastar tempo no resto.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Spring Boot Test 3.5.9 + AssertJ + MockMvc |
| Config file | `api-whatsapp/src/test/resources/application-test.yml` (a criar) |
| Quick run command | `./mvnw -pl api-whatsapp test -Dtest=HmacValidatorTest -q` |
| Full suite command | `./mvnw verify -pl api-whatsapp` |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| WEB-01 | GET hub.challenge ecoa plain text + 403 quando token errado | integration (MockMvc) | `./mvnw -pl api-whatsapp test -Dtest=WebhookControllerTest#get_handshake_*` | Wave 0 |
| WEB-02 | POST valida HMAC timing-safe + 401 sem persistir se modificado | integration (MockMvc) + unit | `./mvnw -pl api-whatsapp test -Dtest=WebhookControllerTest#post_*,HmacValidatorTest` | Wave 0 |
| WEB-03 | HMAC usa CachedBodyHttpServletRequest (nao ContentCachingRequestWrapper) | integration | `./mvnw -pl api-whatsapp test -Dtest=WebhookControllerTest#post_com_payload_portugues_e_hmac_valido_retorna_200` | Wave 0 |
| WEB-04 | POST responde 200 em <1s | integration (MockMvc — verificar timing nao essencial em Phase 1; Phase 6 com WireMock) | `./mvnw -pl api-whatsapp test -Dtest=WebhookControllerTest#post_com_hmac_valido_retorna_200` | Wave 0 |
| PER-01 | Schema `whatsapp` aplicado por Flyway | integration (JdbcTemplate) | `./mvnw -pl api-whatsapp test -Dtest=FlywayMigrationTest` | Wave 0 |
| CFG-01 | 5 secrets `@NotBlank` falham boot se ausentes | integration (@SpringBootTest) | `./mvnw -pl api-whatsapp test -Dtest=WhatsAppPropertiesValidationTest` | Wave 0 |
| CFG-02 | Placeholders `${WHATSAPP_*}` no application.yml | manual (code review) | `grep "WHATSAPP_" api-whatsapp/src/main/resources/application.yml` | Wave 0 |
| CFG-03 | Logs nao imprimem accessToken/appSecret | unit (toString) | `./mvnw -pl api-whatsapp test -Dtest=WhatsAppPropertiesValidationTest#toString_mascara_secrets` | Wave 0 |
| CFG-04 | Porta 9193 default | manual (code review) | `grep "9193" api-whatsapp/src/main/resources/application.yml` | Wave 0 |

### Sampling Rate

- **Per task commit:** `./mvnw -pl api-whatsapp test -Dtest=<TestClassDoCommit>` (rapido, < 10s para tests unitarios)
- **Per wave merge:** `./mvnw verify -pl lib-shared,api-whatsapp` (cobre regression de lib-shared + suite completa de api-whatsapp; ~30-60s)
- **Phase gate:** `./mvnw verify` no root (reator completo, todos os 5 modulos + api-whatsapp; ~1-2min)

### Wave 0 Gaps

- [ ] `api-whatsapp/src/test/resources/application-test.yml` — config H2 PostgreSQL-mode (secao 9)
- [ ] `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/HmacValidatorTest.java` — 11 cenarios da secao 12.1
- [ ] `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/controller/WebhookControllerTest.java` — 7 cenarios da secao 12.2
- [ ] `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/config/WhatsAppPropertiesValidationTest.java` — 7 cenarios da secao 12.4
- [ ] `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/db/FlywayMigrationTest.java` — 3 cenarios da secao 12.5
- [ ] `lib-shared/src/test/java/br/com/erpkit/shared/security/ApiKeyFilterTest.java` — adicionar 5 tests novos (secao 12.3) ao arquivo existente
- [ ] Framework install: nenhum — JUnit 5 ja vem em `spring-boot-starter-test`

## Security Domain

### Applicable ASVS Categories (Level 1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | HMAC-SHA256 do header `X-Hub-Signature-256` (Meta como counterparty); API Key header `X-API-Key` (ERP como counterparty) |
| V3 Session Management | no | Stateless — sem sessoes nem cookies |
| V4 Access Control | yes | `lib-shared/ApiKeyFilter` controla `/api/*`; `HmacSignatureFilter` controla `/webhook/*` — defesa em camadas |
| V5 Input Validation | yes | Jakarta Bean Validation em `WhatsAppProperties` (Phase 1); em DTOs de Phase 2+ |
| V6 Cryptography | yes | `Mac.getInstance("HmacSHA256")` + `MessageDigest.isEqual` constant-time — JCE built-in, NUNCA hand-roll HMAC ou comparacao |
| V7 Error Handling & Logging | yes | `toString()` mascarado em Properties; `keys-to-sanitize` em actuator; sem log de body |
| V9 Communication | yes | TLS na entrada (Cloudflare Tunnel termina TLS) — fora do escopo da app, mas validado pelo runbook |
| V14 Configuration | yes | Secrets via env var, Bean Validation fail-fast, `accesslog.enabled: false` |

### Known Threat Patterns para Spring Boot Webhook

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Webhook forging (attacker POST injetando comandos) | Spoofing | HMAC validation com `MessageDigest.isEqual` (PITFALLS C-02 + C-03) |
| HMAC timing oracle | Information Disclosure | `MessageDigest.isEqual` constant-time comparison (PITFALLS C-03) |
| Body charset corruption (UTF-8 vs ISO-8859-1) | Tampering / DoS | Trabalhar sempre com `byte[]` (PITFALLS C-04) |
| Body lazily cacheado vazio quando filter le | Spoofing | Custom `HttpServletRequestWrapper` com eager read (PITFALLS C-02) |
| Secret em log (accessToken via Bearer) | Information Disclosure | `toString()` mascarado + `keys-to-sanitize` (PITFALLS C-09); cobertura completa em Phase 4 |
| verifyToken em log (query string) | Information Disclosure | `accesslog.enabled: false` + sem `CommonsRequestLoggingFilter` (PITFALLS C-11) |
| hub.challenge wrapped em JSON (handshake falha) | DoS | `produces = MediaType.TEXT_PLAIN_VALUE` (PITFALLS C-10) |
| Property scan vazia (Spring nao registra Properties) | Configuration error | `@EnableConfigurationProperties(WhatsAppProperties.class)` explicito |

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — todas as deps ja existem em api-email/api-consultas com versoes pinadas no pom raiz
- Architecture (Filter + service split): HIGH — codigo de referencia lido (ApiKeyFilter, SecurityConfig de api-consultas)
- Pitfalls: HIGH — PITFALLS.md ja contem analise validada das armadilhas C-02/03/04/09/10/11
- Migrations portaveis: MEDIUM — `BIGINT GENERATED ALWAYS AS IDENTITY` validado em training mas nao testado empiricamente neste codebase ainda (assumption A1)
- Bean Validation fail-fast: HIGH — pattern usado em api-email (DTOs com `@NotBlank`); Spring Boot 3.5.x docs confirmam

**Research date:** 2026-05-05
**Valid until:** 2026-06-05 (estavel — Spring Boot 3.5.x e WhatsApp Cloud API v21.0 sao versoes pinadas)
