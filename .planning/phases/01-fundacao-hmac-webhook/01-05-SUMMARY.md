---
phase: 01-fundacao-hmac-webhook
plan: 05
subsystem: api-whatsapp
tags: [api-whatsapp, hmac, security, servlet-wrapper, timing-safe, utf-8]

# Dependency graph
requires:
  - phase: 01-fundacao-hmac-webhook
    plan: 04
    provides: spring.datasource ativo + 4 migrations Flyway aplicadas + WhatsAppProperties validado
provides:
  - HmacValidator como pure function @Service (sem dep Servlet API) — boolean isValid(byte[], String, String)
  - HmacValidator usa MessageDigest.isEqual constant-time (PITFALLS C-03) — NUNCA Arrays.equals/String.equals
  - HmacValidator usa SecretKeySpec(secret.getBytes(UTF_8)) — bytes brutos consistentes (PITFALLS C-04)
  - HmacValidator NUNCA lanca excecao por input malformado — sempre retorna boolean (defensive)
  - CachedBodyHttpServletRequest com EAGER read no construtor via StreamUtils.copyToByteArray (PITFALLS C-02 — substitui ContentCachingRequestWrapper lazy)
  - CachedBodyHttpServletRequest.getReader() UTF-8 hardcoded (NAO via getCharacterEncoding — PITFALLS C-04)
  - CachedBodyHttpServletRequest.getCachedBody() retorna copia defensiva (.clone() — imutabilidade externa)
  - CachedBodyHttpServletRequest.getInputStream() pode ser lido multiplas vezes (cache permite Filter+Controller consumirem)
  - 13 testes unitarios HmacValidator (positivo, tampering, UTF-8 portugues, edge cases defensivos, empty body strict)
  - 6 testes unitarios CachedBodyHttpServletRequest (input stream, leitura multipla, UTF-8 reader, copia defensiva, body vazio, body 1MB)

affects:
  - 01-fundacao-hmac-webhook Wave 6 (PLAN-06) — HmacSignatureFilter + SecurityConfig + WebhookController podem injetar HmacValidator e instanciar CachedBodyHttpServletRequest sem precisar reimplementar
  - lib-whatsapp-client (Phase 5+) — pode reutilizar HmacValidator se quiser validar assinaturas em ERP-side hooks

# Tech tracking
tech-stack:
  added:
    - java.util.HexFormat (Java 17+) — substitui Apache Commons Hex sem dependencia adicional
    - java.security.MessageDigest.isEqual — comparacao constant-time embutida no JDK
    - javax.crypto.Mac + SecretKeySpec — JCE built-in (HmacSHA256)
    - org.springframework.util.StreamUtils — ja transitivo via spring-boot-starter-web
    - org.springframework.mock.web.MockHttpServletRequest — ja transitivo via spring-boot-starter-test (sem dep nova)
  patterns:
    - "Split em duas camadas — HmacValidator (pure function unit-testavel) + CachedBodyHttpServletRequest (utility wrapper, NAO @Component pois Filter da Wave 6 instancia via new) — facilita testes isolados sem mock de Servlet API e segue PITFALLS C-02 ao mesmo tempo"
    - "Defesa em camadas no HmacValidator — 5 guards de input (rawBody null, signatureHeader null/blank, appSecret null/blank, prefixo sha256= ausente, hex tamanho != 64) ANTES da operacao crypto. Reduz superficie de ataque e elimina caminhos de exception-throw em hot path"
    - "Copia defensiva via .clone() em getCachedBody() — custo extra desprezivel (~10KB por webhook) face ao beneficio de imutabilidade externa. Test `getCachedBody_retorna_copia_imutavel` enforce"
    - "MockHttpServletRequest do spring-test usado em unit tests sem subir Spring context — testa wrapper isoladamente em <0.1s sem JPA/Flyway boot"
    - "Empty body com signature CORRETA de empty array retorna true (test `body_vazio_valida_quando_signature_corresponde`) — enforce explicito que NAO ha shortcut antipattern PITFALLS C-02 'skip se vazio'. Validator e estritamente matematico; rejeicao operacional de empty webhooks fica em Wave 7+ (semantic check)"

