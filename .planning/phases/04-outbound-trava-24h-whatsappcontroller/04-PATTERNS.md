# Phase 4: Outbound + Trava 24h + WhatsAppController - Pattern Map

**Mapped:** 2026-05-05
**Files analyzed:** 22 (NEW: 19 main + 5 tests; MODIFY: 2-3)
**Analogs found:** 22 / 22 (todos com analog forte no codebase Phase 1-3)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `service/WhatsAppCloudClient.java` | service / HTTP outbound client | request-response (sync HTTP, 4xx/5xx + retry) | `service/ErpCallbackClient.java` (Phase 3) + `service/MetaMediaClient.java` (Phase 3 download analog) | exact (annotation-driven Resilience4j com fallback no @Retry, Bearer per-request, RestClient) |
| `service/MediaCacheService.java` | service / cache | CRUD (sha256 lookup, save+catch race) | `service/IdempotencyService.java` (Phase 2) | exact (save+catch DataIntegrityViolationException + UNIQUE PK gate) |
| `service/WindowEnforcementService.java` | service / read-only enforcement | request-response (single native query) | `service/ClienteZapService.java` (Phase 2) + `repository/ClienteZapRepository.java` | exact (native @Query SELECT, mas sem REQUIRES_NEW pois e leitura) |
| `aspect/JanelaEnforcementAspect.java` | aspect / cross-cutting AOP | request-response (intercept around) | (sem analog direto — primeiro aspect customizado do projeto; Resilience4j AOP infra ja validada Phase 3) | role-match (novo sub-package; pattern documentado no RESEARCH §Pattern 2) |
| `aspect/JanelaProtegida.java` | annotation marker | n/a (metadata) | (sem analog — primeira annotation customizada do projeto) | no-analog (template Java standard) |
| `controller/WhatsAppController.java` | controller / REST entrypoint | request-response (5 endpoints) | `controller/WebhookController.java` (Phase 1+2) + `api-email/.../EmailController.java` (monorepo) | exact (thin wrapper @RestController + @RequestMapping + @Valid; ResponseEntity.ok) |
| `dto/EnviarTextoRequest.java` | DTO / request record | n/a (transport) | `dto/ComandoCallbackDTO.java` (Phase 3 record) + Jakarta validation pattern | exact (record + @NotBlank + @Pattern) |
| `dto/EnviarDocumentoRequest.java` | DTO / request record | n/a (transport) | `dto/ComandoCallbackDTO.java` + RESEARCH §Code Examples §6 | role-match (record com mediaBase64 — primeiro DTO base64 inbound do controller) |
| `dto/EnviarBotoesRequest.java` + `BotaoDto.java` | DTO / request record (nested) | n/a | `dto/ComandoCallbackDTO.java` + Jakarta validation `@Size(max=3)` | role-match (records aninhados @Valid List<>) |
| `dto/EnviarListaRequest.java` + `SecaoDto.java` + `ItemDto.java` | DTO / request record (nested) | n/a | `dto/ComandoCallbackDTO.java` + RESEARCH §Pattern `@AssertTrue` | role-match (record com `@AssertTrue isTotalItensValido()` cross-field) |
| `dto/EnvioResponse.java` + `StatusResponse.java` | DTO / response record | n/a | `dto/ComandoCallbackDTO.java` (record output) | exact (record output Jackson nativo) |
| `exception/JanelaConversaFechadaException.java` | exception / domain | n/a | `lib-shared/.../ModuloException.java` (super) | exact (extends ModuloException com HttpStatus.CONFLICT + codigo) |
| `exception/MetaApiException.java` | exception / domain | n/a | `lib-shared/.../ModuloException.java` (super) | role-match (extends ModuloException + carrega metaErrorCode + tipo enum — primeiro com payload extra) |
| `WhatsAppCloudClientTest.java` | test / integration | request-response (WireMock) | `service/ErpCallbackClientTest.java` (Phase 3) | exact (mesmo @SpringBootTest + WireMock + cbRegistry.find().reset() + scenarioState) |
| `MediaCacheServiceTest.java` | test / integration | CRUD (H2) | (semelhante a `IdempotencyServiceTest` Phase 2) | exact (@SpringBootTest + H2 + race scenario) |
| `WindowEnforcementServiceTest.java` | test / integration | request-response (H2) | (semelhante a `ClienteZapServiceTest` Phase 2) | exact (@SpringBootTest + H2 + native query) |
| `JanelaEnforcementAspectTest.java` | test / integration | aspect verification | `service/ErpCallbackClientTest.java` (Phase 3) — Mockito spy + WireMock counter | role-match (Mockito spy contar invocacoes + verify times(1) em 3 retries) |
| `WhatsAppControllerTest.java` | test / web layer | request-response (MockMvc) | `controller/WebhookControllerTest.java` (Phase 1) — analog @WebMvcTest | role-match (`@WebMvcTest` + MockMvc + @MockBean WhatsAppCloudClient) |
| `application.yml` (MODIFY) | config | n/a | bloco `resilience4j.{circuitbreaker,retry}.instances.erp-callback` (Phase 3 herdado) | exact (espelhar `erp-callback` para `whatsapp-cloud`) |
| `application-test.yml` (MODIFY) | config | n/a | bloco `whatsapp-cloud` em test profile, espelhado de `erp-callback` (Phase 3) | exact (wait-duration: 50ms) |
| `lib-shared/.../ErrorResponse.java` (MODIFY) | DTO / shared | n/a | (proprio arquivo a modificar) | exact (adicionar campo `codigo` String + `metaErrorCode` Integer — campos opcionais nullable) |
| `lib-shared/.../GlobalExceptionHandler.java` (opcional MODIFY) | exception handler | request-response | (proprio arquivo a modificar) | role-match (propagar `codigo` + `metaErrorCode` quando ModuloException for `MetaApiException` ou `JanelaConversaFechadaException`) |

---

## Pattern Assignments

### `service/WhatsAppCloudClient.java` (service, request-response)

**Analog:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/ErpCallbackClient.java` (Phase 3)
**Auxiliary analog:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/MetaMediaClient.java` (Phase 3 — Bearer per-request + Graph API base URL)

**Imports pattern** (copiar verbatim, ajustar tipos):
```java
package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.aspect.JanelaProtegida;
import br.com.erpkit.whatsapp.dto.EnvioResponse;
import br.com.erpkit.whatsapp.exception.MetaApiException;
import br.com.erpkit.whatsapp.model.Direcao;
import br.com.erpkit.whatsapp.model.MensagemLog;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
```

