---
phase: 01-fundacao-hmac-webhook
plan: 05
type: execute
wave: 5
depends_on:
  - "01-04"
files_modified:
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/HmacValidator.java  # NEW
  - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/CachedBodyHttpServletRequest.java  # NEW
  - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/HmacValidatorTest.java  # NEW
autonomous: true
requirements:
  - WEB-02  # parcial — HMAC-SHA256 timing-safe via MessageDigest.isEqual (a integracao via filter vem em PLAN-06)
  - WEB-03  # parcial — CachedBodyHttpServletRequest com eager body read (substitui ContentCachingRequestWrapper)
tags:
  - api-whatsapp
  - hmac
  - security
  - servlet-wrapper

must_haves:
  truths:
    - "HmacValidator existe como @Service pure (sem dep de Servlet API), unit-testavel"
    - "Metodo isValid(byte[] rawBody, String signatureHeader, String appSecret) retorna boolean"
    - "Comparacao via MessageDigest.isEqual (constant-time) — NUNCA String.equals nem Arrays.equals"
    - "NUNCA lanca excecao por input malformado — sempre retorna boolean"
    - "rawBody=null, body vazio, header malformado, hex invalido, len errado, appSecret blank → todos false"
    - "Body com texto portugues UTF-8 (Olá, gostaria de um orçamento) → true (HMAC computado sobre bytes brutos, nunca via String intermediario — PITFALLS C-04)"
    - "CachedBodyHttpServletRequest extends HttpServletRequestWrapper, le bytes EAGERLY no construtor via StreamUtils.copyToByteArray"
    - "getInputStream() retorna ServletInputStream baseado em ByteArrayInputStream (cache permite multiplas leituras)"
    - "getReader() retorna BufferedReader UTF-8 (NUNCA via getCharacterEncoding — PITFALLS C-04)"
    - "getCachedBody() expoe os bytes brutos para HMAC computation"
    - "HmacValidatorTest passa com 11 cenarios (positive, negative, edge cases, portugues UTF-8)"
    - "mvnw verify -pl api-whatsapp BUILD SUCCESS"
  artifacts:
    - path: "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/HmacValidator.java"
      provides: "Pure function de validacao HMAC-SHA256 timing-safe"
      contains: "MessageDigest.isEqual(expected, received)"
    - path: "api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/CachedBodyHttpServletRequest.java"
      provides: "HttpServletRequestWrapper com eager body read (substitui ContentCachingRequestWrapper)"
      contains: "StreamUtils.copyToByteArray"
    - path: "api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/HmacValidatorTest.java"
      provides: "11 testes unitarios (positivo, negativos, edge cases, UTF-8 portugues)"
      contains: "payload_em_portugues_utf8_retorna_true"
  key_links:
    - from: "HmacValidator.isValid"
      to: "MessageDigest.isEqual"
      via: "constant-time comparison (PITFALLS C-03)"
      pattern: "MessageDigest\\.isEqual"
    - from: "CachedBodyHttpServletRequest construtor"
      to: "byte[] cachedBody"
      via: "StreamUtils.copyToByteArray (eager read no construtor)"
      pattern: "StreamUtils\\.copyToByteArray"
    - from: "HmacValidator.isValid"
      to: "byte[] rawBody (nao String)"
      via: "evita conversao charset (PITFALLS C-04)"
      pattern: "byte\\[\\] rawBody"
---

<objective>
Criar `HmacValidator` (`@Service`, pure function, sem dep de Servlet API) e `CachedBodyHttpServletRequest` (HttpServletRequestWrapper com eager body read). Adicionar `HmacValidatorTest` cobrindo 11 cenarios incluindo body portugues UTF-8 (Olá, gostaria de um orçamento) garantindo que a HMAC e computada sobre bytes brutos. Estas sao as 2 unidades-fundacao de seguranca da Phase 1 — PLAN-06 vai fazer o glue (Filter + SecurityConfig + Controller) que as utiliza.