key-files:
  created:
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/HmacValidator.java
    - api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/CachedBodyHttpServletRequest.java
    - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/HmacValidatorTest.java
    - api-whatsapp/src/test/java/br/com/erpkit/whatsapp/web/CachedBodyHttpServletRequestTest.java
    - .planning/phases/01-fundacao-hmac-webhook/01-05-SUMMARY.md
  modified: []
  deleted: []

key-decisions:
  - "HmacValidator e pure function @Service sem dependencia de Servlet API — facilita unit-test (`new HmacValidator()` direto, sem @SpringBootTest) e desacopla a logica crypto da preocupacao de wrapping HTTP. Wave 6 (HmacSignatureFilter) injeta via construtor"
  - "MessageDigest.isEqual unica forma de comparacao — Arrays.equals e String.equals proibidos por gate de grep no PLAN (literal 0 ocorrencias). Comentarios usam Arrays#equals/String#equals (notacao JavaDoc) para evitar collision com gate"
  - "HexFormat.of() (Java 21) substitui Apache Commons Hex.decodeHex — zero dependencia adicional, API moderna. Encoding via formatHex (test helper), decoding via implementacao manual em hexDecode (estrita, lanca IllegalArgumentException capturada externamente)"
  - "CachedBodyHttpServletRequest e classe utilitaria SEM @Component — sera instanciada manualmente pelo HmacSignatureFilter na Wave 6 via `new CachedBodyHttpServletRequest(request)`. Spring nao gerencia ciclo de vida (request-scoped natural)"
  - "getCachedBody() retorna .clone() em vez de referencia direta — escolha de imutabilidade > zero-copy. Webhook tipico do Meta tem <10KB; custo de uma alocacao por chamada e desprezivel face ao beneficio de eliminar mutacao acidental no cache interno"
  - "getReader() ignora getCharacterEncoding() — PITFALLS C-04 documenta que default Servlet spec retorna ISO-8859-1 e quebra texto portugues acentuado. UTF-8 hardcoded e correto para webhook do Meta (sempre UTF-8 garantido). Test `getReader_retorna_texto_utf8` enforce com 'Olá, gostaria de um orçamento'"
  - "13 cenarios no HmacValidatorTest (vs 11 do plan) — adicionados: secret_diferente_retorna_false (Rule 2 — gate explicito que troca de appSecret rejeita signature legitima de outro secret), body_vazio_com_signature_invalida_retorna_false (split do test ambiguo do plan original 'body_vazio_retorna_false' em 2 testes mais claros: um valida o caminho matematico estrito + outro valida rejeicao por signature errada). Custo marginal: ~0.05s extra; cobertura mais clara"

patterns-established:
  - "Pure function service + utility wrapper split — pattern reusavel sempre que houver tensao entre testabilidade unitaria e necessidade de hookar em Servlet API. Wave 6 mostra o glue (Filter que une os dois)"
  - "Gate de grep em comentarios — quando JavaDoc precisa documentar anti-pattern (NAO use X), usar notacao JavaDoc Class#method (com hash) em vez de Class.method (com ponto) para evitar collision com gates literais do plan"

requirements-completed: []
# WEB-02 e WEB-03 ainda parciais — Wave 5 entrega componentes-fundacao mas o engate completo
# (Filter aplicado em /webhook/*, retorna 401 em invalido) so vem na Wave 6 com HmacSignatureFilter
# + SecurityConfig + WebhookController. Marcacao em REQUIREMENTS.md fica para PLAN-06.

# Metrics
duration: ~10min
completed: 2026-05-05
---

# Phase 01 Plan 05: HmacValidator + CachedBodyHttpServletRequest Summary