**Constructor + RestClient pattern** (copiar de `ErpCallbackClient.java:63-72` adaptando a `metaApiBaseUrl`):
```java
private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudClient.class);

private final RestClient restClient;
private final WhatsAppProperties properties;
private final MediaCacheService mediaCacheService;
private final MensagemLogRepository mensagemLogRepository;

public WhatsAppCloudClient(WhatsAppProperties properties,
                           MediaCacheService mediaCacheService,
                           MensagemLogRepository mensagemLogRepository) {
    this.properties = properties;
    this.mediaCacheService = mediaCacheService;
    this.mensagemLogRepository = mensagemLogRepository;
    this.restClient = RestClient.builder()
            .baseUrl(properties.getMetaApiBaseUrl())
            .build();
}
```

**Core annotation-driven pattern** (copiar de `ErpCallbackClient.java:85-96` — locked: fallback NO `@Retry`):
```java
@JanelaProtegida
@CircuitBreaker(name = "whatsapp-cloud")
@Retry(name = "whatsapp-cloud", fallbackMethod = "fallbackEnviarTexto")
public EnvioResponse enviarTexto(String telefone, String texto) {
    Map<String, Object> body = Map.of(
        "messaging_product", "whatsapp",
        "recipient_type", "individual",
        "to", telefone,
        "type", "text",
        "text", Map.of("body", texto)
    );
    Map response = restClient.post()
        .uri("/{phoneNumberId}/messages", properties.getPhoneNumberId())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(Map.class);
    String wamid = extrairWamid(response);
    mensagemLogRepository.save(new MensagemLog(wamid, telefone, Direcao.out, "text", texto, null));
    return new EnvioResponse(wamid);
}
```

**Multipart upload pattern** (RESEARCH §Code Examples §2 — sem analog interno; padrao Spring docs):
```java
private String uploadMedia(byte[] bytes, String mimeType, String filename) {
    ByteArrayResource fileResource = new ByteArrayResource(bytes) {
        @Override public String getFilename() { return filename; }
    };
    MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    parts.add("messaging_product", "whatsapp");
    parts.add("type", mimeType);
    parts.add("file", fileResource);

    Map response = restClient.post()
        .uri("/{phoneNumberId}/media", properties.getPhoneNumberId())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(parts)
        .retrieve()
        .body(Map.class);
    return (String) response.get("id");
}
```

**Fallback pattern** (copiar de `ErpCallbackClient.java:110-115` — gotcha 03-04 RESOLVED):
```java
@SuppressWarnings("unused") // referenciado por fallbackMethod = "fallbackEnviarTexto"
private EnvioResponse fallbackEnviarTexto(String telefone, String texto, Throwable t) {
    log.error("WhatsApp Cloud falhou apos retry+CB: telefone={} tipo=text: {}", telefone, t.getMessage());
    throw classificar(t); // converte para MetaApiException com tipo apropriado
}
```

**Classificacao de excecoes (D-02):**
```java
private MetaApiException classificar(Throwable t) {
    if (t instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
        return new MetaApiException(MetaApiException.Tipo.CIRCUIT_OPEN, null, t.getMessage());
    }
    if (t instanceof org.springframework.web.client.HttpClientErrorException he) {
        Integer metaCode = extrairMetaErrorCode(he.getResponseBodyAsString());
        return new MetaApiException(MetaApiException.Tipo.CATEGORIA_4XX, metaCode, t.getMessage());
    }
    if (t instanceof org.springframework.web.client.ResourceAccessException) {
        return new MetaApiException(MetaApiException.Tipo.TIMEOUT, null, t.getMessage());
    }
    return new MetaApiException(MetaApiException.Tipo.INDISPONIVEL_5XX, null, t.getMessage());
}
```

**O que adaptar:**
- baseUrl em `metaApiBaseUrl` (nao `erpCallbackUrl`)
- `header(AUTHORIZATION, "Bearer " + accessToken)` em CADA chamada (nao defaultHeader — PITFALLS C-09/C-14)
- 4 metodos publicos `enviarTexto/enviarDocumento/enviarBotoes/enviarLista` — todos com 3 annotations
- `enviarDocumento` chama `mediaCacheService.buscarMediaId()` antes de `uploadMedia()` (RESEARCH §Code Examples §3)
- `MensagemLogRepository.save(direcao=out)` apos sucesso (OUT-09)
- Fallback **lanca** `MetaApiException` (NAO suprime como `ErpCallbackClient` que e fire-and-forget — outbound do controller precisa propagar erro ao ERP)
- `extrairWamid(Map)`: `((List<Map>) response.get("messages")).get(0).get("id")` (RESEARCH §A4)
- Javadoc explicito: "Por design (D9 PROJECT.md, OUT-05), este cliente NAO expoe `enviarTemplate(...)`"

**O que NAO copiar:**
- timeout configuravel via `properties.getCallbackTimeout()` (irrelevante — usa Resilience4j retry; Spring HTTP client global timeout ja em yml)
- `SimpleClientHttpRequestFactory` com timeout custom (RestClient default factory funciona; timeout global em `spring.http.client.*` em yml)
- Fallback retorna void (silencia) — outbound DEVE relancar `MetaApiException`

---

### `service/MediaCacheService.java` (service, CRUD)

**Analog:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/IdempotencyService.java` (Phase 2)

**Imports pattern**:
```java
package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.model.MediaCache;
import br.com.erpkit.whatsapp.repository.MediaCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
```

**Save+catch race protection pattern** (copiar de `IdempotencyService.java:62-75`):
```java
public boolean tentarPersistir(String wamid, String telefone, Direcao direcao,
                                String tipo, String conteudo, String mediaId) {
    MensagemLog mensagem = new MensagemLog(wamid, telefone, direcao, tipo, conteudo, mediaId);
    try {
        repository.save(mensagem);
        log.debug("Idempotencia: wamid={} persistido (direcao={}, tipo={})", wamid, direcao, tipo);
        return true;
    } catch (DataIntegrityViolationException e) {
        log.debug("Idempotencia: wamid={} ja existe — Meta reenviou, silenciado", wamid);
        return false;
    }
}
```

**Adaptar para MediaCacheService** (RESEARCH §Code Examples §4 — D-04 TTL estrito 30d):
```java
@Service
public class MediaCacheService {
    private static final Logger log = LoggerFactory.getLogger(MediaCacheService.class);
    private static final Duration TTL = Duration.ofDays(30);

    private final MediaCacheRepository repository;

    public MediaCacheService(MediaCacheRepository repository) {
        this.repository = repository;
    }

    public Optional<String> buscarMediaId(byte[] bytes) {
        String hash = sha256Hex(bytes);
        return repository.findByArquivoHashAndExpiraEmAfter(hash, Instant.now())
            .map(MediaCache::getMediaId);
    }