Purpose: Decisao D-01 do CONTEXT.md split em 2 camadas: HmacValidator (testavel isolado) + Wrapper (substitui ContentCachingRequestWrapper que e lazy e quebra HMAC — PITFALLS C-02). PITFALLS C-03 (timing attack) e C-04 (UTF-8 charset) endereçados por design.

Output:
- `service/HmacValidator.java` — pure function `boolean isValid(byte[], String, String)`
- `web/CachedBodyHttpServletRequest.java` — wrapper com eager read, getInputStream/getReader/getCachedBody
- `service/HmacValidatorTest.java` — 11 cenarios verdes
- `mvnw verify -pl api-whatsapp` BUILD SUCCESS
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
@.planning/phases/01-fundacao-hmac-webhook/01-04-SUMMARY.md
@.planning/research/PITFALLS.md

<interfaces>
<!-- HmacValidator.java a copiar EXATAMENTE de RESEARCH §3.1 (linhas 204-289) -->

```java
package br.com.erpkit.whatsapp.service;

@Service
public class HmacValidator {
    private static final String ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final int EXPECTED_HEX_LENGTH = 64;

    public boolean isValid(byte[] rawBody, String signatureHeader, String appSecret) {
        // null/blank guards retornam false (NUNCA throw)
        // strip "sha256=" prefix
        // hex decode (try/catch para hex invalido — retorna false)
        // Mac.getInstance("HmacSHA256") + SecretKeySpec(appSecret.getBytes(UTF_8))
        // mac.doFinal(rawBody) → expected
        // return MessageDigest.isEqual(expected, received)
    }

    private static byte[] hexDecode(String hex) {
        // throws IllegalArgumentException em caracter invalido
    }
}
```

<!-- CachedBodyHttpServletRequest.java a copiar EXATAMENTE de RESEARCH §2 (linhas 117-186) -->

```java
package br.com.erpkit.whatsapp.web;

public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream()); // EAGER
    }

    public byte[] getCachedBody() { return cachedBody; }

    @Override
    public ServletInputStream getInputStream() {
        // ByteArrayInputStream + ServletInputStream anonima (NAO DelegatingServletInputStream — so existe em spring-test)
    }

    @Override
    public BufferedReader getReader() {
        // UTF-8 hardcoded (NUNCA via getCharacterEncoding — PITFALLS C-04)
    }
}
```

<!-- Edge cases que HmacValidatorTest cobre (RESEARCH §12.1 + §3.1 tabela) -->

