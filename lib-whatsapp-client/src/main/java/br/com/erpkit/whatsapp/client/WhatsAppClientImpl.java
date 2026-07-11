package br.com.erpkit.whatsapp.client;

import br.com.erpkit.whatsapp.client.dto.BotaoDto;
import br.com.erpkit.whatsapp.client.dto.EnvioResponse;
import br.com.erpkit.whatsapp.client.dto.MetaConfigRequest;
import br.com.erpkit.whatsapp.client.dto.MetaConfigResponse;
import br.com.erpkit.whatsapp.client.dto.ResumoUsoResponse;
import br.com.erpkit.whatsapp.client.dto.SecaoDto;
import br.com.erpkit.whatsapp.client.dto.StatusResponse;
import br.com.erpkit.whatsapp.client.dto.WhatsAppRespostaDto;
import br.com.erpkit.whatsapp.client.exception.WhatsAppException;
import br.com.erpkit.whatsapp.client.exception.WhatsAppIndisponivelException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Implementacao do {@link WhatsAppClient} sobre Spring {@code RestClient} + Resilience4j.
 * Espelha {@code ConsultasClientImpl} (mesma config de circuit breaker/retry, mesmo
 * padrao {@code execute(Supplier)}); diverge apenas ao usar {@code RestClient} (LIB-06,
 * alinhado a Phase 4) em vez de {@code RestTemplate}.
 */