    public void registrarUpload(byte[] bytes, String mediaId) {
        String hash = sha256Hex(bytes);
        Instant expira = Instant.now().plus(TTL);
        try {
            // Upsert simples: deleta antigo (expirado) + save novo
            repository.findById(hash).ifPresent(repository::delete);
            repository.save(new MediaCache(hash, mediaId, expira));
            log.debug("MediaCache: hash={} mediaId={} expira={}", hash, mediaId, expira);
        } catch (DataIntegrityViolationException e) {
            // Race: outro thread fez upload do mesmo arquivo. PK gate atomico (Phase 2 pattern).
            log.debug("MediaCache race: hash={} — outro thread ja registrou", hash);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
    }
}
```

**O que adaptar:**
- `MediaCacheRepository.findByArquivoHashAndExpiraEmAfter(hash, Instant.now())` ja existe em Phase 2 — usar como esta
- `MediaCache` constructor `(arquivoHash, mediaId, expiraEm)` — ja existente
- `HexFormat.of().formatHex(digest)` (Java 17+) — sem dep extra (RESEARCH §Don't Hand-Roll)

**O que NAO copiar:**
- contrato `boolean tentarPersistir(...)` retornando `true/false` — `registrarUpload` retorna void (silencia race)
- mensagem entrante / Direcao — irrelevante para cache

---

### `service/WindowEnforcementService.java` (service, request-response)

**Analog:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/service/ClienteZapService.java` (Phase 2) + `ClienteZapRepository.java` (Phase 2)

**Repository extension pattern** (adicionar metodo a `ClienteZapRepository.java` — copiar formato de `atualizarUltimaMensagemEm` em linhas 44-50):
```java
// Em ClienteZapRepository.java (ADICIONAR — espelha pattern native @Query)
@Query(value =
    "SELECT ultima_mensagem_em FROM whatsapp.clientes_zap WHERE telefone = :telefone",
    nativeQuery = true)
Optional<Instant> buscarUltimaMensagemEm(@Param("telefone") String telefone);
```

**Service pattern** (espelhar layout de `ClienteZapService.java:36-46` mas SEM `@Transactional` — apenas leitura):
```java
package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.exception.JanelaConversaFechadaException;
import br.com.erpkit.whatsapp.repository.ClienteZapRepository;
import br.com.erpkit.whatsapp.util.TelefoneBR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class WindowEnforcementService {

    private static final Logger log = LoggerFactory.getLogger(WindowEnforcementService.class);
    private static final Duration JANELA = Duration.ofHours(24);

    private final ClienteZapRepository repository;

    public WindowEnforcementService(ClienteZapRepository repository) {
        this.repository = repository;
    }

    public void verificarJanela(String telefone) {
        String normalizado = TelefoneBR.normalizar(telefone);
        Optional<Instant> ultima = repository.buscarUltimaMensagemEm(normalizado);
        if (ultima.isEmpty()) {
            log.warn("Janela 24h: telefone={} nao tem mensagem entrante registrada", normalizado);
            throw new JanelaConversaFechadaException(normalizado, null);
        }
        Duration diff = Duration.between(ultima.get(), Instant.now());
        if (diff.compareTo(JANELA) > 0) {
            log.warn("Janela 24h fechada: telefone={} ultima_mensagem_em={} diff={}h",
                normalizado, ultima.get(), diff.toHours());
            throw new JanelaConversaFechadaException(normalizado, ultima.get());
        }
    }
}
```

**O que adaptar:**
- `TelefoneBR.normalizar(telefone)` antes da query (PITFALLS C-13)
- Native query — pula JPA L1 cache (PITFALLS C-01 — Phase 2 PER-07 escreve com REQUIRES_NEW + NOW())
- Fallback se `Optional<Instant>` quebrar em H2 (RESEARCH §Pitfall 4): retornar `Instant` nullable

**O que NAO copiar:**
- `@Transactional(REQUIRES_NEW)` — apenas leitura
- `@Modifying` — apenas SELECT

---

### `aspect/JanelaProtegida.java` (annotation marker)

**Analog:** Sem analog interno — primeira annotation customizada do projeto.

**Pattern direto (D-03 RESEARCH §Pattern 2):**
```java
package br.com.erpkit.whatsapp.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker para metodos do {@link br.com.erpkit.whatsapp.service.WhatsAppCloudClient}
 * que devem ter a janela 24h verificada pelo {@link JanelaEnforcementAspect} antes
 * de qualquer chamada Cloud API.
 *
 * <p><b>Convencao posicional:</b> o metodo anotado DEVE ter {@code String telefone}
 * como primeiro argumento. Aspect le {@code args[0]} e lanca
 * {@code IllegalStateException} fail-fast em runtime se a convencao nao for honrada.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JanelaProtegida { }
```

---

### `aspect/JanelaEnforcementAspect.java` (aspect / cross-cutting AOP)

**Analog:** Sem analog interno — primeiro aspect customizado. Pattern documentado em RESEARCH §Pattern 2.

**Imports pattern:**
```java
package br.com.erpkit.whatsapp.aspect;

import br.com.erpkit.whatsapp.service.WindowEnforcementService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
```

**Core aspect pattern** (RESEARCH §Pattern 2 — D-03 + Resilience4j issue #2383):
```java
/**
 * Aspect que aplica a trava 24h ANTES de qualquer chamada outbound Cloud API
 * (D-03 + OUT-07).
 *
 * <p><b>{@code @Order(HIGHEST_PRECEDENCE)} crucial:</b> Spring `@Order` semantica =
 * lower numeric = outermost. Resilience4j Spring Boot defaults: Retry order =
 * LOWEST_PRECEDENCE-3, CircuitBreaker = LOWEST_PRECEDENCE-2. HIGHEST_PRECEDENCE
 * (Integer.MIN_VALUE) garante que este aspect rode FORA do retry loop — 1 check
 * por chamada externa, nao 1 por tentativa. Sem isso, em scenario 5xx + 3 retries,
 * verificarJanela seria chamado 3x (desperdicio + race em boundary 24h durante
 * backoff exponencial 1s/2s/4s). Validado empiricamente via Mockito counter==1
 * em test {@code aspect_invoca_apenas_uma_vez_em_3_retries}.
 *
 * <p><b>Convencao posicional {@code args[0]}:</b> aspect le primeiro argumento
 * como {@code String telefone}. Fail-fast com {@link IllegalStateException} em
 * runtime se metodo anotado nao seguir convencao — pegado em test, nao em prod.
 *
 * <p>Ref: github.com/resilience4j/resilience4j/issues/2383, RESEARCH §Pattern 2.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JanelaEnforcementAspect {

    private final WindowEnforcementService windowService;

    public JanelaEnforcementAspect(WindowEnforcementService windowService) {
        this.windowService = windowService;
    }

    @Around("@annotation(br.com.erpkit.whatsapp.aspect.JanelaProtegida)")
    public Object enforce(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        if (args.length == 0 || !(args[0] instanceof String telefone)) {
            throw new IllegalStateException(
                "Metodo @JanelaProtegida deve ter telefone como primeiro argumento String: "
                    + pjp.getSignature());
        }
        windowService.verificarJanela(telefone); // throws JanelaConversaFechadaException se > 24h
        return pjp.proceed();
    }
}
```

---

### `controller/WhatsAppController.java` (controller, request-response)

**Analog:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/controller/WebhookController.java` (Phase 1+2) + `api-email/src/main/java/br/com/erpkit/email/controller/EmailController.java` (monorepo standard)

**Imports pattern** (combinar `WebhookController.java:1-20` com `EmailController.java:1-20`):
```java
package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.shared.exception.ModuloException;
import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.dto.EnviarBotoesRequest;
import br.com.erpkit.whatsapp.dto.EnviarDocumentoRequest;
import br.com.erpkit.whatsapp.dto.EnviarListaRequest;
import br.com.erpkit.whatsapp.dto.EnviarTextoRequest;
import br.com.erpkit.whatsapp.dto.EnvioResponse;
import br.com.erpkit.whatsapp.dto.StatusResponse;
import br.com.erpkit.whatsapp.service.WhatsAppCloudClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
```

**Class skeleton pattern** (espelhar `EmailController.java:22-30` + RESEARCH §Pattern 5):
```java
@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppController {

    private final WhatsAppCloudClient cloudClient;
    private final WhatsAppProperties properties;
    private final CircuitBreakerRegistry cbRegistry;

    public WhatsAppController(WhatsAppCloudClient cloudClient,
                              WhatsAppProperties properties,
                              CircuitBreakerRegistry cbRegistry) {
        this.cloudClient = cloudClient;
        this.properties = properties;
        this.cbRegistry = cbRegistry;
    }

    @PostMapping("/enviar-texto")
    public ResponseEntity<EnvioResponse> enviarTexto(@Valid @RequestBody EnviarTextoRequest req) {
        return ResponseEntity.ok(cloudClient.enviarTexto(req.telefone(), req.texto()));
    }

    @PostMapping("/enviar-documento")
    public ResponseEntity<EnvioResponse> enviarDocumento(@Valid @RequestBody EnviarDocumentoRequest req) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(req.mediaBase64());
        } catch (IllegalArgumentException e) {
            throw new ModuloException("mediaBase64 invalido", HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(cloudClient.enviarDocumento(
            req.telefone(), bytes, req.filename(), req.mimeType(), req.caption()));
    }

    @PostMapping("/enviar-botoes")
    public ResponseEntity<EnvioResponse> enviarBotoes(@Valid @RequestBody EnviarBotoesRequest req) {
        return ResponseEntity.ok(cloudClient.enviarBotoes(req.telefone(), req.texto(), req.botoes()));
    }

    @PostMapping("/enviar-lista")
    public ResponseEntity<EnvioResponse> enviarLista(@Valid @RequestBody EnviarListaRequest req) {
        return ResponseEntity.ok(cloudClient.enviarLista(req.telefone(), req.texto(), req.secoes()));
    }

    @GetMapping("/status")
    public ResponseEntity<StatusResponse> status() {
        String state = cbRegistry.find("whatsapp-cloud")
            .map(cb -> cb.getState().name())
            .orElse("UNKNOWN");
        return ResponseEntity.ok(new StatusResponse("UP", state, properties.getPhoneNumberId()));
    }
}
```

**O que adaptar:**
- `@Valid @RequestBody` em todos os 4 POST (forca Bean Validation; GlobalExceptionHandler.handleValidation mapeia para 400)
- `Base64.getDecoder().decode(...)` em try/catch IllegalArgumentException → 400 (D-01)
- NAO ha `try/catch` para ModuloException — GlobalExceptionHandler em lib-shared captura

**O que NAO copiar:**
- `HttpServletRequest` raw body / HMAC handling de `WebhookController` (irrelevante — `/api/whatsapp/*` usa ApiKeyFilter, nao HMAC)
- Estatisticas / paginacao / PUT reenviar de `EmailController` — irrelevante

---

### `dto/EnviarTextoRequest.java` (DTO record + Jakarta validation)

**Analog:** `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/dto/ComandoCallbackDTO.java` (Phase 3 record output) — adaptar para INPUT com validation

**Pattern direto** (RESEARCH §Code Examples §6):
```java
package br.com.erpkit.whatsapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EnviarTextoRequest(
    @NotBlank(message = "telefone obrigatorio")
    @Pattern(regexp = "^\\d{10,15}$", message = "telefone deve conter apenas digitos (10-15)")
    String telefone,

    @NotBlank(message = "texto obrigatorio")
    @Size(max = 4096, message = "texto excede 4096 chars")
    String texto
) {}
```

---

### `dto/EnviarDocumentoRequest.java` (DTO record com mediaBase64)

**Analog:** `ComandoCallbackDTO.java` (Phase 3 — usa `mediaBase64` como String). RESEARCH §Code Examples §6 + D-01 locked.

**Pattern direto:**
```java
package br.com.erpkit.whatsapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EnviarDocumentoRequest(
    @NotBlank(message = "telefone obrigatorio")
    @Pattern(regexp = "^\\d{10,15}$") String telefone,

    @NotBlank(message = "mediaBase64 obrigatorio")
    @Size(max = 18_000_000, message = "mediaBase64 excede limite (~13MB binario)")
    String mediaBase64,

    @NotBlank @Pattern(regexp = "^[a-z]+/[a-z0-9.+-]+$", message = "mimeType invalido")
    String mimeType,

    @NotBlank @Size(max = 255) String filename,

    @Size(max = 1024) String caption
) {}
```

---

### `dto/EnviarBotoesRequest.java` + `BotaoDto.java` (records aninhados com @Size(max=3))

**Analog:** `ComandoCallbackDTO.java` (record). Pattern Jakarta `@Valid List<>` em RESEARCH §Code Examples §6.

**Pattern direto** (limites Cloud API: max 3 botoes, title 20 chars, id 256 chars):
```java
package br.com.erpkit.whatsapp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EnviarBotoesRequest(
    @NotBlank @Pattern(regexp = "^\\d{10,15}$") String telefone,
    @NotBlank @Size(max = 1024) String texto,
    @NotEmpty @Size(max = 3, message = "Maximo 3 botoes (Cloud API limit)")
    @Valid List<BotaoDto> botoes
) {}

// BotaoDto.java (arquivo separado)
public record BotaoDto(
    @NotBlank @Size(max = 256) String id,
    @NotBlank @Size(max = 20, message = "title max 20 chars (Cloud API limit)") String title
) {}
```

---

### `dto/EnviarListaRequest.java` + `SecaoDto.java` + `ItemDto.java` (records com `@AssertTrue` cross-field)

**Analog:** RESEARCH §Code Examples §6 + jakarta.ee bean-validation 3.0 §`@AssertTrue` em metodo `isXxx()`.

**Pattern direto:**
```java
package br.com.erpkit.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

public record EnviarListaRequest(
    @NotBlank @Pattern(regexp = "^\\d{10,15}$") String telefone,
    @NotBlank @Size(max = 1024) String texto,
    @NotEmpty @Size(max = 10, message = "Maximo 10 secoes")
    @Valid List<SecaoDto> secoes
) {
    @AssertTrue(message = "Total de itens em todas as secoes excede 10 (Cloud API limit)")
    @JsonIgnore
    public boolean isTotalItensValido() {
        if (secoes == null) return true;
        int total = secoes.stream()
            .filter(Objects::nonNull)
            .mapToInt(s -> s.itens() == null ? 0 : s.itens().size())
            .sum();
        return total <= 10;
    }
}

// SecaoDto.java
public record SecaoDto(
    @NotBlank @Size(max = 24) String titulo,
    @NotEmpty @Valid List<ItemDto> itens
) {}

// ItemDto.java
public record ItemDto(
    @NotBlank @Size(max = 200) String id,
    @NotBlank @Size(max = 24) String title,
    @Size(max = 72) String description
) {}
```

---

### `dto/EnvioResponse.java` + `StatusResponse.java` (response records)

**Analog:** `ComandoCallbackDTO.java` (record output, Jackson nativo).

**Pattern direto:**
```java
// EnvioResponse.java
package br.com.erpkit.whatsapp.dto;
public record EnvioResponse(String wamid) { }

// StatusResponse.java (D-04 minimal)
public record StatusResponse(
    String status,                  // "UP" | "DOWN"
    String circuitBreakerState,     // "CLOSED" | "OPEN" | "HALF_OPEN" | "UNKNOWN"
    String phoneNumberId
) {}
```

---

### `exception/JanelaConversaFechadaException.java`

**Analog:** `lib-shared/src/main/java/br/com/erpkit/shared/exception/ModuloException.java` (super)

**Pattern direto:**
```java
package br.com.erpkit.whatsapp.exception;

import br.com.erpkit.shared.exception.ModuloException;
import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * Janela de 24h da conversa esta fechada — ERP tentou enviar mensagem para cliente
 * cuja ultima mensagem entrante foi ha mais de 24h, ou cliente sem nenhuma mensagem
 * entrante registrada.
 *
 * <p><b>HTTP 409 + codigo {@code JANELA_24H_FECHADA}</b> (D-02). Nao retentavel —
 * ERP precisa esperar nova mensagem entrante do cliente para reabrir a janela.
 */
public class JanelaConversaFechadaException extends ModuloException {

    private final String telefone;
    private final Instant ultimaMensagemEm;

    public JanelaConversaFechadaException(String telefone, Instant ultimaMensagemEm) {
        super(montarMensagem(telefone, ultimaMensagemEm), HttpStatus.CONFLICT);
        this.telefone = telefone;
        this.ultimaMensagemEm = ultimaMensagemEm;
    }

    public String getCodigo() { return "JANELA_24H_FECHADA"; }
    public String getTelefone() { return telefone; }
    public Instant getUltimaMensagemEm() { return ultimaMensagemEm; }

    private static String montarMensagem(String telefone, Instant ultima) {
        if (ultima == null) {
            return "Janela 24h: telefone " + telefone + " sem mensagem entrante registrada";
        }
        return "Janela 24h fechada: telefone " + telefone + " ultima entrante em " + ultima;
    }
}
```

---

### `exception/MetaApiException.java`

**Analog:** `lib-shared/src/main/java/br/com/erpkit/shared/exception/ModuloException.java` (super)

**Pattern direto** (D-02 — carrega `metaErrorCode` Integer + `tipo` enum):
```java
package br.com.erpkit.whatsapp.exception;

import br.com.erpkit.shared.exception.ModuloException;
import org.springframework.http.HttpStatus;

/**
 * Falha na chamada Cloud API do Meta. Carrega {@link Tipo} categorizando a falha
 * + opcionalmente {@code metaErrorCode} (codigo numerico do Meta — ex: 131026
 * invalid phone, 131009 token expired) extraido do response body.
 *
 * <p><b>Mapping (D-02):</b>
 * <table>
 *   <tr><th>Tipo</th><th>HTTP</th><th>codigo</th></tr>
 *   <tr><td>CATEGORIA_4XX</td><td>422</td><td>META_ERROR</td></tr>
 *   <tr><td>INDISPONIVEL_5XX</td><td>502</td><td>META_INDISPONIVEL</td></tr>
 *   <tr><td>TIMEOUT</td><td>504</td><td>META_TIMEOUT</td></tr>
 *   <tr><td>CIRCUIT_OPEN</td><td>503</td><td>CIRCUIT_OPEN</td></tr>
 * </table>
 */
public class MetaApiException extends ModuloException {

    public enum Tipo {
        CATEGORIA_4XX(HttpStatus.UNPROCESSABLE_ENTITY, "META_ERROR"),
        INDISPONIVEL_5XX(HttpStatus.BAD_GATEWAY, "META_INDISPONIVEL"),
        TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "META_TIMEOUT"),
        CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "CIRCUIT_OPEN");

        private final HttpStatus status;
        private final String codigo;

        Tipo(HttpStatus status, String codigo) { this.status = status; this.codigo = codigo; }
        public HttpStatus getStatus() { return status; }
        public String getCodigo() { return codigo; }
    }

