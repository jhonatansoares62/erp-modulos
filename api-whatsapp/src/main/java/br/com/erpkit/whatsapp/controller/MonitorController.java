package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.dto.BotaoDto;
import br.com.erpkit.whatsapp.dto.EnvioResponse;
import br.com.erpkit.whatsapp.exception.JanelaConversaFechadaException;
import br.com.erpkit.whatsapp.exception.MetaApiException;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import br.com.erpkit.whatsapp.service.WhatsAppCloudClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Console de monitoramento + TESTE + DIAGNOSTICO (dev/meta apenas) para a pagina
 * {@code /monitor.html}. Um lugar unico para acompanhar mensagens ao vivo, enviar
 * testes e diagnosticar problemas (token Meta, circuit breaker, conexao com o ERP).
 *
 * <p>Ativo somente nos profiles {@code dev} e {@code meta} ({@link Profile}) — nao
 * entra no runtime de producao. Path {@code /monitor} liberado no {@code ApiKeyFilter}.
 */
@RestController
@Profile({"dev", "meta"})
@RequestMapping("/monitor")
public class MonitorController {

    private final MensagemLogRepository repository;
    private final CircuitBreakerRegistry cbRegistry;
    private final WhatsAppProperties properties;
    private final WhatsAppCloudClient cloudClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final RestClient http = RestClient.create();

    public MonitorController(MensagemLogRepository repository,
                             CircuitBreakerRegistry cbRegistry,
                             WhatsAppProperties properties,
                             WhatsAppCloudClient cloudClient,
                             ObjectMapper objectMapper,
                             @Value("${modulo.api-key:}") String apiKey) {
        this.repository = repository;
        this.cbRegistry = cbRegistry;
        this.properties = properties;
        this.cloudClient = cloudClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    // ---- Feed ao vivo -----------------------------------------------------

    @GetMapping("/feed")
    public MonitorFeed feed() {
        String cb = cbRegistry.find("whatsapp-cloud").map(c -> c.getState().name()).orElse("UNKNOWN");
        List<MonitorMensagem> mensagens = repository.findTop200ByOrderByIdDesc().stream()
                .map(m -> new MonitorMensagem(
                        m.getId(),
                        m.getDirecao() == null ? null : m.getDirecao().name(),
                        // numero de RESPOSTA: wa_id exato (com 9) quando houver; senao o
                        // telefone (saidas ja gravam o wa_id ali)
                        m.getWaId() != null ? m.getWaId() : m.getTelefone(),
                        m.getTipo(),
                        m.getConteudo(),
                        m.getCriadoEm() == null ? null : m.getCriadoEm().toEpochMilli()))
                .toList();
        return new MonitorFeed(properties.getPhoneNumberId(), cb, repository.count(), mensagens);
    }

    // ---- Enviar teste (texto ou botoes) -----------------------------------

    @PostMapping("/enviar")
    public EnvioResultado enviar(@RequestBody EnviarTeste req) {
        try {
            EnvioResponse resp;
            if ("botoes".equalsIgnoreCase(req.tipo())) {
                resp = cloudClient.enviarBotoes(req.telefone(), req.texto(), req.botoes());
            } else {
                resp = cloudClient.enviarTexto(req.telefone(), req.texto());
            }
            return EnvioResultado.ok(resp.wamid());
        } catch (JanelaConversaFechadaException e) {
            return EnvioResultado.erro(e.getCodigo(), null, e.getMessage());
        } catch (MetaApiException e) {
            return EnvioResultado.erro(e.getCodigo(), e.getMetaErrorCode(), e.getMessage());
        } catch (Exception e) {
            return EnvioResultado.erro("ERRO", null,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ---- Diagnostico ------------------------------------------------------

    @GetMapping("/diagnostico")
    public Diagnostico diagnostico() {
        String cb = cbRegistry.find("whatsapp-cloud").map(c -> c.getState().name()).orElse("UNKNOWN");
        return new Diagnostico(
                properties.getPhoneNumberId(),
                properties.getMetaApiBaseUrl(),
                properties.getErpCallbackUrl(),
                apiKey != null && !apiKey.isBlank(),
                cb,
                checarMeta(),
                checarErp());
    }

    /**
     * Valida o token + numero chamando o Graph API (GET do phone number). Le como
     * String porque o Graph API as vezes responde {@code text/javascript} (sem
     * conversor JSON automatico) e parseia com o {@link ObjectMapper}.
     */
    private Check checarMeta() {
        try {
            String body = http.get()
                    .uri(properties.getMetaApiBaseUrl() + "/" + properties.getPhoneNumberId()
                            + "?fields=display_phone_number,verified_name")
                    .header("Authorization", "Bearer " + properties.getAccessToken())
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(body == null ? "{}" : body);
            if (node.has("error")) {
                return new Check(false, "Meta: " + node.path("error").path("message").asText());
            }
            return new Check(true, "Token OK — " + node.path("verified_name").asText("?")
                    + " (" + node.path("display_phone_number").asText("?") + ")");
        } catch (Exception e) {
            return new Check(false, "Token/numero: " + resumo(e));
        }
    }

    /** Verifica se o ERP (erpCallbackUrl) esta alcancavel (qualquer resposta HTTP conta). */
    private Check checarErp() {
        try {
            RestClient.create().get()
                    .uri(properties.getErpCallbackUrl())
                    .retrieve()
                    .toBodilessEntity();
            return new Check(true, "ERP alcancavel em " + properties.getErpCallbackUrl());
        } catch (org.springframework.web.client.RestClientResponseException e) {
            // recebeu resposta HTTP (ex: 404) — esta alcancavel
            return new Check(true, "ERP alcancavel (HTTP " + e.getStatusCode().value() + ")");
        } catch (Exception e) {
            return new Check(false, "ERP inalcancavel: " + resumo(e));
        }
    }

    private static String resumo(Exception e) {
        String m = e.getMessage();
        if (m == null) return e.getClass().getSimpleName();
        return m.length() > 180 ? m.substring(0, 180) + "…" : m;
    }

    // ---- Records ----------------------------------------------------------

    public record MonitorFeed(String phoneNumberId, String circuitBreakerState, long total,
                              List<MonitorMensagem> mensagens) { }

    public record MonitorMensagem(Long id, String direcao, String telefone, String tipo,
                                  String conteudo, Long criadoEm) { }

    public record EnviarTeste(String telefone, String tipo, String texto, List<BotaoDto> botoes) { }

    public record EnvioResultado(boolean ok, String wamid, String codigo, Integer metaErrorCode, String mensagem) {
        static EnvioResultado ok(String wamid) { return new EnvioResultado(true, wamid, null, null, null); }
        static EnvioResultado erro(String codigo, Integer metaErrorCode, String mensagem) {
            return new EnvioResultado(false, null, codigo, metaErrorCode, mensagem);
        }
    }

    public record Diagnostico(String phoneNumberId, String metaApiBaseUrl, String erpCallbackUrl,
                              boolean apiKeyConfigurada, String circuitBreakerState,
                              Check meta, Check erp) { }

    public record Check(boolean ok, String detalhe) { }
}
