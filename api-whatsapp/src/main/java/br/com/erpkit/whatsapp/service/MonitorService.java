package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.dto.DiagnosticoCheck;
import br.com.erpkit.whatsapp.dto.DiagnosticoResponse;
import br.com.erpkit.whatsapp.dto.FeedRecentesResponse;
import br.com.erpkit.whatsapp.dto.MensagemRecenteResponse;
import br.com.erpkit.whatsapp.model.MensagemLog;
import br.com.erpkit.whatsapp.repository.MensagemLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Logica de monitoramento (feed + diagnostico) compartilhada entre dois consumidores:
 * <ul>
 *   <li>{@code MonitorController} ({@code /monitor/*}, profiles dev/meta) — o painel
 *       {@code monitor.html} do prototipo;</li>
 *   <li>{@code WhatsAppController} ({@code /api/whatsapp/*}, autenticado por Bearer) —
 *       a tela de Testes do app do atendente, valida em PRODUCAO.</li>
 * </ul>
 *
 * <p>Existe para NAO duplicar a logica (montagem do feed, validacao do token Meta na
 * Graph API, alcancabilidade do ERP callback, leitura do circuit breaker) em dois
 * controllers. Sem {@code @Profile} — disponivel em todos os ambientes.
 */
@Service
public class MonitorService {

    /** Teto de seguranca para o {@code limit} do feed (evita varredura grande). */
    public static final int LIMITE_MAXIMO_FEED = 200;
    /** Quantidade padrao de mensagens quando o caller nao informa {@code limit}. */
    public static final int LIMITE_PADRAO_FEED = 50;

    private final MensagemLogRepository repository;
    private final CircuitBreakerRegistry cbRegistry;
    private final WhatsAppProperties properties;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final RestClient http = RestClient.create();

    public MonitorService(MensagemLogRepository repository,
                          CircuitBreakerRegistry cbRegistry,
                          WhatsAppProperties properties,
                          ObjectMapper objectMapper,
                          @Value("${modulo.api-key:}") String apiKey) {
        this.repository = repository;
        this.cbRegistry = cbRegistry;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    // ---- Feed ao vivo -----------------------------------------------------

    /**
     * Feed das ultimas {@code limit} mensagens (entrada + saida), mais recentes primeiro.
     * O {@code limit} e saneado para o intervalo {@code [1, LIMITE_MAXIMO_FEED]}; valores
     * nulos ou &lt;= 0 caem no {@link #LIMITE_PADRAO_FEED}.
     */
    public FeedRecentesResponse feed(Integer limit) {
        int tamanho = sanearLimite(limit);
        String cb = estadoCircuitBreaker();
        List<MensagemRecenteResponse> mensagens = repository
                .findAll(PageRequest.of(0, tamanho, Sort.by(Sort.Direction.DESC, "id")))
                .stream()
                .map(this::mapear)
                .toList();
        return new FeedRecentesResponse(properties.getPhoneNumberId(), cb, repository.count(), mensagens);
    }

    private MensagemRecenteResponse mapear(MensagemLog m) {
        return new MensagemRecenteResponse(
                m.getId(),
                m.getDirecao() == null ? null : m.getDirecao().name(),
                // numero de RESPOSTA: wa_id exato (com 9) quando houver; senao o telefone
                // (saidas ja gravam o wa_id ali)
                m.getWaId() != null ? m.getWaId() : m.getTelefone(),
                m.getTipo(),
                m.getConteudo(),
                m.getCriadoEm() == null ? null : m.getCriadoEm().toEpochMilli());
    }

    private int sanearLimite(Integer limit) {
        if (limit == null || limit <= 0) {
            return LIMITE_PADRAO_FEED;
        }
        return Math.min(limit, LIMITE_MAXIMO_FEED);
    }

    // ---- Diagnostico ------------------------------------------------------

    /**
     * Diagnostico do modulo: valida token/numero Meta (Graph API), alcancabilidade do
     * ERP callback e le o estado do circuit breaker. Nunca lanca — cada checagem vira um
     * {@link DiagnosticoCheck} com {@code ok=false} e o motivo em PT-BR.
     */
    public DiagnosticoResponse diagnostico() {
        return new DiagnosticoResponse(
                properties.getPhoneNumberId(),
                properties.getMetaApiBaseUrl(),
                properties.getErpCallbackUrl(),
                apiKey != null && !apiKey.isBlank(),
                estadoCircuitBreaker(),
                checarMeta(),
                checarErp());
    }

    private String estadoCircuitBreaker() {
        return cbRegistry.find("whatsapp-cloud").map(c -> c.getState().name()).orElse("UNKNOWN");
    }

    /**
     * Valida o token + numero chamando o Graph API (GET do phone number). Le como String
     * porque o Graph API as vezes responde {@code text/javascript} (sem conversor JSON
     * automatico) e parseia com o {@link ObjectMapper}.
     */
    private DiagnosticoCheck checarMeta() {
        try {
            String body = http.get()
                    .uri(properties.getMetaApiBaseUrl() + "/" + properties.getPhoneNumberId()
                            + "?fields=display_phone_number,verified_name")
                    .header("Authorization", "Bearer " + properties.getAccessToken())
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(body == null ? "{}" : body);
            if (node.has("error")) {
                return new DiagnosticoCheck(false, "Meta: " + node.path("error").path("message").asText());
            }
            return new DiagnosticoCheck(true, "Token OK — " + node.path("verified_name").asText("?")
                    + " (" + node.path("display_phone_number").asText("?") + ")");
        } catch (Exception e) {
            return new DiagnosticoCheck(false, "Token/numero: " + resumo(e));
        }
    }

    /** Verifica se o ERP (erpCallbackUrl) esta alcancavel (qualquer resposta HTTP conta). */
    private DiagnosticoCheck checarErp() {
        try {
            RestClient.create().get()
                    .uri(properties.getErpCallbackUrl())
                    .retrieve()
                    .toBodilessEntity();
            return new DiagnosticoCheck(true, "ERP alcancavel em " + properties.getErpCallbackUrl());
        } catch (RestClientResponseException e) {
            // recebeu resposta HTTP (ex: 404) — esta alcancavel
            return new DiagnosticoCheck(true, "ERP alcancavel (HTTP " + e.getStatusCode().value() + ")");
        } catch (Exception e) {
            return new DiagnosticoCheck(false, "ERP inalcancavel: " + resumo(e));
        }
    }

    private static String resumo(Exception e) {
        String m = e.getMessage();
        if (m == null) return e.getClass().getSimpleName();
        return m.length() > 180 ? m.substring(0, 180) + "…" : m;
    }
}