    private final Tipo tipo;
    private final Integer metaErrorCode;

    public MetaApiException(Tipo tipo, Integer metaErrorCode, String mensagem) {
        super(mensagem, tipo.getStatus());
        this.tipo = tipo;
        this.metaErrorCode = metaErrorCode;
    }

    public Tipo getTipo() { return tipo; }
    public Integer getMetaErrorCode() { return metaErrorCode; }
    public String getCodigo() { return tipo.getCodigo(); }
}
```

---

### `WhatsAppCloudClientTest.java` (test, integration)

**Analog:** `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/service/ErpCallbackClientTest.java` (Phase 3) — copiar layout EXATAMENTE

**Imports + scaffold pattern** (copiar de `ErpCallbackClientTest.java:1-90`):
```java
package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
class WhatsAppCloudClientTest {

    private static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) wireMock.stop();
    }

    @DynamicPropertySource
    static void overrideMetaUrl(DynamicPropertyRegistry registry) {
        // CRITICO: difere do ErpCallbackClient — usa metaApiBaseUrl
        registry.add("app.modulos.whatsapp.metaApiBaseUrl", () -> wireMock.baseUrl());
    }

    @Autowired WhatsAppCloudClient client;
    @Autowired CircuitBreakerRegistry cbRegistry;

    @BeforeEach
    void resetEverything() {
        wireMock.resetAll();
        // Reset CB whatsapp-cloud (Singleton — Risk A3 cross-test pollution)
        cbRegistry.find("whatsapp-cloud").ifPresent(CircuitBreaker::reset);
    }
    // ...tests usando scenarios igual ErpCallbackClientTest...
}
```

**Counter assertion patterns** (copiar de `ErpCallbackClientTest.java:104-138`):
```java
// 5xx recupera (counter==3 prova AOP funcionando):
wireMock.verify(3, postRequestedFor(urlPathMatching("/test-phone-id/messages")));

