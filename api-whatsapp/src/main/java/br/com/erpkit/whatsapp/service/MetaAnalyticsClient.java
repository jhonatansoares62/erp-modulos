package br.com.erpkit.whatsapp.service;

import br.com.erpkit.shared.exception.ModuloException;
import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.dto.CustoResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cliente dos analytics de custo da Meta (Graph API {@code pricing_analytics}) — §11.
 * Fonte AUTORITATIVA de custo por categoria + free/billable (o que a agregacao local
 * de {@code mensagens_log} nao da). Read-only, custo zero de Meta.
 *
 * <p><b>Sintaxe critica</b> (descoberta empirica): o campo exige
 * {@code metric_types([...])} + {@code dimensions([...])}; sem eles a Graph API
 * responde so o {@code id} do WABA (sem dados). O token de envio ja tem o escopo
 * {@code whatsapp_business_management} necessario.
 */
@Service
public class MetaAnalyticsClient {

    private static final Logger log = LoggerFactory.getLogger(MetaAnalyticsClient.class);
    private static final ObjectMapper OM = new ObjectMapper();
    private static final String TIPO_REGULAR = "REGULAR";

    private final WhatsAppProperties properties;
    private final RestClient restClient;

    public MetaAnalyticsClient(WhatsAppProperties properties,
                               @Value("${spring.http.client.connect-timeout:5s}") Duration connectTimeout,
                               @Value("${spring.http.client.read-timeout:15s}") Duration readTimeout) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeout.toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getMetaApiBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * Consulta {@code pricing_analytics} do WABA no periodo e agrega em {@link CustoResponse}.
     *
     * @param granularity DAILY | HALF_HOUR | MONTHLY
     */
    public CustoResponse custo(Instant de, Instant ate, String granularity) {
        String waba = properties.getWabaId();
        if (waba == null || waba.isBlank()) {
            throw new ModuloException("WABA_ID nao configurado (WHATSAPP_WABA_ID) — custo indisponivel",
                    HttpStatus.PRECONDITION_FAILED);
        }
        String fields = "pricing_analytics.start(" + de.getEpochSecond() + ").end(" + ate.getEpochSecond()
                + ").granularity(" + granularity + ").metric_types([\"COST\",\"VOLUME\"])"
                + ".dimensions([\"PRICING_CATEGORY\",\"PRICING_TYPE\"])";
        String body;
        try {
            body = restClient.get()
                    .uri(b -> b.path("/" + waba).queryParam("fields", fields).build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("Falha ao consultar pricing_analytics do WABA {}: {}", waba, e.getMessage());
            throw new ModuloException("Falha ao consultar custo na Meta: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY);
        }
        return parseCusto(body, de, ate);
    }

    /** Agrega {@code pricing_analytics.data[].data_points[]} em {@link CustoResponse}. Package-private p/ teste. */
    static CustoResponse parseCusto(String body, Instant de, Instant ate) {
        long volumeTotal = 0;
        long faturavel = 0;
        long gratis = 0;
        BigDecimal custoTotal = BigDecimal.ZERO;
        Map<String, Long> volCat = new LinkedHashMap<>();
        Map<String, BigDecimal> custoCat = new LinkedHashMap<>();
        Map<String, Long> volTipo = new LinkedHashMap<>();
        try {
            JsonNode data = OM.readTree(body == null ? "{}" : body).path("pricing_analytics").path("data");
            for (JsonNode bloco : data) {
                for (JsonNode p : bloco.path("data_points")) {
                    long vol = p.path("volume").asLong(0);
                    BigDecimal cost = p.path("cost").decimalValue();
                    String cat = p.path("pricing_category").asText("DESCONHECIDA");
                    String tipo = p.path("pricing_type").asText("DESCONHECIDO");
                    volumeTotal += vol;
                    custoTotal = custoTotal.add(cost);
                    volCat.merge(cat, vol, Long::sum);
                    custoCat.merge(cat, cost, BigDecimal::add);
                    volTipo.merge(tipo, vol, Long::sum);
                    if (TIPO_REGULAR.equals(tipo)) faturavel += vol; else gratis += vol;
                }
            }
        } catch (Exception e) {
            throw new ModuloException("Falha ao interpretar pricing_analytics: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY);
        }
        return new CustoResponse(de.toString(), ate.toString(), volumeTotal, custoTotal,
                faturavel, gratis, volCat, custoCat, volTipo);
    }
}
