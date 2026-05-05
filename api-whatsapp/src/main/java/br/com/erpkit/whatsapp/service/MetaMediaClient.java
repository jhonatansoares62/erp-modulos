package br.com.erpkit.whatsapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.dto.MediaMetadataDTO;
import br.com.erpkit.whatsapp.dto.MetaMediaResultado;

/**
 * Cliente HTTP para download de media de ENTRADA do Meta Cloud API (D-04 + ROU-05).
 *
 * <p>Sequencia 2-step da Graph API:
 * <ol>
 *   <li>{@code GET {metaApiBaseUrl}/{mediaId}} -&gt; {@link MediaMetadataDTO}
 *       (URL temporaria assinada + mime + filename + sha256)</li>
 *   <li>{@code GET {metadata.url}} -&gt; {@code byte[]} (URL absoluta tipo
 *       {@code lookaside.fbsbx.com} — RestClient segue automaticamente)</li>
 * </ol>
 *
 * <p><b>Bearer obrigatorio em ambas requests (PITFALLS C-14):</b> token NUNCA
 * em query param. Header {@code Authorization: Bearer {accessToken}} em cada
 * GET — query param vazaria em logs/proxies/access logs do CDN do Meta.
 *
 * <p><b>404 graceful (PITFALLS C-08):</b> URL Meta expira em 5 min. Se a queue
 * async atrasar apos o ack 200 e a URL ja expirou no momento do download
 * (qualquer step), retorna {@link Optional#empty()} + {@code log.warn} — a
 * mensagem permanece persistida (Phase 2) sem bytes; ERP recebe callback sem
 * {@code mediaBase64}.
 *
 * <p><b>Sem Resilience4j por design:</b> primeira acao apos ack precisa ser
 * RAPIDA (5 min de janela). Circuit breaker / retry adicionariam latencia
 * desnecessaria — falha = mensagem persistida sem bytes; nao critico. Erros
 * 5xx escapam como {@code HttpServerErrorException}; o listener async (Wave 5)
 * tem try/catch generico em volta da chamada.
 *
 * <p><b>Defesa em profundidade:</b> metadata {@code null}, URL ausente, ou
 * bytes vazios -&gt; {@link Optional#empty()} + log.warn (em vez de propagar
 * NPE ou retornar Resultado parcial).
 */
@Service
public class MetaMediaClient {

    private static final Logger log = LoggerFactory.getLogger(MetaMediaClient.class);

    private final WhatsAppProperties properties;
    private final RestClient restClient;

    public MetaMediaClient(WhatsAppProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getMetaApiBaseUrl())
                .build();
    }

    /**
     * Baixa media de entrada do Meta. Retorna {@link Optional#empty()} se URL
     * expirada ou metadata invalida; nunca propaga
     * {@link HttpClientErrorException.NotFound}. Erros 5xx escapam (sem retry).
     *
     * @param mediaId ID retornado em {@code messages[].image|document|audio|video.id}
     *                do webhook (Phase 2 parser extrai para
     *                {@code MensagemPersistidaEvent.mediaId})
     * @return {@link Optional} com bytes + metadata; empty se 404 em qualquer
     *         step ou metadata defeituosa
     */
    public Optional<MetaMediaResultado> baixar(String mediaId) {
        // Step 1: metadata (URL temporaria assinada + mime + filename)
        MediaMetadataDTO metadata;
        try {
            metadata = restClient.get()
                    .uri("/{id}", mediaId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                    .retrieve()
                    .body(MediaMetadataDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Meta media metadata 404 — URL expirada (5min PITFALLS C-08): mediaId={}", mediaId);
            return Optional.empty();
        }
        if (metadata == null || metadata.getUrl() == null) {
            log.warn("Meta media metadata sem URL: mediaId={}", mediaId);
            return Optional.empty();
        }

        // Step 2: bytes binarios. URL e absoluta (lookaside.fbsbx.com) — RestClient
        // segue. Bearer continua obrigatorio no header da CDN do Meta (PITFALLS
        // C-14: NUNCA em query param mesmo aqui).
        byte[] bytes;
        try {
            bytes = restClient.get()
                    .uri(metadata.getUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                    .retrieve()
                    .body(byte[].class);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Meta media bytes 404 — URL expirada antes do step 2: mediaId={}", mediaId);
            return Optional.empty();
        }
        if (bytes == null || bytes.length == 0) {
            log.warn("Meta media bytes vazio: mediaId={}", mediaId);
            return Optional.empty();
        }

        return Optional.of(new MetaMediaResultado(bytes, metadata.getMimeType(), metadata.getFilename()));
    }
}