// 4xx no retry (counter==1):
wireMock.verify(1, postRequestedFor(urlPathMatching("/test-phone-id/messages")));

// Circuit aberto:
var cb = cbRegistry.find("whatsapp-cloud").orElseThrow();
assertThat(cb.getState()).isIn(CircuitBreaker.State.OPEN, CircuitBreaker.State.HALF_OPEN);
```

**Multipart upload assertion (RESEARCH §Pitfall 5):**
```java
// Validar que upload envia 3 fields obrigatorios
wireMock.verify(1, postRequestedFor(urlPathMatching("/test-phone-id/media"))
    .withRequestBodyPart(aMultipart().withName("messaging_product")
        .withBody(equalTo("whatsapp")).build())
    .withRequestBodyPart(aMultipart().withName("type").build())
    .withRequestBodyPart(aMultipart().withName("file").build()));
```

**Bearer leak gate (PITFALLS C-14):**
```java
// access_token NUNCA em query string
wireMock.getAllServeEvents().forEach(event ->
    assertThat(event.getRequest().getUrl())
        .as("Bearer NUNCA em query param (PITFALLS C-14)")
        .doesNotContain("access_token="));
```

---

### `MediaCacheServiceTest.java` (test, integration H2)

**Analog:** Mesmo pattern de `IdempotencyServiceTest.java` (Phase 2 — `@SpringBootTest + H2 + race scenario`).

**Test scenarios obrigatorios** (RESEARCH §Wave 0 Gaps + SC-3):
1. `hit_dentro_do_ttl_retorna_media_id`
2. `miss_quando_hash_nao_existe_retorna_empty`
3. `miss_quando_expirado_retorna_empty` (entry com `expira_em < now()`)
4. `race_em_registrar_silencia_data_integrity_violation`

---

### `WindowEnforcementServiceTest.java` (test, integration H2)

**Analog:** Mesmo pattern de `ClienteZapServiceTest.java` (Phase 2).

**Test scenarios obrigatorios** (RESEARCH §Wave 0 Gaps + SC-2):
1. `cliente_com_ultima_em_23h_passa` (insert `clientes_zap` com `ultima_mensagem_em = NOW() - 23h` via JdbcTemplate; chama service; sem excecao)
2. `cliente_com_ultima_em_25h_lanca` (insert com 25h; assertThrows JanelaConversaFechadaException)
3. `cliente_inexistente_lanca` (telefone nao em clientes_zap; assertThrows)

---

### `JanelaEnforcementAspectTest.java` (test, integration aspect)

**Analog:** `service/ErpCallbackClientTest.java` (Phase 3 — Mockito spy + WireMock counter padrao).

**Test scenarios obrigatorios** (RESEARCH §Wave 0 Gaps + RESEARCH §Pattern 2 empirical validation + SC-2):
1. `aspect_invoca_apenas_uma_vez_em_3_retries` — `@MockBean WindowEnforcementService` (ou `@SpyBean`); WireMock 500/500/200 em scenarioState; `client.enviarTexto(...)`; `verify(windowService, times(1)).verificarJanela(any())` — CRITICO (counter==1 prova que aspect roda fora do retry loop, validando @Order(HIGHEST_PRECEDENCE))
2. `aspect_lanca_se_telefone_nao_e_string` — anotar metodo de test bean com @JanelaProtegida + arg Long; assertThrows IllegalStateException
3. `aspect_propaga_janela_fechada_exception` — when(windowService).thenThrow; assert that controller-level path lanca

---

### `WhatsAppControllerTest.java` (test, web layer)

**Analog:** `controller/WebhookControllerTest.java` (Phase 1) + monorepo standard `@WebMvcTest`.

**Imports + scaffold:**
```java
@WebMvcTest(WhatsAppController.class)
@AutoConfigureMockMvc(addFilters = false) // bypass ApiKeyFilter em test layer
class WhatsAppControllerTest {

