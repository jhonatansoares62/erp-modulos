package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.aspect.JanelaProtegida;
import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.dto.BotaoDto;
import br.com.erpkit.whatsapp.dto.EnvioResponse;
import br.com.erpkit.whatsapp.dto.SecaoDto;
import br.com.erpkit.whatsapp.exception.MetaApiException;
import br.com.erpkit.whatsapp.exception.MetaApiException.Tipo;
import br.com.erpkit.whatsapp.model.Direcao;
import br.com.erpkit.whatsapp.model.MensagemLog;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import br.com.erpkit.whatsapp.util.TelefoneBR;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cliente HTTP para Meta Cloud API v22.0 (OUT-01..05 + OUT-08..10).
 *
 * <p><b>Trava custo zero #1 (D9 PROJECT.md, OUT-05): este cliente NAO expoe
 * {@code enviarTemplate(...)}.</b> Templates geram custo Meta — proibido por
 * design. Gate de grep + reflection test em {@code WhatsAppCloudClientTest}
 * garantem regressao impossivel.
 *
 * <p><b>Trava custo zero #2 (D-03 + OUT-06 + OUT-07):</b> cada metodo publico
 * carrega {@link JanelaProtegida} — aspect 04-02 com {@code @Order(HIGHEST_PRECEDENCE)}
 * verifica janela 24h ANTES de qualquer byte ir para Meta. Hard 409 com codigo
 * {@code JANELA_24H_FECHADA} em violacao.
 *
 * <p><b>Resilience4j (D-02 + OUT-10):</b> {@code @CircuitBreaker(name="whatsapp-cloud")}
 * + {@code @Retry(name="whatsapp-cloud", fallbackMethod=...)}. Config em
 * application.yml/whatsapp-cloud (sliding-window=10, threshold=50, retry max=3
 * exponencial 2.0). 4xx categoricos NAO retentam (whitelist NAO inclui
 * HttpClientErrorException). 5xx + timeout retentam.
 *
 * <p><b>fallbackMethod localizado em {@code @Retry} (NAO em {@code @CircuitBreaker}):</b>
 * gotcha empiricamente descoberto em Phase 3 03-04. Quando ambas annotations
 * coexistem com fallback no INNER (CircuitBreaker), o fallback inner converte
 * excecao em retorno void de sucesso ANTES da OUTER (Retry) ver o erro — Retry
 * recebe "sucesso" e nao retenta. Solucao: por fallbackMethod no @Retry. CB
 * inner continua contabilizando attempts no sliding-window.
 *
 * <p><b>Bearer header per-request (PITFALLS C-09 + C-14):</b> cada chamada
 * RestClient seta explicitamente {@code .header(AUTHORIZATION, "Bearer " + token)}.
 * NAO defaultHeader global (auditavel visualmente, alinhado D-04 Phase 3).
 * Bearer NUNCA em query param — gate empirico via WireMock
 * {@code getAllServeEvents().forEach(...)} no teste.
 *
 * <p><b>Persistencia outbound (OUT-09):</b> apos sucesso da chamada Meta, persiste
 * em {@code mensagens_log} com {@code direcao=out} + wamid retornado. Falha de
 * persistencia (DataIntegrityViolationException ou outra) NAO suprime — propaga
 * (mensagem foi enviada ao cliente; perder o log e operacional).
 */