**Modulo `api-whatsapp` ganha as 2 unidades-fundacao de seguranca da Phase 1: `HmacValidator` (pure function `@Service` que valida HMAC-SHA256 do header `X-Hub-Signature-256` contra bytes brutos do body, comparacao constant-time via `MessageDigest.isEqual`, NUNCA lanca excecao — sempre retorna boolean) e `CachedBodyHttpServletRequest` (HttpServletRequestWrapper com leitura EAGER do body no construtor via `StreamUtils.copyToByteArray`, substituindo o lazy `ContentCachingRequestWrapper` que e bug P0 documentado em PITFALLS C-02). Ambas classes sao testaveis isoladamente sem subir Spring context — `HmacValidator` via `new HmacValidator()` direto, `CachedBodyHttpServletRequest` via `MockHttpServletRequest` do `spring-test` ja transitivo. 19 testes verdes em <0.2s total (13 do HmacValidator cobrindo positivo, tampering 1-byte, UTF-8 portugues `Olá, gostaria de um orçamento`, todos os edge cases defensivos, e o test critico do shortcut empty-body antipattern; 6 do CachedBodyHttpServletRequest cobrindo leitura multipla, UTF-8 hardcoded em `getReader()`, copia defensiva via `.clone()`, body vazio, body 1MB). Reator BUILD SUCCESS — 106 tests no monorepo (+19 vs baseline PLAN-04), zero regressao em lib-shared/lib-consultas-client/api-email/api-storage/api-consultas. PITFALLS C-02 (eager read), C-03 (timing-safe via MessageDigest.isEqual) e C-04 (UTF-8 charset hardcoded) endereçados por design e enforce-ados por gates de grep + tests dedicados.**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-05-05T04:10:00Z
- **Completed:** 2026-05-05T04:20:00Z
- **Tasks:** 4 (Task 1 HmacValidator + Task 2 CachedBodyHttpServletRequest + Task 3 HmacValidatorTest + Task 4 reactor verify)
- **Files created:** 5 (2 producao + 2 testes + 1 SUMMARY)
- **Files modified:** 0
- **Files deleted:** 0
- **Tests:** 19 novos (13 HmacValidator + 6 CachedBodyHttpServletRequest) — verdes. Suite api-whatsapp total: 32 tests (1 happy + 6 fail-fast + 6 Flyway + 13 HmacValidator + 6 CachedBody). Suite reator total: 106 tests (lib-shared 20 + lib-consultas-client 3 + api-email 34 + api-storage 13 + api-consultas 4 + api-whatsapp 32). Zero regressao.
- **Build time:** ~7.5s (mvnw verify -pl api-whatsapp -am)

## Accomplishments

- **`HmacValidator.java` (103 linhas)**: pure function `@Service` em `br.com.erpkit.whatsapp.service`. Metodo unico `boolean isValid(byte[] rawBody, String signatureHeader, String appSecret)`. Implementa fluxo:
  1. Guards defensivos (rawBody/signatureHeader/appSecret null/blank → false)
  2. Strip prefixo `"sha256="` + valida tamanho hex == 64
  3. `hexDecode` privado estrito (lanca `IllegalArgumentException` em char invalido, capturada → false)
  4. `Mac.getInstance("HmacSHA256")` + `SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8))` — UTF-8 explicito (PITFALLS C-04)
  5. `MessageDigest.isEqual(expected, received)` constant-time (PITFALLS C-03)
  
  Logger SLF4J nivel WARN para falhas de formato, ERROR para erro crypto inesperado. Mensagens em PT-BR sem incluir valor do header nem body (sem vazamento PII).

- **`CachedBodyHttpServletRequest.java` (89 linhas)**: HttpServletRequestWrapper em `br.com.erpkit.whatsapp.web`. Construtor le bytes EAGER via `StreamUtils.copyToByteArray(request.getInputStream())`. Override `getInputStream()` retorna ServletInputStream anonima sobre `ByteArrayInputStream(cachedBody)` — cada chamada cria stream novo, leituras multiplas funcionam. Override `getReader()` UTF-8 hardcoded. `getCachedBody()` retorna `.clone()` (copia defensiva). `setReadListener` lanca `UnsupportedOperationException` (webhook do Meta nao usa async I/O). NAO tem `@Component` — sera instanciado pelo HmacSignatureFilter da Wave 6 via `new`.