| Cenario | Esperado |
|---------|----------|
| Body valido + secret correto + signature correta | true |
| Body 1-byte modificado + signature original | false |
| Body em portugues UTF-8 + signature correta | true (PITFALLS C-04) |
| signatureHeader sem "sha256=" prefix | false |
| signatureHeader=null | false |
| signatureHeader="" | false |
| rawBody=null | false |
| rawBody=byte[0] (empty) | false |
| appSecret=null ou "" | false |
| hex invalido ("zz...") | false (sem throw) |
| hex tamanho errado ("abc" len 3) | false |
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Criar HmacValidator.java</name>
  <files>api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/HmacValidator.java</files>
  <behavior>
    - `@Service` Spring component
    - Sem dependencia de Servlet API (HttpServletRequest etc) — facil de unit-testar
    - `boolean isValid(byte[] rawBody, String signatureHeader, String appSecret)` retorna true/false
    - NUNCA throws (input malformado retorna false)
    - Constant-time comparison via `MessageDigest.isEqual` (PITFALLS C-03)
    - HMAC-SHA256 via `javax.crypto.Mac.getInstance("HmacSHA256")`
    - SecretKeySpec usa `appSecret.getBytes(StandardCharsets.UTF_8)` (consistencia com PITFALLS C-04)
    - Hex decode estrito (caracteres invalidos lancam IllegalArgumentException internamente, capturada por try/catch)
    - Strip prefixo "sha256=" do header antes de decode
    - Validar tamanho do hex == 64 (SHA-256 = 32 bytes = 64 hex chars)
  </behavior>
  <action>
    Copiar integralmente o codigo Java da secao 3.1 do `01-RESEARCH.md` (linhas 204-289) para o novo arquivo.

    **Verificar imports:**
    - `javax.crypto.Mac` (NAO `jakarta.crypto` — JCE permanece em `javax`)
    - `javax.crypto.spec.SecretKeySpec`
    - `java.nio.charset.StandardCharsets`
    - `java.security.MessageDigest`
    - `org.slf4j.Logger` + `org.slf4j.LoggerFactory`
    - `org.springframework.stereotype.Service`

    **Verificar logica:**
    - Logger nivel `WARN` para falhas (header sem prefixo, hex invalido, len errado)
    - Logger nivel `ERROR` para Mac.getInstance falha (improvavel — JCE built-in)
    - hexDecode privado static — recebe hex string, retorna byte[] de tamanho len/2
    - Verificar caractere invalido com `Character.digit(ch, 16) < 0` antes de bit-shift
  </action>
  <verify>
    <automated>./mvnw -pl api-whatsapp compile -q && grep -c "MessageDigest.isEqual" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/HmacValidator.java</automated>
  </verify>
  <done>
    - Compila sem erros
    - grep retorna 1 (MessageDigest.isEqual usado exatamente uma vez)
    - `grep "Arrays.equals\|String.equals" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/HmacValidator.java` retorna 0 (anti-pattern)
    - `grep "@Service" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/HmacValidator.java` retorna 1
  </done>
</task>