    @Autowired MockMvc mvc;
    @MockBean WhatsAppCloudClient cloudClient;
    @MockBean WhatsAppProperties properties;
    @MockBean CircuitBreakerRegistry cbRegistry;

    // happy path 200, validation 400, janela 409, meta 422/502, circuit 503
}
```

**Test scenarios obrigatorios** (RESEARCH §Wave 0 Gaps + OUT-11 + SC-2 + SC-5):
1. `enviar_texto_happy_200` — when(cloudClient.enviarTexto).thenReturn → 200 + `wamid` no body
2. `enviar_texto_validation_400` — body `{}` ou texto vazio → 400 + `campos.telefone` no ErrorResponse
3. `enviar_texto_janela_409` — when(cloudClient).thenThrow(JanelaConversaFechadaException) → 409 + `codigo=JANELA_24H_FECHADA`
4. `enviar_documento_base64_invalido_400` — mediaBase64 com chars invalidos → 400 + `mediaBase64 invalido`
5. `enviar_botoes_4_botoes_400` — 4 botoes no body → 400 (Jakarta @Size(max=3))
6. `enviar_lista_total_11_itens_400` — soma cross-secoes > 10 → 400 (`@AssertTrue isTotalItensValido`)
7. `meta_4xx_retorna_422` — when(cloudClient).thenThrow(MetaApiException(CATEGORIA_4XX, 131026, ...)) → 422 + `codigo=META_ERROR` + `metaErrorCode=131026`
8. `meta_5xx_retorna_502` — INDISPONIVEL_5XX → 502
9. `circuit_open_retorna_503` — CIRCUIT_OPEN → 503
10. `status_endpoint_retorna_state_cb` — `cbRegistry.find("whatsapp-cloud")` mock CLOSED → 200 + `circuitBreakerState=CLOSED`

---

### `application.yml` (MODIFY — adicionar bloco `whatsapp-cloud`)

**Analog:** `api-whatsapp/src/main/resources/application.yml` linhas 138-167 (bloco `erp-callback`)

**O que adicionar** (espelhar `erp-callback` literalmente, RESEARCH §Code Examples §7):
```yaml
resilience4j:
  circuitbreaker:
    instances:
      erp-callback:
        # ja existente — INALTERADO
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 3
        register-health-indicator: false
      whatsapp-cloud:                    # NOVO Phase 4
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 3
        register-health-indicator: false
  retry:
    instances:
      erp-callback:
        # ja existente — INALTERADO
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2.0
        retry-exceptions:
          - org.springframework.web.client.HttpServerErrorException
          - org.springframework.web.client.ResourceAccessException
          - java.net.SocketTimeoutException
          - java.io.IOException
      whatsapp-cloud:                    # NOVO Phase 4
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2.0
        retry-exceptions:
          - org.springframework.web.client.HttpServerErrorException
          - org.springframework.web.client.ResourceAccessException
          - java.net.SocketTimeoutException
          - java.io.IOException
        # 4xx (HttpClientErrorException) NAO listado — Resilience4j default NAO retenta
        # excecoes nao listadas. Gotcha 03-04: ResourceAccessException CRUCIAL pois
        # RestClient empacota SocketTimeoutException nele.