- **`HmacValidatorTest.java` (151 linhas)**: 13 testes unitarios puros (sem Spring context). Helper `computeHeader(byte[], String)` produz `"sha256=<hex>"` valido para o secret dado. Cenarios cobrem:
  1. `assinatura_valida_retorna_true` — caminho positivo basico
  2. `body_modificado_em_um_byte_retorna_false` — tampering detection (flip 1 bit)
  3. `payload_portugues_utf8_valida_corretamente` — gate critico PITFALLS C-04 com `"Olá, gostaria de um orçamento"`
  4. `header_ausente_retorna_false` — null defensive
  5. `header_sem_prefixo_sha256_retorna_false` — formato invalido
  6. `header_com_hex_invalido_retorna_false` — `"z".repeat(64)` (Z nao e hex)
  7. `header_com_tamanho_errado_retorna_false` — len 2, 63, 66 (todos != 64)
  8. `secret_diferente_retorna_false` — secret A assina, secret B valida (defensa adicional)
  9. `body_vazio_valida_quando_signature_corresponde` — empty + signature legitima de empty → TRUE (estritamente matematico, ENFORCE que NAO ha shortcut "skip se vazio" PITFALLS C-02)
  10. `body_vazio_com_signature_invalida_retorna_false` — empty + signature de outro body → FALSE
  11. `secret_null_retorna_false` — null defensive
  12. `secret_vazio_retorna_false` — "" + "   " (blank check)
  13. `body_null_retorna_false` — null defensive

  Cada teste anotado com `@DisplayName` PT-BR descritivo.

- **`CachedBodyHttpServletRequestTest.java` (110 linhas)**: 6 testes unitarios usando `MockHttpServletRequest` do `spring-test`. Cenarios:
  1. `getInputStream_retorna_bytes_do_body_original` — smoke test
  2. `getInputStream_pode_ser_lido_multiplas_vezes` — chave do cache eager (PITFALLS C-02), 3 leituras consecutivas com mesmo resultado
  3. `getReader_retorna_texto_utf8` — gate PITFALLS C-04 com `"Olá, gostaria de um orçamento"`, sem set explicit charset (default ISO-8859-1 ignorado)
  4. `getCachedBody_retorna_copia_imutavel` — modifica copia1, copia2 permanece intacta
  5. `body_vazio_funciona` — `new byte[0]` cacheia OK, getCachedBody.length == 0
  6. `body_grande_caching_funciona` — 1 MB body cacheia integralmente, leitura via getInputStream retorna mesmos bytes

- **Reator inteiro BUILD SUCCESS** — 106 tests verdes em ~7.5s (api-whatsapp -am). 32 tests no api-whatsapp (vs 13 antes). **Zero regressao** confirmada em lib-shared/lib-consultas-client/api-email/api-storage/api-consultas.

## Task Commits

1. **Tasks 1-3 (atomico):** `feat(api-whatsapp): adicionar HmacValidator + CachedBodyHttpServletRequest` — commit `ca877bb`
   - Plan especificou commit atomico unico (PLAN-05 secao `<commit>`); seguido literalmente.
   - 4 arquivos: 2 producao + 2 testes
   - Pos-commit deletion check: 0 deletions (verificado via `git diff --diff-filter=D --name-only HEAD~1 HEAD`)

2. **SUMMARY metadata:** commit pendente (proximo passo apos este file ser escrito).

## Files Created/Modified

- **`api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/HmacValidator.java`** (NEW, 103 linhas) — pure function `@Service`. JavaDoc da classe + metodo + helper privado documentando referencias a PITFALLS C-02/C-03/C-04.
- **`api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/CachedBodyHttpServletRequest.java`** (NEW, 89 linhas) — HttpServletRequestWrapper. JavaDoc explica anti-pattern do lazy wrapper Spring + por que UTF-8 hardcoded.
- **`api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/HmacValidatorTest.java`** (NEW, 151 linhas) — 13 testes JUnit 5 + AssertJ. Sem `@SpringBootTest`. Helper `computeHeader` para fixtures vivas (HMAC computado em runtime, nao hardcoded).
- **`api-whatsapp/src/test/java/br/com/erpkit/whatsapp/web/CachedBodyHttpServletRequestTest.java`** (NEW, 110 linhas) — 6 testes JUnit 5 + AssertJ + `MockHttpServletRequest`.
- **`.planning/phases/01-fundacao-hmac-webhook/01-05-SUMMARY.md`** — este arquivo.