<task type="auto">
  <name>Task 2: Criar CachedBodyHttpServletRequest.java</name>
  <files>api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/CachedBodyHttpServletRequest.java</files>
  <action>
    Copiar integralmente o codigo Java da secao 2 do `01-RESEARCH.md` (linhas 117-186) para o novo arquivo.

    **Verificar imports:**
    - `jakarta.servlet.ReadListener` (NAO `javax.servlet` — Spring Boot 3.x usa Jakarta)
    - `jakarta.servlet.ServletInputStream`
    - `jakarta.servlet.http.HttpServletRequest`
    - `jakarta.servlet.http.HttpServletRequestWrapper`
    - `org.springframework.util.StreamUtils`
    - `java.io.{BufferedReader, ByteArrayInputStream, IOException, InputStreamReader}`
    - `java.nio.charset.StandardCharsets`

    **Pontos criticos do design (PITFALLS C-02):**
    - **Eager read no construtor** — `StreamUtils.copyToByteArray(request.getInputStream())`. NAO lazy. NAO ContentCachingRequestWrapper.
    - `getInputStream()` retorna anonymous `ServletInputStream` baseado em `ByteArrayInputStream(cachedBody)`. RESEARCH §2 Gotchas confirma que `DelegatingServletInputStream` so existe em spring-test, NAO usar em production.
    - `getReader()` UTF-8 hardcoded (PITFALLS C-04) — NAO usa `request.getCharacterEncoding()` que pode retornar ISO-8859-1.
    - `getCachedBody()` expoe os bytes; documentar que callers nao devem modificar (RESEARCH §2 Gotchas).
    - `setReadListener(ReadListener)` lança UnsupportedOperationException (raro de chamar; webhook do Meta nao usa async I/O).
  </action>
  <verify>
    <automated>./mvnw -pl api-whatsapp compile -q && grep -c "StreamUtils.copyToByteArray" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/CachedBodyHttpServletRequest.java</automated>
  </verify>
  <done>
    - Compila sem erros
    - `StreamUtils.copyToByteArray` usado no construtor
    - `getReader()` usa `StandardCharsets.UTF_8` (verificar via grep)
    - Imports todos jakarta.* (NAO javax.servlet.*)
    - `grep "ContentCachingRequestWrapper" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/CachedBodyHttpServletRequest.java` retorna 0 (anti-pattern)
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: Criar HmacValidatorTest com 11 cenarios</name>
  <files>api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/HmacValidatorTest.java</files>
  <behavior>
    11 testes unitarios per RESEARCH §12.1 (linhas 1054-1066) e §3.1 tabela (linhas 294-304):

    1. `payload_valido_retorna_true` — body+secret conhecido, signature pre-computada → true
    2. `body_modificado_em_1_byte_retorna_false` — mesma signature, 1 byte alterado no body → false
    3. `payload_em_portugues_utf8_retorna_true` — body=`{"text":"Olá, gostaria de um orçamento"}` UTF-8, signature pre-computada → true (PITFALLS C-04)
    4. `header_sem_prefixo_sha256_retorna_false` — `signatureHeader = "abc..."` (sem `sha256=`) → false
    5. `header_null_retorna_false` — null → false
    6. `header_blank_retorna_false` — "" → false
    7. `body_vazio_retorna_false` — `byte[0]` + signature qualquer → false (NUNCA short-circuit empty body)
    8. `body_null_retorna_false` — null → false
    9. `appSecret_blank_retorna_false` — appSecret="" → false
    10. `hex_invalido_no_header_retorna_false` — `"sha256=zz..."` → false (sem throw)
    11. `hex_com_tamanho_errado_retorna_false` — `"sha256=abc"` (3 chars) → false
  </behavior>
  <action>
    Criar `HmacValidatorTest.java`. Estrutura sugerida:

    ```java
    package br.com.erpkit.whatsapp.service;

    import org.junit.jupiter.api.BeforeEach;
    import org.junit.jupiter.api.DisplayName;
    import org.junit.jupiter.api.Test;

    import javax.crypto.Mac;
    import javax.crypto.spec.SecretKeySpec;
    import java.nio.charset.StandardCharsets;
    import java.util.HexFormat;

    import static org.assertj.core.api.Assertions.assertThat;

    class HmacValidatorTest {

        private HmacValidator validator;
        private static final String SECRET = "test-app-secret-xyz";

        @BeforeEach
        void setUp() {
            validator = new HmacValidator();
        }

        /** Helper: computa "sha256=<hex>" para body+secret usando HMAC-SHA256 (mesmo algoritmo que HmacValidator). */
        private String computeHeader(byte[] body, String secret) throws Exception {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmac = mac.doFinal(body);
            return "sha256=" + HexFormat.of().formatHex(hmac);
        }

        @Test
        @DisplayName("Body valido + signature correta + secret correto → true")
        void payload_valido_retorna_true() throws Exception {
            byte[] body = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);
            String header = computeHeader(body, SECRET);
            assertThat(validator.isValid(body, header, SECRET)).isTrue();
        }

        @Test
        @DisplayName("Body modificado em 1 byte → false (signature original nao bate)")
        void body_modificado_em_1_byte_retorna_false() throws Exception {
            byte[] originalBody = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);
            String header = computeHeader(originalBody, SECRET);
            byte[] modified = originalBody.clone();
            modified[0] ^= 0x01; // flip 1 bit
            assertThat(validator.isValid(modified, header, SECRET)).isFalse();
        }

        @Test
        @DisplayName("Body com texto portugues UTF-8 (Olá, gostaria de um orçamento) → true (PITFALLS C-04)")
        void payload_em_portugues_utf8_retorna_true() throws Exception {
            byte[] body = "{\"text\":\"Olá, gostaria de um orçamento\"}".getBytes(StandardCharsets.UTF_8);
            String header = computeHeader(body, SECRET);
            assertThat(validator.isValid(body, header, SECRET)).isTrue();
        }

        @Test
        void header_sem_prefixo_sha256_retorna_false() {
            byte[] body = "x".getBytes();
            assertThat(validator.isValid(body, "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", SECRET)).isFalse();
        }

        @Test
        void header_null_retorna_false() {
            assertThat(validator.isValid("x".getBytes(), null, SECRET)).isFalse();
        }

        @Test
        void header_blank_retorna_false() {
            assertThat(validator.isValid("x".getBytes(), "", SECRET)).isFalse();
            assertThat(validator.isValid("x".getBytes(), "   ", SECRET)).isFalse();
        }

        @Test
        void body_vazio_retorna_false() throws Exception {
            byte[] empty = new byte[0];
            String headerForEmpty = computeHeader(empty, SECRET); // signature legitima de empty
            // Mesmo com signature correta para empty, o esperado e false: empty body nao deve ser aceito como webhook
            // Per RESEARCH §3.1 tabela: "rawBody={} (empty) → false (HMAC computado sobre empty bytes ainda assim
            // — empty-body deve ser rejeitado se nao for HMAC valido de empty body)"
            // *Caveat:* o codigo de RESEARCH §3.1 NAO rejeita empty body explicitamente — ele computa HMAC normalmente.
            // Para empty + signature correta de empty, retorna TRUE. Reescrever este teste para usar signature INCORRETA:
            String wrongHeader = computeHeader("x".getBytes(), SECRET); // signature de outro body
            assertThat(validator.isValid(empty, wrongHeader, SECRET)).isFalse();
        }

        @Test
        void body_null_retorna_false() {
            String anyValidLengthHex = "sha256=" + "a".repeat(64);
            assertThat(validator.isValid(null, anyValidLengthHex, SECRET)).isFalse();
        }

        @Test
        void appSecret_blank_retorna_false() {
            assertThat(validator.isValid("x".getBytes(), "sha256=" + "a".repeat(64), "")).isFalse();
            assertThat(validator.isValid("x".getBytes(), "sha256=" + "a".repeat(64), null)).isFalse();
        }

        @Test
        void hex_invalido_no_header_retorna_false() {
            String malformed = "sha256=" + "z".repeat(64);
            assertThat(validator.isValid("x".getBytes(), malformed, SECRET)).isFalse();
        }

        @Test
        void hex_com_tamanho_errado_retorna_false() {
            assertThat(validator.isValid("x".getBytes(), "sha256=abc", SECRET)).isFalse();
            assertThat(validator.isValid("x".getBytes(), "sha256=" + "a".repeat(63), SECRET)).isFalse(); // 63 (impar)
            assertThat(validator.isValid("x".getBytes(), "sha256=" + "a".repeat(66), SECRET)).isFalse(); // 66 (len errado)
        }
    }
    ```

    **Notas:**
    - `HexFormat.of()` (Java 17+) substitui `Hex.encodeHexString` da Apache Commons — disponivel em Java 21 do projeto.
    - O test `body_vazio_retorna_false` na tabela do RESEARCH e ambiguo — clarifiquei na implementacao acima usando uma signature de OUTRO body (assim a comparacao falha mesmo para empty body).
    - Tests sao 100% unit (sem @SpringBootTest) — rapidos (<1s).
  </action>
  <verify>
    <automated>./mvnw -pl api-whatsapp test -Dtest=HmacValidatorTest -q</automated>
  </verify>
  <done>
    - Surefire: "Tests run: 11, Failures: 0"
    - Test "payload_em_portugues_utf8_retorna_true" passa (cobertura PITFALLS C-04)
    - Test "body_modificado_em_1_byte_retorna_false" passa (cobertura tampering)
    - Tempo total < 5s (sao unit tests puros)
  </done>
