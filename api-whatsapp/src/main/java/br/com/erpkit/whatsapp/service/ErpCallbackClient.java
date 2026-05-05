package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.dto.ComandoCallbackDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Cliente HTTP para callback ao ERP (D-03 + ROU-02 + ROU-03 + ROU-04).
 *
 * <p>Pattern espelha {@code lib-consultas-client/ConsultasClientImpl} — la
 * programatico (decorateSupplier), aqui declarativo via annotations Spring AOP.
 * Parametros sao resolvidos do {@code application.yml} bloco
 * {@code resilience4j.{circuitbreaker,retry}.instances.erp-callback}:
 * <ul>
 *   <li>CircuitBreaker: 10-call window, 50% threshold, 60s open, 3 in half-open.</li>
 *   <li>Retry: 3 tentativas, backoff exponencial 1s/2.0x = 1s/2s/4s.</li>
 *   <li>Retry-exceptions: HttpServerErrorException + SocketTimeoutException + IOException.
 *       4xx categoricos NAO retentam (D-08, ROU-03).</li>
 * </ul>
 *
 * <p><b>Aspect order</b> (Resilience4j Spring Boot starter): Retry POR FORA de
 * CircuitBreaker (default order: Retry order = LOWEST_PRECEDENCE-3, CircuitBreaker
 * order = LOWEST_PRECEDENCE-2; lower order = outer aspect). 1 dispatch falho = 3
 * calls counted no CB sliding-window. 4 dispatches falhos consecutivos = 12 calls
 * = circuit aberto.
 *
 * <p><b>fallbackMethod localizado em {@code @Retry} (NAO em {@code @CircuitBreaker}):</b>
 * Resilience4j Spring AOP — quando {@code fallbackMethod} esta na annotation INNER
 * (CircuitBreaker), o fallback converte a excecao em retorno de sucesso ANTES da
 * outer (Retry) ver o erro, mascarando o trigger de retry. Solucao: por
 * {@code fallbackMethod} no aspect OUTER ({@code @Retry}) — Retry executa todas as
 * tentativas e SO ENTAO chama fallback se todas falharem. CircuitBreaker inner
 * continua contabilizando cada attempt no sliding-window (sem swallow). Bug
 * empirico descoberto na primeira execucao do test {@code cinquecentos_recupera_counter_3}
 * (counter == 1 em vez de 3 com fallback no @CircuitBreaker) — fix Rule 1.
 *
 * <p><b>Fallback (D-08, ROU-03):</b> quando retries esgotam OU circuit abre,
 * {@link #fallbackDespachar} apenas log.error estruturado e fim — SEM rethrow,
 * SEM retry adicional. ERP pode ter executado parcialmente (handler ja marcou
 * orcamento como "visualizado") — retry duplicaria side effect (PITFALLS C-05).
 *
 * <p><b>Risk A6:</b> annotations resolvem via Spring AOP — Wave 1 garantiu
 * {@code spring-boot-starter-aop} no classpath. Sem ele, annotations viram no-op
 * silencioso. {@code ErpCallbackClientTest.cinquecentos_recupera_counter_3} valida
 * empiricamente via WireMock counter == 3.
 */
@Service
public class ErpCallbackClient {

    private static final Logger log = LoggerFactory.getLogger(ErpCallbackClient.class);

    private final RestClient restClient;

    public ErpCallbackClient(WhatsAppProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = properties.getCallbackTimeout();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getErpCallbackUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * Despacha comando ao ERP via callback HTTP. Idempotente do ponto de vista do
     * caller — propagacao de excecao e travada pelo {@link #fallbackDespachar}.
     *
     * <p>Anotada com {@code @Retry} POR FORA de {@code @CircuitBreaker} (default
     * aspect order do Resilience4j Spring Boot starter). {@code fallbackMethod}
     * INTENCIONALMENTE no {@code @Retry} (outer) — se ficasse no {@code @CircuitBreaker}
     * (inner), o fallback inner mascararia a excecao do outer Retry e o retry NAO
     * dispararia. Em caso de 4xx (nao listado em {@code retry-exceptions}), Retry
     * nao retenta e fallback e chamado imediatamente.
     */
    @CircuitBreaker(name = "erp-callback")
    @Retry(name = "erp-callback", fallbackMethod = "fallbackDespachar")
    public void despachar(ComandoCallbackDTO payload) {
        log.debug("Dispatch ERP: telefone={} comando={}", payload.telefone(), payload.comando());
        restClient.post()
                .uri("/api/modulos/whatsapp/comando")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        log.info("ERP callback ok: telefone={} comando={}", payload.telefone(), payload.comando());
    }

    /**
     * Fallback chamado quando Resilience4j esgota retries OU circuit abre
     * ({@code CallNotPermittedException}).
     * <ul>
     *   <li>Signature OBRIGATORIA: mesmos params da method original + {@link Throwable}
     *       como ULTIMO parametro (Resilience4j convention).</li>
     *   <li>SEM retry adicional, SEM rethrow — apenas log estruturado (D-08, ROU-03).</li>
     *   <li>log.error apenas com {@code t.getMessage()}, NAO {@code t} inteiro
     *       (PITFALLS C-09: stack trace de HttpClientErrorException pode incluir
     *       Bearer header em alguns drivers HTTP).</li>
     * </ul>
     */
    @SuppressWarnings("unused") // referenciado por fallbackMethod = "fallbackDespachar"
    private void fallbackDespachar(ComandoCallbackDTO payload, Throwable t) {
        log.error("ERP callback falhou apos retry+CB: telefone={} comando={}: {}",
                payload.telefone(), payload.comando(), t.getMessage());
        // ack-first: ERP pode ter executado parcialmente; nao retentar.
    }
}
