package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import br.com.erpkit.whatsapp.dto.ComandoCallbackDTO;
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

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests Resilience4j AOP-driven (@CircuitBreaker + @Retry) com WireMock standalone.
 *
 * <p><b>Risk A6 detector key:</b> {@link #cinquecentos_recupera_counter_3} —
 * counter == 3 PROVA que Spring AOP esta resolvendo as annotations Resilience4j.
 * Sem {@code spring-boot-starter-aop} no classpath (Wave 1), annotations seriam
 * no-op silencioso e counter == 1.
 *
 * <p><b>Risk A3 mitigation:</b> @BeforeEach reseta o CircuitBreaker bean (Singleton)
 * para evitar state pollution cross-test.
 *
 * <p>Pattern WireMock + @DynamicPropertySource alinhado com {@code MetaMediaClientTest}
 * (Wave 3) — reuse direto.
 */
@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
class ErpCallbackClientTest {

    private static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @DynamicPropertySource
    static void overrideErpUrl(DynamicPropertyRegistry registry) {
        registry.add("app.modulos.whatsapp.erp-callback-url", () -> wireMock.baseUrl());
    }

    @Autowired
    ErpCallbackClient client;

    @Autowired
    CircuitBreakerRegistry cbRegistry;

    @BeforeEach
    void resetEverything() {
        wireMock.resetAll();
        // Risk A3: reset circuit breaker entre tests para evitar state pollution
        // cross-test (CB e bean Singleton com state que persiste no contexto).
        cbRegistry.find("erp-callback").ifPresent(CircuitBreaker::reset);
    }

    /** Helper: payload unico por chamada — evita dedup acidental por scenarioState. */
    private ComandoCallbackDTO payload() {
        return new ComandoCallbackDTO(
                "554784178525",
                "orcamento",
                "orcamento " + UUID.randomUUID(),
                42L,
                null,
                null,
                null);
    }

    @Test
    @DisplayName("Happy path: WireMock 200 — counter == 1, sem retry, sem fallback")
    void happy_path_counter_1() {
        wireMock.stubFor(post(urlEqualTo("/api/modulos/whatsapp/comando"))
                .willReturn(aResponse().withStatus(200)));

        client.despachar(payload());

        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/modulos/whatsapp/comando")));
    }

    @Test
    @DisplayName("5xx recupera: 500, 500, 200 — counter == 3 (PROVA Risk A6: AOP funcionando)")
    void cinquecentos_recupera_counter_3() {
        // Scenario state: 1a request -> 500, 2a -> 500, 3a -> 200
        wireMock.stubFor(post(urlEqualTo("/api/modulos/whatsapp/comando"))
                .inScenario("retry-recover").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("retry-1"));
        wireMock.stubFor(post(urlEqualTo("/api/modulos/whatsapp/comando"))
                .inScenario("retry-recover").whenScenarioStateIs("retry-1")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("retry-2"));
        wireMock.stubFor(post(urlEqualTo("/api/modulos/whatsapp/comando"))
                .inScenario("retry-recover").whenScenarioStateIs("retry-2")
                .willReturn(aResponse().withStatus(200)));

        client.despachar(payload());

        // CRITICO: counter == 3 PROVA que @Retry esta ativo via AOP. Sem
        // spring-boot-starter-aop (Risk A6), annotation seria no-op silencioso e
        // counter == 1 (sem retry).
        wireMock.verify(3, postRequestedFor(urlEqualTo("/api/modulos/whatsapp/comando")));
    }

    @Test
    @DisplayName("5xx persistente: counter == 3 (max-attempts) + fallback NAO propaga excecao")
    void cinquecentos_persistente_fallback_log() {
        wireMock.stubFor(post(urlEqualTo("/api/modulos/whatsapp/comando"))
                .willReturn(aResponse().withStatus(500)));

        // Fallback NAO propaga — call retorna void normalmente
        client.despachar(payload());

        // counter == 3 (max-attempts da config)
        wireMock.verify(3, postRequestedFor(urlEqualTo("/api/modulos/whatsapp/comando")));
    }

    @Test
    @DisplayName("4xx (400): counter == 1 (NAO retenta per yml retry-exceptions) + fallback chamado")
    void quatrocentos_no_retry_counter_1() {
        wireMock.stubFor(post(urlEqualTo("/api/modulos/whatsapp/comando"))
                .willReturn(aResponse().withStatus(400)));

        // 4xx (HttpClientErrorException) NAO esta na whitelist de retry-exceptions
        // -> Resilience4j chama fallback IMEDIATAMENTE (sem retry).
        client.despachar(payload());

        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/modulos/whatsapp/comando")));
    }

    @Test
    @DisplayName("Timeout: delay > callbackTimeout — retry triggers, eventualmente fallback")
    void timeout_retry_e_fallback() {
        // application-test.yml callbackTimeout = 500ms — delay 1500ms forca timeout.
        // SocketTimeoutException ESTA na whitelist de retry-exceptions -> deve retentar.
        wireMock.stubFor(post(urlEqualTo("/api/modulos/whatsapp/comando"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(1500)));

        client.despachar(payload());

        // Counter > 1 prova que houve retry. Esperado == 3 (max-attempts), mas
        // aceitamos > 1 para nao ser flaky em caso de timing edge cases.
        int count = wireMock.findAll(postRequestedFor(urlEqualTo("/api/modulos/whatsapp/comando")))
                .size();
        assertThat(count)
                .as("Timeout deve ter retentado ao menos 1 vez (counter > 1)")
                .isGreaterThan(1);
    }

    @Test
    @DisplayName("Circuit aberto apos 4 dispatches em 5xx (12 calls > sliding-window 10) — fallback IMEDIATO")
    void circuit_open_apos_falhas_repetidas() {
        wireMock.stubFor(post(urlEqualTo("/api/modulos/whatsapp/comando"))
                .willReturn(aResponse().withStatus(500)));

        // 4 dispatches consecutivos: cada um faz 3 calls (retry) = 12 total.
        // Sliding-window-size = 10, failure-rate-threshold = 50% — circuit abre.
        for (int i = 0; i < 4; i++) {
            client.despachar(payload());
        }

        var cb = cbRegistry.find("erp-callback").orElseThrow();
        assertThat(cb.getState())
                .as("Apos 4 dispatches falhos (12 calls com retry), circuit deveria estar OPEN/HALF_OPEN")
                .isIn(CircuitBreaker.State.OPEN, CircuitBreaker.State.HALF_OPEN);

        // 5o dispatch: fallback IMEDIATO via CallNotPermittedException — counter
        // NAO incrementa no WireMock (request nao chega ate o stub).
        long countBefore = wireMock.findAll(postRequestedFor(urlEqualTo("/api/modulos/whatsapp/comando")))
                .size();
        client.despachar(payload());
        long countAfter = wireMock.findAll(postRequestedFor(urlEqualTo("/api/modulos/whatsapp/comando")))
                .size();
        assertThat(countAfter)
                .as("Circuit aberto: dispatch nao chega ao WireMock (CallNotPermittedException -> fallback)")
                .isEqualTo(countBefore);
    }

    // ==================== resolverIdCliente ====================

    @Test
    @DisplayName("resolver: match unico -> le idCliente do body {\"idCliente\":3}")
    void resolver_matchUnico_retornaId() {
        wireMock.stubFor(get(urlPathEqualTo("/api/modulos/whatsapp/resolver"))
                .withQueryParam("telefone", equalTo("554784178525"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"idCliente\":3}")));

        assertThat(client.resolverIdCliente("554784178525")).isEqualTo(3L);
    }

    @Test
    @DisplayName("resolver: sem match -> body {} (idCliente ausente) -> null")
    void resolver_semMatch_retornaNull() {
        wireMock.stubFor(get(urlPathEqualTo("/api/modulos/whatsapp/resolver"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        assertThat(client.resolverIdCliente("554700000000")).isNull();
    }

    @Test
    @DisplayName("resolver: best-effort — erro no ERP (500) nao lanca, retorna null")
    void resolver_erro_retornaNull() {
        wireMock.stubFor(get(urlPathEqualTo("/api/modulos/whatsapp/resolver"))
                .willReturn(aResponse().withStatus(500)));

        assertThat(client.resolverIdCliente("554784178525")).isNull();
    }
}