</task>

<task type="auto">
  <name>Task 4: Verificar build do reator</name>
  <files>(nenhum modificado)</files>
  <action>
    Rodar `./mvnw verify -pl api-whatsapp -q`. Esperado: BUILD SUCCESS com Tests run >= 21 (10 anteriores + 11 do HmacValidator).
  </action>
  <verify>
    <automated>./mvnw verify -pl api-whatsapp -q</automated>
  </verify>
  <done>
    - BUILD SUCCESS
    - Tests run >= 21
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Bytes do body → HMAC computation | byte[] cru entra no Mac, sem conversao charset (PITFALLS C-04) |
| signatureHeader externo → hexDecode | input nao-confiavel (Meta ou attacker); sempre returna boolean nunca lanca |
| MessageDigest.isEqual gate | comparacao constant-time impede timing oracle (PITFALLS C-03) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05-01 | Spoofing | HmacValidator pulando validacao em empty body | mitigate | Tests `body_vazio_retorna_false` + `body_null_retorna_false` enforce explicitly; PITFALLS C-02 anti-pattern empty-array shortcut documentado e rejeitado |
| T-05-02 | Information Disclosure | Timing attack via String.equals na comparacao HMAC | mitigate | `MessageDigest.isEqual` constant-time (PITFALLS C-03); test `body_modificado_em_1_byte_retorna_false` valida rejeicao mas NAO mede timing — Phase 6 pode adicionar microbenchmark se quiser  |
| T-05-03 | Tampering | Body convertido para String → HMAC errada para chars UTF-8 | mitigate | byte[] passado direto via getCachedBody (CachedBodyHttpServletRequest); test `payload_em_portugues_utf8_retorna_true` enforced (PITFALLS C-04) |
| T-05-04 | Spoofing | ContentCachingRequestWrapper lazy → body vazio em filter | mitigate | Custom CachedBodyHttpServletRequest com EAGER read no construtor (RESEARCH §2 + PITFALLS C-02); grep gate em Task 2 confirma que ContentCachingRequestWrapper nao e referenciado |
| T-05-05 | Tampering | hex decode com input malicioso lança exception → DoS via crash | mitigate | hexDecode privado captura caracteres invalidos retornando IllegalArgumentException, que e capturada externamente e retorna false. Tests `hex_invalido_no_header_retorna_false` + `hex_com_tamanho_errado_retorna_false` enforce. |
| T-05-06 | Information Disclosure | Logger imprimindo signatureHeader/body em log de WARN | accept | RESEARCH §3.1 logs em WARN apenas mensagens descritivas ("HMAC header sem prefixo sha256="), sem incluir o valor do header nem do body. Aceitavel — logs nao vazam secret. |
</threat_model>