public class WhatsAppClientImpl implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppClientImpl.class);
    private static final String NOME_MODULO = "whatsapp";

    private final WhatsAppProperties props;
    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public WhatsAppClientImpl(WhatsAppProperties props) {
        this.props = props;
        this.restClient = criarRestClient(props);

        var cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        this.circuitBreaker = CircuitBreaker.of(NOME_MODULO, cbConfig);

        var retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(1000, 2.0))
                .retryExceptions(RestClientException.class)
                .ignoreExceptions(HttpClientErrorException.class)
                .build();
        this.retry = Retry.of(NOME_MODULO, retryConfig);
    }

    @Override
    public EnvioResponse enviarTexto(String telefone, String texto) {
        verificarHabilitado();
        var body = new EnviarTextoBody(telefone, texto);
        return execute(() -> postJson("/api/whatsapp/enviar-texto", body, EnvioResponse.class));
    }

    @Override
    public EnvioResponse enviarDocumento(String telefone, byte[] bytes, String filename, String mimeType, String caption) {
        verificarHabilitado();
        String mediaBase64 = Base64.getEncoder().encodeToString(bytes);
        var body = new EnviarDocumentoBody(telefone, mediaBase64, mimeType, filename, caption);
        return execute(() -> postJson("/api/whatsapp/enviar-documento", body, EnvioResponse.class));
    }

    @Override
    public EnvioResponse enviarBotoes(String telefone, String texto, List<BotaoDto> botoes) {
        verificarHabilitado();
        var body = new EnviarBotoesBody(telefone, texto, botoes);
        return execute(() -> postJson("/api/whatsapp/enviar-botoes", body, EnvioResponse.class));
    }

    @Override
    public EnvioResponse enviarLista(String telefone, String texto, List<SecaoDto> secoes) {
        verificarHabilitado();
        var body = new EnviarListaBody(telefone, texto, secoes);
        return execute(() -> postJson("/api/whatsapp/enviar-lista", body, EnvioResponse.class));
    }

    @Override
    public EnvioResponse despachar(String telefone, WhatsAppRespostaDto resposta) {
        if (resposta == null) return null;
        return switch (resposta.tipo()) {
            case TEXTO -> enviarTexto(telefone, resposta.texto());
            case DOCUMENTO -> enviarDocumento(telefone, resposta.documento().bytes(),
                    resposta.documento().filename(), resposta.documento().mimeType(),
                    resposta.documento().caption());
            case BOTOES -> enviarBotoes(telefone, resposta.texto(), resposta.botoes());
            case LISTA -> enviarLista(telefone, resposta.texto(), resposta.secoes());
        };
    }

    @Override
    public StatusResponse status() {
        verificarHabilitado();
        return execute(() -> restClient.get()
                .uri("/api/whatsapp/status")
                .headers(this::aplicarApiKey)
                .retrieve()
                .body(StatusResponse.class));
    }

    @Override
    public MetaConfigResponse obterConfig() {
        verificarHabilitado();
        return execute(() -> restClient.get()
                .uri("/api/whatsapp/config")
                .headers(this::aplicarApiKey)
                .retrieve()
                .body(MetaConfigResponse.class));
    }

    @Override
    public MetaConfigResponse salvarConfig(MetaConfigRequest req) {
        verificarHabilitado();
        // Upsert idempotente no api-whatsapp — seguro sob o @Retry do execute().
        return execute(() -> restClient.put()
                .uri("/api/whatsapp/config")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(this::aplicarApiKey)
                .body(req)
                .retrieve()
                .body(MetaConfigResponse.class));
    }

    @Override
    public ResumoUsoResponse relatorioResumo(String de, String ate) {
        verificarHabilitado();
        return execute(() -> restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/whatsapp/relatorios/resumo");
                    if (de != null && !de.isBlank()) uriBuilder.queryParam("de", de);
                    if (ate != null && !ate.isBlank()) uriBuilder.queryParam("ate", ate);
                    return uriBuilder.build();
                })
                .headers(this::aplicarApiKey)
                .retrieve()
                .body(ResumoUsoResponse.class));
    }

    @Override
    public boolean isOnline() {
        if (!props.isEnabled()) return false;
        try {
            Map<?, ?> body = restClient.get()
                    .uri("/health")
                    .headers(this::aplicarApiKey)
                    .retrieve()
                    .body(Map.class);
            return body != null && "UP".equals(body.get("status"));
        } catch (RestClientException e) {
            log.warn("Modulo whatsapp offline: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getCircuitBreakerState() {
        return circuitBreaker.getState().name();
    }

    @Override
    public boolean isHabilitado() {
        return props.isEnabled();
    }

    private <T> T execute(Supplier<T> supplier) {
        Supplier<T> decorated = CircuitBreaker.decorateSupplier(circuitBreaker,
                Retry.decorateSupplier(retry, supplier));
        try {
            return decorated.get();
        } catch (HttpClientErrorException e) {
            throw new WhatsAppException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            throw new WhatsAppException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (CallNotPermittedException e) {
            throw new WhatsAppIndisponivelException("Modulo whatsapp: circuit breaker aberto", e);
        } catch (RestClientException e) {
            throw new WhatsAppIndisponivelException("Modulo whatsapp indisponivel: " + e.getMessage(), e);
        }
    }

    private <T> T postJson(String path, Object body, Class<T> responseType) {
        return restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(this::aplicarApiKey)
                .body(body)
                .retrieve()
                .body(responseType);
    }

    private void aplicarApiKey(HttpHeaders headers) {
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            headers.set("X-API-Key", props.getApiKey());
        }
    }

    private void verificarHabilitado() {
        if (!props.isEnabled()) {
            throw new WhatsAppIndisponivelException("Modulo whatsapp desabilitado");
        }
    }

    private static RestClient criarRestClient(WhatsAppProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getTimeout());
        factory.setReadTimeout(props.getTimeout());
        return RestClient.builder()
                .baseUrl(props.getUrl())
                .requestFactory(factory)
                .build();
    }

    // Wire bodies — as chaves JSON espelham os request DTOs do api-whatsapp Controller.
    private record EnviarTextoBody(String telefone, String texto) {
    }

    private record EnviarDocumentoBody(String telefone, String mediaBase64, String mimeType, String filename, String caption) {
    }

    private record EnviarBotoesBody(String telefone, String texto, List<BotaoDto> botoes) {
    }

    private record EnviarListaBody(String telefone, String texto, List<SecaoDto> secoes) {
    }
}
