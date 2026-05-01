package br.com.erpkit.consultas.client;

import br.com.erpkit.consultas.client.dto.EnderecoResponse;
import br.com.erpkit.consultas.client.dto.FornecedorResponse;
import br.com.erpkit.consultas.client.exception.ConsultasException;
import br.com.erpkit.consultas.client.exception.ConsultasIndisponivelException;
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

public class ConsultasClientImpl implements ConsultasClient {

    private static final Logger log = LoggerFactory.getLogger(ConsultasClientImpl.class);
    private static final String NOME_MODULO = "consultas";

    private final ConsultasProperties props;
    private final RestTemplate restTemplate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ConsultasClientImpl(ConsultasProperties props) {
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
    public EnderecoResponse consultarCep(String cep) {
        verificarHabilitado();
        String path = "/api/cep/" + sanitize(cep);
        return execute(() -> getJson(path, EnderecoResponse.class));
    }

    @Override
    public FornecedorResponse consultarCnpj(String cnpj) {
        verificarHabilitado();
        String path = "/api/cnpj/" + sanitize(cnpj);
        return execute(() -> getJson(path, FornecedorResponse.class));
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
            log.warn("Modulo consultas offline: {}", e.getMessage());
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
            throw new ConsultasException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            throw new ConsultasException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (CallNotPermittedException e) {
            throw new ConsultasIndisponivelException("Modulo consultas: circuit breaker aberto", e);
        } catch (RestClientException e) {
            throw new ConsultasIndisponivelException("Modulo consultas indisponivel: " + e.getMessage(), e);
        }
    }

    private <T> T getJson(String path, Class<T> responseType) {
        ResponseEntity<T> response = restTemplate.exchange(
                props.getUrl() + path, HttpMethod.GET, criarEntity(null), responseType);
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
            throw new ConsultasIndisponivelException("Modulo consultas desabilitado");
        }
    }

    private static String sanitize(String entrada) {
        if (entrada == null) return "";
        return entrada.replaceAll("\\D", "");
    }

    private static RestTemplate criarRestTemplate(int timeoutMs) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return new RestTemplate(factory);
    }
}