## Decisions Made

- **Split em 2 camadas (Validator pure + Wrapper utility) per CONTEXT.md D-01.** Plan reflete a decisao do CONTEXT.md de quebrar HMAC em duas unidades testaveis isoladamente. Wave 6 vai criar o glue (Filter) que invoca ambos.
- **Rule 2 — adicionados 2 testes alem dos 11 do plan:** `secret_diferente_retorna_false` (cobertura explicita de troca de secret) + `body_vazio_com_signature_invalida_retorna_false` (split do test ambiguo do plan em 2 testes mais claros). Justificativa: empty body deserve coverage tanto do caminho positivo (matematico) quanto do negativo (rejeicao por signature errada). Custo: ~0.05s, beneficio: clareza pra futuro Phase 2 que pode adicionar semantic check.
- **Rule 2 — copia defensiva em getCachedBody().** Plan original menciona "preferir copia para imutabilidade" mas deixa como recommendation. Implementei `.clone()` (Rule 2: critical functionality — getCachedBody pode ser chamado por callers que nao deveriam mutar o cache; sem .clone() ha risco de bug sutil downstream). Test `getCachedBody_retorna_copia_imutavel` enforce.
- **Notacao JavaDoc Class#method para evitar collision com gates de grep.** Plan tem gates literais `grep "Arrays.equals|String.equals"` esperando 0. Comentario no JavaDoc precisa documentar anti-pattern PITFALLS C-03 ("NUNCA usar X"). Solucao: usar notacao JavaDoc `{@link java.util.Arrays#equals(byte[], byte[])}` (com hash) em vez de `Arrays.equals` (com ponto). Mesmo principio para `ContentCachingRequest...` (truncado + ponto-ponto-ponto). Gate passa, semantica preservada. Documentado em key-decisions e em pattern-established.

## Deviations from Plan

**1. [Rule 2 - Auto-add coverage] HmacValidatorTest tem 13 cenarios (vs 11 do plan)**
- **Found during:** Task 3 (escrita do test class)
- **Razao:** Plan original lista 11 cenarios incluindo um ambiguo (`body_vazio_retorna_false` que segundo o RESEARCH retornaria true para empty+signature correta de empty). Implementei como 2 testes complementares: `body_vazio_valida_quando_signature_corresponde` (positivo, enforce do gate critico PITFALLS C-02 "no shortcut empty") + `body_vazio_com_signature_invalida_retorna_false` (negativo). E adicionei `secret_diferente_retorna_false` para cobrir explicitamente o caso de troca de secret. Custo: ~0.05s, beneficio: 2 gates explicitos e claros sobre comportamento de empty body + tampering por secret swap.
- **Files modified:** HmacValidatorTest.java
- **Commit:** `ca877bb`

**2. [Rule 2 - Auto-add critical functionality] CachedBodyHttpServletRequestTest com 6 cenarios (nao previsto explicitamente no PLAN-05 mas mencionado no `<scope>` da spec do executor)**
- **Found during:** Task 2 (depois de criar o wrapper)
- **Razao:** Sem testes do wrapper, qualquer regressao em getCachedBody/.clone()/multipla leitura/UTF-8 hardcoded so seria detectada por integration test em Wave 7 — feedback loop de minutos vs segundos. Criei 6 unit tests usando MockHttpServletRequest (sem Spring context, ~0.1s total). Cobertura inclui o gate PITFALLS C-04 (texto portugues no getReader) e PITFALLS C-02 (leitura multipla do mesmo stream).
- **Files modified:** CachedBodyHttpServletRequestTest.java (NOVO)
- **Commit:** `ca877bb`