<verification>
## Phase Checks

1. `grep "MessageDigest.isEqual" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/HmacValidator.java` retorna 1
2. `grep "Arrays.equals\|String.equals" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/HmacValidator.java` retorna 0 (anti-pattern PITFALLS C-03)
3. `grep "StreamUtils.copyToByteArray" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/CachedBodyHttpServletRequest.java` retorna 1
4. `grep "ContentCachingRequestWrapper" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/CachedBodyHttpServletRequest.java` retorna 0 (anti-pattern PITFALLS C-02)
5. `grep "StandardCharsets.UTF_8" api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/CachedBodyHttpServletRequest.java` retorna >= 1 (PITFALLS C-04)
6. `./mvnw -pl api-whatsapp test -Dtest=HmacValidatorTest` — Tests run: 11, Failures: 0
7. `./mvnw verify -pl api-whatsapp` BUILD SUCCESS, Tests run >= 21
</verification>

<success_criteria>
- HmacValidator existe como pure function sem Servlet API dep
- HMAC-SHA256 + MessageDigest.isEqual + UTF-8 secret bytes
- Nunca throws — sempre retorna boolean
- 11 tests verdes (positivo, body modificado, portugues UTF-8, todos os edge cases)
- CachedBodyHttpServletRequest lê body eagerly via StreamUtils.copyToByteArray
- getReader() UTF-8 hardcoded (nao via getCharacterEncoding)
- getInputStream() retorna ServletInputStream anonima baseada em ByteArrayInputStream
- mvnw verify -pl api-whatsapp BUILD SUCCESS
- 1 commit atomico
</success_criteria>