```

---

### `application-test.yml` (MODIFY — adicionar bloco `whatsapp-cloud` com janelas curtas)

**Analog:** `api-whatsapp/src/test/resources/application-test.yml` linhas 90-110 (`erp-callback` test profile)

**O que adicionar:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      erp-callback:
        # ja existente
      whatsapp-cloud:                    # NOVO Phase 4
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 1s
        permitted-number-of-calls-in-half-open-state: 3
  retry:
    instances:
      erp-callback:
        # ja existente
      whatsapp-cloud:                    # NOVO Phase 4
        max-attempts: 3
        wait-duration: 50ms              # tests rapidos (igual erp-callback test profile)
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2.0
        retry-exceptions:
          - org.springframework.web.client.HttpServerErrorException
          - org.springframework.web.client.ResourceAccessException
          - java.net.SocketTimeoutException
          - java.io.IOException
```

---

### `lib-shared/.../ErrorResponse.java` (MODIFY — adicionar `codigo` + `metaErrorCode`)

**Analog:** o proprio arquivo (`lib-shared/src/main/java/br/com/erpkit/shared/dto/ErrorResponse.java`).

**O que adicionar** (mudanca compativel — campos opcionais nullable):
```java
// ADICIONAR campos:
private String codigo;          // ex: "JANELA_24H_FECHADA", "META_ERROR", "CIRCUIT_OPEN"
private Integer metaErrorCode;  // ex: 131026 (Meta error code numerico, opcional)

// ADICIONAR getters/setters:
public String getCodigo() { return codigo; }
public void setCodigo(String codigo) { this.codigo = codigo; }
public Integer getMetaErrorCode() { return metaErrorCode; }
public void setMetaErrorCode(Integer metaErrorCode) { this.metaErrorCode = metaErrorCode; }
```

**Por que e compativel:**
- Jackson serializa null como null ou omite (depende de `@JsonInclude` global) — outros modulos (api-email/api-storage/api-consultas) que NAO setam `codigo` veem JSON identico ao atual
- Field opcional — sem default value diferente de null
- Sem breaking change na assinatura do construtor (campos novos via setter)

---

### `lib-shared/.../GlobalExceptionHandler.java` (opcional MODIFY — propagar codigo + metaErrorCode)

**Analog:** o proprio arquivo (`lib-shared/src/main/java/br/com/erpkit/shared/exception/GlobalExceptionHandler.java`).

**O que adicionar** (linha 17-24 — handler de ModuloException):
```java
@ExceptionHandler(ModuloException.class)
public ResponseEntity<ErrorResponse> handleModuloException(ModuloException ex) {
    ErrorResponse error = new ErrorResponse(
            ex.getStatus().value(),
            ex.getStatus().getReasonPhrase(),
            ex.getMessage()
    );
    // NOVO: propagar codigo + metaErrorCode quando subclasses carregam estado
    if (ex instanceof br.com.erpkit.whatsapp.exception.JanelaConversaFechadaException jcf) {
        error.setCodigo(jcf.getCodigo());
    } else if (ex instanceof br.com.erpkit.whatsapp.exception.MetaApiException mae) {
        error.setCodigo(mae.getCodigo());
        error.setMetaErrorCode(mae.getMetaErrorCode());
    }
    return ResponseEntity.status(ex.getStatus()).body(error);
}
```

**Trade-off:** lib-shared importando `br.com.erpkit.whatsapp.exception.*` cria dependencia ascendente lib-shared → api-whatsapp. **Alternativa preferida:** usar interface `CodigoCarrier { String getCodigo(); }` em lib-shared; cada exception module-side implementa. Planner decide.

---

## Shared Patterns

### Authentication / API Key
**Source:** `lib-shared/src/main/java/br/com/erpkit/shared/security/ApiKeyFilter.java` (Phase 1 SecurityConfig ja registra para `/api/whatsapp/*`)
**Apply to:** `WhatsAppController` (todos os 5 endpoints)
**Detalhe:** Phase 4 NAO modifica SecurityConfig — `/api/whatsapp/*` herda exigencia de `X-API-Key` header da configuracao Phase 1. Webhook publico (`/webhook/*`) e excluido por HMAC; `/api/whatsapp/*` e protegido por API key.

### Error Handling
**Source:** `lib-shared/src/main/java/br/com/erpkit/shared/exception/GlobalExceptionHandler.java` (linhas 16-41)
**Apply to:** Todas as exceptions lancadas por `WhatsAppCloudClient`, aspect, controller
**Pattern:**
- `JanelaConversaFechadaException extends ModuloException(409)` — automaticamente capturado por `handleModuloException`, mapeado para HTTP 409 + ErrorResponse
- `MetaApiException extends ModuloException(varia per Tipo)` — automaticamente capturado, mapeado para 422/502/504/503 conforme `tipo`
- `MethodArgumentNotValidException` (Bean Validation) — automaticamente capturado por `handleValidation`, mapeado para 400 + `campos` map (ex: `{telefone: "telefone obrigatorio"}`)
- Generic `Exception` — fallback 500 (nunca deveria acontecer em runtime do WhatsAppCloudClient — todo path passa por fallbackMethod)

