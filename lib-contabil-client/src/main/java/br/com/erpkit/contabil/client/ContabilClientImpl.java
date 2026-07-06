package br.com.erpkit.contabil.client;

import br.com.erpkit.contabil.client.dto.EventoContabilRequest;
import br.com.erpkit.contabil.client.dto.EventoRecebidoResponse;
import br.com.erpkit.contabil.client.exception.ContabilException;
import br.com.erpkit.contabil.client.exception.ContabilIndisponivelException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

public class ContabilClientImpl implements ContabilClient {

    private static final Logger log = LoggerFactory.getLogger(ContabilClientImpl.class);
    private static final String NOME_MODULO = "contabil";

    private final ContabilProperties props;
    private final RestTemplate restTemplate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ContabilClientImpl(ContabilProperties props) {
        this.props = props;
        this.restTemplate = criarRestTemplate(props.getTimeout());

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
    public EventoRecebidoResponse enviarEvento(EventoContabilRequest evento) {
        verificarHabilitado();
        return execute(() -> postJson("/v1/eventos", evento, EventoRecebidoResponse.class));
    }

    @Override
    public String proxyGet(String path) {
        verificarHabilitado();
        return execute(() -> restTemplate.exchange(
                props.getUrl() + path, HttpMethod.GET, criarEntity(null), String.class).getBody());
    }

    @Override
    public String proxyPost(String path, String body) {
        verificarHabilitado();
        return execute(() -> restTemplate.exchange(
                props.getUrl() + path, HttpMethod.POST, criarEntity(body), String.class).getBody());
    }

    @Override
    public String proxyPut(String path, String body) {
        verificarHabilitado();
        return execute(() -> restTemplate.exchange(
                props.getUrl() + path, HttpMethod.PUT, criarEntity(body), String.class).getBody());
    }

    @Override
    public String proxyDelete(String path) {
        verificarHabilitado();
        return execute(() -> restTemplate.exchange(
                props.getUrl() + path, HttpMethod.DELETE, criarEntity(null), String.class).getBody());
    }

    @Override
    public boolean isOnline() {
        if (!props.isEnabled()) return false;
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    props.getUrl() + "/health", HttpMethod.GET, criarEntity(null), Map.class);
            return response.getStatusCode().is2xxSuccessful()
                    && response.getBody() != null
                    && "UP".equals(response.getBody().get("status"));
        } catch (RestClientException e) {
            log.warn("Modulo contabil offline: {}", e.getMessage());
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
            throw new ContabilException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            throw new ContabilException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (CallNotPermittedException e) {
            throw new ContabilIndisponivelException("Modulo contabil: circuit breaker aberto", e);
        } catch (RestClientException e) {
            throw new ContabilIndisponivelException("Modulo contabil indisponivel: " + e.getMessage(), e);
        }
    }

    private <T> T postJson(String path, Object body, Class<T> responseType) {
        ResponseEntity<T> response = restTemplate.exchange(
                props.getUrl() + path, HttpMethod.POST, criarEntity(body), responseType);
        return response.getBody();
    }

    private HttpEntity<?> criarEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            headers.set("X-API-Key", props.getApiKey());
        }
        return new HttpEntity<>(body, headers);
    }

    private void verificarHabilitado() {
        if (!props.isEnabled()) {
            throw new ContabilIndisponivelException("Modulo contabil desabilitado");
        }
    }

    private static RestTemplate criarRestTemplate(int timeoutMs) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return new RestTemplate(factory);
    }
}