<commit>
Mensagem (Conventional Commits PT-BR):

```
feat(api-whatsapp): adicionar HmacValidator + CachedBodyHttpServletRequest

HmacValidator: pure function @Service que valida HMAC-SHA256 do header
X-Hub-Signature-256 contra bytes brutos do body. Comparacao constant-time
via MessageDigest.isEqual (PITFALLS C-03). NUNCA throws — input malformado
retorna false. UTF-8 hardcoded em SecretKeySpec (PITFALLS C-04).

CachedBodyHttpServletRequest: HttpServletRequestWrapper com EAGER read no
construtor (StreamUtils.copyToByteArray). Substitui ContentCachingRequestWrapper
que e lazy e leva a HMAC sobre body vazio (PITFALLS C-02). getReader() UTF-8
hardcoded para nunca quebrar com texto portugues.

11 tests unitarios cobrem: body valido, body 1-byte modificado, payload em
portugues (Olá, gostaria de um orçamento), todos os edge cases de header
malformado / body null / hex invalido.

Refs: D-01 (CONTEXT.md), WEB-02 + WEB-03 (REQUIREMENTS.md), 01-RESEARCH.md §2 §3 §12.1
PITFALLS C-02 / C-03 / C-04
```

Comando:
```bash
node $HOME/.claude/get-shit-done/bin/gsd-tools.cjs commit "feat(api-whatsapp): adicionar HmacValidator + CachedBodyHttpServletRequest" --files \
  api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/HmacValidator.java \
  api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/CachedBodyHttpServletRequest.java \
  api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/HmacValidatorTest.java
```
</commit>

<risks>
- **Apache Commons Hex vs Java 17+ HexFormat** — usar `HexFormat.of()` (Java 21 do projeto) para evitar dep extra. Confirmar import `java.util.HexFormat`.
- **`MessageDigest.isEqual` requer arrays do mesmo tamanho** — se algo retornar bytes de tamanho diferente (ex: hex decode produz 31 bytes em vez de 32), a comparacao retorna false sem throw. RESEARCH `EXPECTED_HEX_LENGTH = 64` ja garante 32 bytes apos decode. Edge case: se hex decode tiver bug, retorna `received.length != expected.length` mas `isEqual` retorna false safely. Defesa em profundidade ja embutida.
- **Test "body_vazio_retorna_false" semantica:** Tabela do RESEARCH lista o caso ambiguo. Implementacao escolheu testar empty body com signature INCORRETA (qualquer signature que nao seja de empty body). Para empty body com signature CORRETA de empty body, o validator retorna TRUE — isso e tecnicamente correto (HMAC sobre 0 bytes e valido), mas operacionalmente um POST com body vazio nao deve ser webhook real do Meta. Phase 2 pode adicionar uma checagem semantica `if (body.length < MIN_PAYLOAD)` antes de chamar isValid; Phase 1 mantem o validator estritamente matematico.
- **Imports javax.crypto.* vs jakarta.crypto.*** — JCE permanece em `javax.crypto.*` (NAO foi migrado para Jakarta). Imports devem usar `javax.crypto.Mac` etc. Diferente de servlets (`jakarta.servlet.*`).
</risks>

<output>
Apos completar, criar `.planning/phases/01-fundacao-hmac-webhook/01-05-SUMMARY.md` com:
- HmacValidator + CachedBodyHttpServletRequest criados
- 11 tests verdes (incluindo cobertura UTF-8 portugues e timing-safe)
- Confirmacao via grep de antipatterns evitados (no String.equals, no ContentCachingRequestWrapper)
- Reactor build BUILD SUCCESS, Tests run >= 21
- Commit hash
</output>