**3. [Rule 2 - Imutabilidade defensiva] getCachedBody() retorna .clone()**
- **Found during:** Task 2 (escrita do wrapper)
- **Razao:** Plan recomenda copia defensiva mas deixa decisao aberta ("preferir copia para imutabilidade externa, mas custo de memoria"). Webhook do Meta tem <10KB tipico — custo de uma alocacao por chamada e desprezivel face ao beneficio de eliminar bug class onde caller mutaria o cache interno. Test `getCachedBody_retorna_copia_imutavel` enforce o comportamento.
- **Files modified:** CachedBodyHttpServletRequest.java (linha `return cachedBody.clone();`)
- **Commit:** `ca877bb`

**4. [Documentation - JavaDoc com anti-pattern]** JavaDoc dos 2 arquivos de producao referencia PITFALLS explicitamente (C-02, C-03, C-04). Notacao `Class#method` (JavaDoc) usada em vez de `Class.method` (Java syntax) para evitar collision com gates de grep do plan. Documentado em key-decisions.

Nenhum desvio Rule 1 (sem bugs) nem Rule 4 (sem mudancas arquiteturais).

## Issues Encountered

- **Gates de grep do plan colidem com JavaDoc anti-pattern.** PLAN especifica `grep "Arrays.equals|String.equals" HmacValidator.java` retorna 0 e `grep "ContentCachingRequestWrapper" CachedBodyHttpServletRequest.java` retorna 0 — mas o JavaDoc precisa documentar anti-pattern ("NUNCA usar X"). Round-trip: 1 (descoberto apos primeira escrita; resolvido reescrevendo comentarios para usar notacao JavaDoc `Class#method` + `o.s.web.util.ContentCachingRequest...` truncado). Custo: ~30s. Documentado como `pattern-established` para futuras waves.

## Verification Performed