@Service
public class WhatsAppCloudClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudClient.class);
    private static final ObjectMapper OM = new ObjectMapper();

    private final RestClient restClient;
    private final WhatsAppProperties properties;
    private final MediaCacheService mediaCacheService;
    private final MensagemLogRepository mensagemLogRepository;

    public WhatsAppCloudClient(WhatsAppProperties properties,
                               MediaCacheService mediaCacheService,
                               MensagemLogRepository mensagemLogRepository,
                               @Value("${spring.http.client.connect-timeout:5s}") Duration connectTimeout,
                               @Value("${spring.http.client.read-timeout:30s}") Duration readTimeout) {
        this.properties = properties;
        this.mediaCacheService = mediaCacheService;
        this.mensagemLogRepository = mensagemLogRepository;
        // Pattern verbatim Phase 3 ErpCallbackClient: SimpleClientHttpRequestFactory
        // com timeouts globais via spring.http.client.* (application.yml + application-test.yml).
        // SimpleClientHttpRequestFactory usa HttpURLConnection (HTTP/1.1 default) — evita
        // a issue HTTP/2 RST_STREAM contra WireMock plain HTTP (WAVE-1-LEARNING).
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeout.toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getMetaApiBaseUrl())
                .requestFactory(factory)
                .build();
    }

    // =========================================================================
    // 4 metodos publicos — todos com triple annotation. NAO existe enviarTemplate.
    // =========================================================================

    @JanelaProtegida
    @CircuitBreaker(name = "whatsapp-cloud")
    @Retry(name = "whatsapp-cloud", fallbackMethod = "fallbackEnviarTexto")
    public EnvioResponse enviarTexto(String telefone, String texto) {
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", TelefoneBR.paraEnvio(telefone),
                "type", "text",
                "text", Map.of("body", texto)
        );
        Map response = postMessages(body);
        String wamid = extrairWamid(response);
        mensagemLogRepository.save(new MensagemLog(wamid, telefone, Direcao.out, "text", texto, null));
        log.info("WhatsApp Cloud enviarTexto ok: telefone={} wamid={}", telefone, wamid);
        return new EnvioResponse(wamid);
    }

    @JanelaProtegida
    @CircuitBreaker(name = "whatsapp-cloud")
    @Retry(name = "whatsapp-cloud", fallbackMethod = "fallbackEnviarDocumento")
    public EnvioResponse enviarDocumento(String telefone, byte[] bytes, String filename,
                                          String mimeType, String caption) {
        // 1. Cache check (OUT-08 + 04-03)
        Optional<String> cached = mediaCacheService.buscarMediaId(bytes);
        String mediaId = cached.orElseGet(() -> {
            // 2. Miss -> upload + register (multipart interno)
            String novo = uploadMedia(bytes, mimeType, filename);
            mediaCacheService.registrarUpload(bytes, novo);
            return novo;
        });

        // 3. Send referenciando media_id
        Map<String, Object> documento = new LinkedHashMap<>();
        documento.put("id", mediaId);
        documento.put("filename", filename);
        if (caption != null && !caption.isBlank()) {
            documento.put("caption", caption);
        }
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", TelefoneBR.paraEnvio(telefone),
                "type", "document",
                "document", documento
        );
        Map response = postMessages(body);
        String wamid = extrairWamid(response);
        mensagemLogRepository.save(new MensagemLog(wamid, telefone, Direcao.out, "document", caption, mediaId));
        log.info("WhatsApp Cloud enviarDocumento ok: telefone={} wamid={} mediaId={}", telefone, wamid, mediaId);
        return new EnvioResponse(wamid);
    }

    @JanelaProtegida
    @CircuitBreaker(name = "whatsapp-cloud")
    @Retry(name = "whatsapp-cloud", fallbackMethod = "fallbackEnviarBotoes")
    public EnvioResponse enviarBotoes(String telefone, String texto, List<BotaoDto> botoes) {
        List<Map<String, Object>> buttons = new ArrayList<>();
        for (BotaoDto b : botoes) {
            buttons.add(Map.of("type", "reply", "reply", Map.of("id", b.id(), "title", b.title())));
        }
        Map<String, Object> interactive = Map.of(
                "type", "button",
                "body", Map.of("text", texto),
                "action", Map.of("buttons", buttons)
        );
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", TelefoneBR.paraEnvio(telefone),
                "type", "interactive",
                "interactive", interactive
        );
        Map response = postMessages(body);
        String wamid = extrairWamid(response);
        mensagemLogRepository.save(new MensagemLog(wamid, telefone, Direcao.out, "interactive_button", texto, null));
        log.info("WhatsApp Cloud enviarBotoes ok: telefone={} wamid={} qtd_botoes={}", telefone, wamid, botoes.size());
        return new EnvioResponse(wamid);
    }

    @JanelaProtegida
    @CircuitBreaker(name = "whatsapp-cloud")
    @Retry(name = "whatsapp-cloud", fallbackMethod = "fallbackEnviarLista")
    public EnvioResponse enviarLista(String telefone, String texto, List<SecaoDto> secoes) {
        List<Map<String, Object>> sections = new ArrayList<>();
        for (SecaoDto s : secoes) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (var item : s.itens()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.id());
                row.put("title", item.title());
                if (item.description() != null && !item.description().isBlank()) {
                    row.put("description", item.description());
                }
                rows.add(row);
            }
            sections.add(Map.of("title", s.titulo(), "rows", rows));
        }
        Map<String, Object> interactive = Map.of(
                "type", "list",
                "body", Map.of("text", texto),
                "action", Map.of("button", "Ver opcoes", "sections", sections)
        );
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", TelefoneBR.paraEnvio(telefone),
                "type", "interactive",
                "interactive", interactive
        );
        Map response = postMessages(body);
        String wamid = extrairWamid(response);
        mensagemLogRepository.save(new MensagemLog(wamid, telefone, Direcao.out, "interactive_list", texto, null));
        log.info("WhatsApp Cloud enviarLista ok: telefone={} wamid={} secoes={}", telefone, wamid, secoes.size());
        return new EnvioResponse(wamid);
    }

    // =========================================================================
    // Multipart upload (PRIVADO — chamado de dentro de enviarDocumento ja
    // protegido por @JanelaProtegida; sem annotation propria).
    // =========================================================================

    private String uploadMedia(byte[] bytes, String mimeType, String filename) {
        ByteArrayResource fileResource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
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

        if (response == null || response.get("id") == null) {
            throw new IllegalStateException("Response do Meta media upload sem 'id'");
        }
        String mediaId = (String) response.get("id");
        log.info("WhatsApp Cloud uploadMedia ok: filename={} mime={} sizeBytes={} mediaId={}",
                filename, mimeType, bytes.length, mediaId);
        return mediaId;
    }

    // =========================================================================
    // Helpers compartilhados
    // =========================================================================

    private Map postMessages(Map<String, Object> body) {
        return restClient.post()
                .uri("/{phoneNumberId}/messages", properties.getPhoneNumberId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private String extrairWamid(Map response) {
        if (response == null) {
            throw new IllegalStateException("Response do Meta /messages e null");
        }
        Object messages = response.get("messages");
        if (!(messages instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalStateException("Response do Meta sem messages[]: " + response);
        }
        Map<String, Object> first = (Map<String, Object>) list.get(0);
        Object id = first.get("id");
        if (!(id instanceof String wamid) || wamid.isBlank()) {
            throw new IllegalStateException("Response do Meta sem messages[0].id: " + response);
        }
        return wamid;
    }

    // =========================================================================
    // Fallbacks — convertem Throwable em MetaApiException (D-02). Divergencia
    // consciente vs ErpCallbackClient (que suprime — fire-and-forget; outbound
    // do controller PRECISA propagar erro ao ERP).
    // =========================================================================

    @SuppressWarnings("unused") // referenciado por fallbackMethod = "fallbackEnviarTexto"
    private EnvioResponse fallbackEnviarTexto(String telefone, String texto, Throwable t) {
        log.error("WhatsApp Cloud enviarTexto falhou apos retry+CB: telefone={}: {}", telefone, t.getMessage());
        throw classificar(t);
    }

    @SuppressWarnings("unused")
    private EnvioResponse fallbackEnviarDocumento(String telefone, byte[] bytes, String filename,
                                                   String mimeType, String caption, Throwable t) {
        log.error("WhatsApp Cloud enviarDocumento falhou apos retry+CB: telefone={} filename={}: {}",
                telefone, filename, t.getMessage());
        throw classificar(t);
    }

    @SuppressWarnings("unused")
    private EnvioResponse fallbackEnviarBotoes(String telefone, String texto, List<BotaoDto> botoes, Throwable t) {
        log.error("WhatsApp Cloud enviarBotoes falhou apos retry+CB: telefone={}: {}", telefone, t.getMessage());
        throw classificar(t);
    }

    @SuppressWarnings("unused")
    private EnvioResponse fallbackEnviarLista(String telefone, String texto, List<SecaoDto> secoes, Throwable t) {
        log.error("WhatsApp Cloud enviarLista falhou apos retry+CB: telefone={}: {}", telefone, t.getMessage());
        throw classificar(t);
    }

    /**
     * Converte Throwable de Resilience4j fallback em {@link MetaApiException} com
     * Tipo apropriado (D-02 mapping table). Extrai metaErrorCode do response body
     * Meta quando disponivel (best-effort).
     */
    private MetaApiException classificar(Throwable t) {
        if (t instanceof CallNotPermittedException) {
            return new MetaApiException(Tipo.CIRCUIT_OPEN, null, "Circuit breaker whatsapp-cloud aberto");
        }
        if (t instanceof HttpClientErrorException he) {
            Integer metaCode = extrairMetaErrorCode(he.getResponseBodyAsString());
            return new MetaApiException(Tipo.CATEGORIA_4XX, metaCode,
                    "Meta retornou " + he.getStatusCode() + ": " + he.getMessage());
        }
        if (t instanceof HttpServerErrorException he) {
            Integer metaCode = extrairMetaErrorCode(he.getResponseBodyAsString());
            return new MetaApiException(Tipo.INDISPONIVEL_5XX, metaCode,
                    "Meta indisponivel " + he.getStatusCode() + ": " + he.getMessage());
        }
        if (t instanceof ResourceAccessException) {
            return new MetaApiException(Tipo.TIMEOUT, null, "Timeout ao chamar Meta: " + t.getMessage());
        }
        // Fallback default: 5xx genuino mas nao classificado
        return new MetaApiException(Tipo.INDISPONIVEL_5XX, null,
                "Meta indisponivel (erro nao classificado): " + t.getMessage());
    }

    /**
     * Extrai {@code error.code} do response body Meta (best-effort).
     * Body Meta error: {@code {"error":{"message":"...","type":"...","code":131026,...}}}.
     */
    private Integer extrairMetaErrorCode(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OM.readTree(responseBody);
            JsonNode codeNode = root.path("error").path("code");
            return codeNode.isInt() ? codeNode.intValue() : null;
        } catch (Exception e) {
            log.debug("Nao foi possivel extrair meta_error_code do body: {}", e.getMessage());
            return null;
        }
    }
}