### Logging
**Source:** Pattern monorepo (`EmailService.java`, `IdempotencyService.java:40`, `ErpCallbackClient.java:59`)
**Apply to:** Todos os services + aspect + controller
**Pattern:**
- `private static final Logger log = LoggerFactory.getLogger(ClassName.class);`
- Format `log.info("ação: campo1={} campo2={}", val1, val2);` (PT-BR identifiers)
- **NUNCA** `log.error("...", t)` (segundo arg = stack trace) ou `log.error("Erro: {}", t)` (passa exception inteira) — apenas `t.getMessage()` no fallback (PITFALLS C-09 — Bearer pode vazar em stack trace de driver HTTP)
- **NUNCA** logar `bytes` direto — apenas `hash`, `mime`, `size`, `filename`
- **NUNCA** logar `accessToken` — `WhatsAppProperties.toString()` ja mascara como `[REDACTED]`

### Validation
**Source:** Jakarta Bean Validation 3.0 + `@Valid` em controller (pattern monorepo `EmailController.criar` linha 33; RESEARCH §Code Examples §6)
**Apply to:** Todos os 4 POST do `WhatsAppController` (NAO `/status` — sem body)
**Pattern:**
- `@Valid @RequestBody DtoRequest req` no metodo do controller
- DTO record com `@NotBlank`, `@Pattern`, `@Size`, `@AssertTrue`, `@Valid` (em listas aninhadas)
- `MethodArgumentNotValidException` capturado por `GlobalExceptionHandler.handleValidation` → 400 + `campos` map

### Bearer Token Per-Request (PITFALLS C-09 / C-14)
**Source:** `service/ErpCallbackClient.java` (Phase 3 — pattern locked) e `service/MetaMediaClient.java:79-80` (Phase 3 — Bearer per-request explicito)
**Apply to:** Todos os metodos publicos + privados (`uploadMedia`) de `WhatsAppCloudClient` que chamam Cloud API
**Pattern:**
```java
restClient.post()
    .uri(...)
    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken()) // explicito per-request
    ...
```
**NAO usar** `RestClient.builder().defaultHeader("Authorization", ...)` — token vaza facilmente em interceptor mal configurado; auditoria visual pede header explicito por chamada.

### Save+Catch DataIntegrityViolation Race Protection
**Source:** `service/IdempotencyService.java:62-75` (Phase 2) + `service/ClienteZapService.java:65-79` (Phase 2)
**Apply to:** `MediaCacheService.registrarUpload`
**Pattern:** save → try/catch `DataIntegrityViolationException` → silenciar (PK e o gate atomico portavel H2/PostgreSQL)

### Native Query Skip JPA L1 Cache
**Source:** `repository/ClienteZapRepository.java:44-50` (Phase 2 — `atualizarUltimaMensagemEm` UPDATE; pattern reusable para SELECT)
**Apply to:** `ClienteZapRepository.buscarUltimaMensagemEm` (NOVO metodo Phase 4)
**Pattern:** `@Query(value="SELECT ... FROM whatsapp.tabela WHERE col = :param", nativeQuery=true)` retornando `Optional<Instant>`. Pula JPA L1 cache — committed read fresco (PITFALLS C-01).

### WireMock Test Pattern
**Source:** `service/ErpCallbackClientTest.java` (Phase 3 — pattern empiricamente validado)
**Apply to:** `WhatsAppCloudClientTest`, `JanelaEnforcementAspectTest`
**Pattern:**
- `@SpringBootTest(classes = WhatsAppApplication.class)` (NAO sem qualifier)
- `@ActiveProfiles("test")`
- `@BeforeAll` static + `WireMockServer` com `dynamicPort()`
- `@DynamicPropertySource` registra URL WireMock como `app.modulos.whatsapp.metaApiBaseUrl`
- `@BeforeEach` resetAll + `cbRegistry.find("whatsapp-cloud").ifPresent(CircuitBreaker::reset)` (Risk A3 — bean Singleton state pollution)
- ScenarioState para 5xx/5xx/200 sequencia (RESEARCH §test 5xx_recupera)
- `wireMock.verify(N, postRequestedFor(...))` para counter assertion (gate de AOP funcionando)

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `aspect/JanelaProtegida.java` | annotation marker | n/a | Primeira annotation customizada do projeto. Pattern direto Java standard (`@Target` + `@Retention(RUNTIME)`). |
| `aspect/JanelaEnforcementAspect.java` | aspect / cross-cutting | request-response | Primeiro aspect customizado do projeto (Resilience4j AOP infra existe — mas e injetado pela starter, nao escrito a mao). Pattern documentado em RESEARCH §Pattern 2 + Resilience4j issue #2383. **Nao usar codigo de outro modulo do monorepo** — verificacao com Glob nao encontrou `@Aspect` customizado em api-email, api-storage, api-consultas, lib-shared. |
| `dto/EnviarDocumentoRequest.java` (parcial) | DTO request com `mediaBase64` | n/a | DTOs de request com body base64 nao existem ainda no codebase (`ComandoCallbackDTO` e OUTPUT, nao input via @Valid). Pattern documentado em RESEARCH §Code Examples §6 + D-01 locked. |

---

## Metadata

**Analog search scope:**
- `api-whatsapp/src/main/java/br/com/erpkit/whatsapp/**/*.java` (todas as classes Phase 1-3)
- `api-whatsapp/src/test/java/br/com/erpkit/whatsapp/**/*.java` (test patterns Phase 1-3)
- `lib-shared/src/main/java/br/com/erpkit/shared/**/*.java` (cross-cutting + ModuloException + ErrorResponse)
- `api-email/src/main/java/br/com/erpkit/email/controller/EmailController.java` (controller convention monorepo)
- `api-whatsapp/src/main/resources/application.yml` (Resilience4j config layout)
- `api-whatsapp/src/test/resources/application-test.yml` (test profile layout)

**Files scanned:** ~45 arquivos Java + 2 yml
**Pattern extraction date:** 2026-05-05
**Confidence:** HIGH — todos os patterns sao reaproveitamento direto de Phase 1-3, ja empiricamente validados (152 tests verde). 1 area (`@Aspect` customizado) sem precedente interno mas com documentacao Resilience4j explicita (issue #2383) + plano de validacao empirica via test counter==1.