| Check | Comando | Resultado |
|-------|---------|-----------|
| MessageDigest.isEqual presente exatamente 1x | `grep -c "MessageDigest\.isEqual" HmacValidator.java` | 1 (uso ativo, sem mencao em comentario) |
| Anti-pattern Arrays.equals/String.equals | `grep -c "Arrays\.equals\|String\.equals" HmacValidator.java` | 0 (gate passa — comentarios usam Class#method) |
| @Service ativo | linha sozinha contendo `@Service` | 1 (linha 31) |
| StreamUtils.copyToByteArray no construtor | `grep -c "StreamUtils\.copyToByteArray" CachedBodyHttpServletRequest.java` | 1 |
| Anti-pattern ContentCachingRequestWrapper literal | `grep -c "ContentCachingRequestWrapper" CachedBodyHttpServletRequest.java` | 0 (gate passa) |
| StandardCharsets.UTF_8 em getReader | `grep -c "StandardCharsets\.UTF_8" CachedBodyHttpServletRequest.java` | 1 (gate PITFALLS C-04) |
| HmacValidatorTest run isolado | `./mvnw -pl api-whatsapp test -Dtest=HmacValidatorTest` | Tests run: 13, Failures: 0, Time elapsed: 0.024s |
| CachedBodyHttpServletRequestTest run isolado | `./mvnw -pl api-whatsapp test -Dtest=CachedBodyHttpServletRequestTest` | Tests run: 6, Failures: 0, Time elapsed: 0.065s |
| api-whatsapp suite completa | `./mvnw verify -pl api-whatsapp -am` | BUILD SUCCESS — 32 tests verdes, ~7.5s |
| Reactor inteiro | (incluido em -am) | BUILD SUCCESS — lib-shared 20 + lib-consultas-client 3 + api-email 34 + api-storage 13 + api-consultas 4 + api-whatsapp 32 = 106 tests verdes. Zero regressao vs PLAN-04 baseline (87 tests). +19 = +13 HmacValidator +6 CachedBody |
| Pos-commit deletion check | `git diff --diff-filter=D --name-only HEAD~1 HEAD` | 0 deletions (intencional ou nao) |
| Commit existe | `git show ca877bb --stat` | OK — 4 arquivos, 453 inserções |

## Threat Model Compliance

Per `<threat_model>` do PLAN-05:

| Threat ID | Mitigation enforced | Test |
|-----------|---------------------|------|
| T-05-01 (Spoofing: empty-body shortcut) | NUNCA `if (body.length == 0) return true` — HmacValidator computa HMAC matematicamente sobre 0 bytes | `body_vazio_valida_quando_signature_corresponde` (true) + `body_vazio_com_signature_invalida_retorna_false` (false) |
| T-05-02 (InfoDisclosure: timing attack) | `MessageDigest.isEqual` constant-time, gates de grep proibem `Arrays.equals/String.equals` | `body_modificado_em_um_byte_retorna_false` (rejeicao funcional, NAO mede timing — micro-benchmark deferido per RESEARCH) |
| T-05-03 (Tampering: charset UTF-8) | `appSecret.getBytes(StandardCharsets.UTF_8)` no Mac.init + getReader() UTF-8 hardcoded | `payload_portugues_utf8_valida_corretamente` + `getReader_retorna_texto_utf8` (ambos com `Olá, gostaria de um orçamento`) |
| T-05-04 (Spoofing: lazy wrapper Spring) | Construtor de CachedBodyHttpServletRequest faz EAGER read, gate de grep proibe `ContentCachingRequestWrapper` literal | `getInputStream_pode_ser_lido_multiplas_vezes` (3 leituras consecutivas, todas retornam body completo) |
| T-05-05 (Tampering: hex DoS) | hexDecode privado captura char invalido + length impar, todas IllegalArgumentException viram false | `header_com_hex_invalido_retorna_false` + `header_com_tamanho_errado_retorna_false` (3 sub-asserts) |
| T-05-06 (InfoDisclosure: log de secret) | Logger nivel WARN/ERROR sem incluir valor do header, body ou secret | `accept` per plan — verificado por inspecao do codigo (mensagens descritivas only) |

Todas as 5 ameacas com disposition `mitigate` estao enforced + test-validated. T-05-06 (`accept`) verificado por inspection.

## Risks Resolved

- **Java 17+ HexFormat (vs Apache Commons Hex):** CONFIRMADO funciona em Java 21 do projeto, sem dep adicional. `HexFormat.of().formatHex(byte[])` no test helper + implementacao manual de `hexDecode` no Validator (estrita, throws). Zero round-trips.
- **MessageDigest.isEqual com arrays de tamanhos diferentes:** Defesa em profundidade — `EXPECTED_HEX_LENGTH = 64` ja garante 32 bytes apos decode. Mesmo se hexDecode tiver bug, `MessageDigest.isEqual` retorna false safely para arrays de tamanhos diferentes (per Javadoc do JDK).
- **MockHttpServletRequest no classpath:** CONFIRMADO transitivo via `spring-boot-starter-test` (que ja estava no pom.xml). Zero dep adicional, zero modificacao de pom.

## Concerns para Wave 6 (PLAN-06: HmacSignatureFilter + SecurityConfig + WebhookController)

1. **HmacSignatureFilter precisa instanciar `new CachedBodyHttpServletRequest(request)` no doFilterInternal ANTES de chamar HmacValidator** — RESEARCH §3.2 ja tem o codigo skeleton. Atencao: wrap PRIMEIRO, qualquer filter/MVC downstream consome o cached array (PITFALLS C-02). Use `chain.doFilter(cached, response)` (passa o wrapper, nao o request original).

2. **HmacSignatureFilter so processa POST** — GET hub.challenge nao tem body assinado, passa direto. Padrao do RESEARCH §3.2: `if (!HttpMethod.POST.matches(request.getMethod())) { chain.doFilter(...); return; }`.

3. **SecurityConfig precisa registrar o filter via FilterRegistrationBean com `addUrlPatterns("/webhook/*")` + `setOrder(Ordered.HIGHEST_PRECEDENCE)`** — gate mais cedo possivel. Tambem precisa instanciar `ApiKeyFilter` com `additionalPublicPaths = Set.of("/webhook")` para nao bloquear webhooks publicos com 401 de API key (eles ja sao validados via HMAC).

4. **D-02 do CONTEXT.md exige modificacao em `lib-shared/ApiKeyFilter`** para aceitar construtor `(String apiKey, Set<String> additionalPublicPaths)`. Wave 6 precisa fazer isso E garantir que api-email/api-storage/api-consultas continuam buildando (construtor de 1 arg preservado).

5. **WebhookController vai precisar `@PostMapping(value="/webhook/whatsapp")` + `@GetMapping(value="/webhook/whatsapp", produces=TEXT_PLAIN_VALUE)`** — D-04 do CONTEXT.md confirma POST e stub que retorna `ResponseEntity.ok().build()` (Phase 1 nao parseia body). GET ecoa `hub.challenge` em plain text.

6. **Verificar empty body em Wave 6:** PLAN-05 deixa empty body matematico (HmacValidator retorna true se signature corresponde). Wave 6 pode adicionar semantic check no Filter ou Controller (`if (body.length == 0) return 400`) — mas isso e Wave 6+ decision, nao deveria voltar pra Validator.

7. **Logger em filter NAO deve incluir body nem signature** — RESEARCH §3.2 ja aplica esse padrao (`log.warn("HMAC invalido em POST {}", request.getRequestURI())` sem dado sensivel). Manter.

8. **Wave 7 (integration test MockMvc) vai validar o fluxo completo end-to-end** — POST com body real do Meta + signature computada via mesmo HmacValidator. Vale lembrar que MockMvc por default usa charset ISO-8859-1 — Wave 7 precisa setar `MediaType.APPLICATION_JSON_UTF8` ou equivalente para o test passar com texto portugues.

## Self-Check: PASSED

- [x] `HmacValidator.java` existe em `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/`
- [x] `CachedBodyHttpServletRequest.java` existe em `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/web/`
- [x] `HmacValidatorTest.java` existe em `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/` com 13 cenarios
- [x] `CachedBodyHttpServletRequestTest.java` existe em `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/web/` com 6 cenarios
- [x] `MessageDigest.isEqual` usado exatamente 1x em HmacValidator.java (verificado via grep)
- [x] Zero ocorrencias de `Arrays.equals` ou `String.equals` em HmacValidator.java (verificado via grep — comentarios usam `Arrays#equals/String#equals` JavaDoc)
- [x] Zero ocorrencias literais de `ContentCachingRequestWrapper` em CachedBodyHttpServletRequest.java (verificado via grep)
- [x] `StreamUtils.copyToByteArray` usado no construtor (verificado via grep)
- [x] `StandardCharsets.UTF_8` em getReader (PITFALLS C-04, verificado via grep)
- [x] `@Service` em HmacValidator (linha 31, verificado via grep)
- [x] HmacValidatorTest passou: Tests run: 13, Failures: 0
- [x] CachedBodyHttpServletRequestTest passou: Tests run: 6, Failures: 0
- [x] api-whatsapp suite: 32 tests verdes (vs 13 antes — +19 novos)
- [x] Reator inteiro: BUILD SUCCESS — 106 tests verdes em ~7.5s, zero regressao vs PLAN-04
- [x] Test `payload_portugues_utf8_valida_corretamente` passa (gate PITFALLS C-04 com `Olá, gostaria de um orçamento`)
- [x] Test `body_modificado_em_um_byte_retorna_false` passa (gate tampering)
- [x] Test `body_vazio_valida_quando_signature_corresponde` passa (gate PITFALLS C-02 — confirma que NAO ha shortcut "skip se vazio")
- [x] Test `getReader_retorna_texto_utf8` passa (PITFALLS C-04 no wrapper)
- [x] Test `getInputStream_pode_ser_lido_multiplas_vezes` passa (gate cache eager — 3 leituras consecutivas com mesmo resultado)
- [x] Commit `ca877bb` existe no historico (`git log --oneline -3` confirma)
- [x] Pos-commit deletion check: 0 deletions (verificado via `git diff --diff-filter=D --name-only HEAD~1 HEAD`)

---
*Phase: 01-fundacao-hmac-webhook*
*Completed: 2026-05-05*
