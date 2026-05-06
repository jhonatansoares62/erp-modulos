package br.com.erpkit.whatsapp.aspect;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import br.com.erpkit.whatsapp.exception.JanelaConversaFechadaException;
import br.com.erpkit.whatsapp.service.WindowEnforcementService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.annotation.Retry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests do {@link JanelaEnforcementAspect} — D-03 + OUT-07 + Pitfall 1 RESEARCH.
 *
 * <p><b>CRITICO:</b> {@link #aspect_invoca_apenas_uma_vez_em_3_retries}
 * empiricamente prova que {@code @Order(HIGHEST_PRECEDENCE)} faz o aspect rodar
 * OUTERMOST do Resilience4j Retry chain — counter == 1 em 3 retries (1 chamada
 * do aspect, 3 chamadas HTTP via WireMock). Sem esse ordering, contador seria
 * 3 (verificarJanela chamado em cada tentativa). Regression test obrigatorio.
 *
 * <p><b>Pattern espelhando {@link br.com.erpkit.whatsapp.service.ErpCallbackClientTest}</b>
 * (Phase 3 03-04): WireMock standalone com {@code dynamicPort()} +
 * {@code @SpyBean WindowEnforcementService} para contar invocacoes do service real.
 *
 * <p><b>Dummy bean</b> via {@link DummyClientConfig}: reproduz production-real
 * ordering com {@code @JanelaProtegida} + {@code @Retry(name="whatsapp-cloud")}
 * em metodos publicos do componente. Spring AOP precisa de OUTRO bean (cross-bean
 * call) para o proxy interceptar — self-call dentro do mesmo bean nao ativaria.
 */
@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
@Import(JanelaEnforcementAspectTest.DummyClientConfig.class)
class JanelaEnforcementAspectTest {

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

    @SpyBean WindowEnforcementService windowSpy;
    @Autowired DummyAspectClient dummyClient;
    @Autowired CircuitBreakerRegistry cbRegistry;

    @BeforeEach
    void resetEverything() {
        wireMock.resetAll();
        reset(windowSpy);
        // Resilience4j CB e Singleton — reseta state entre tests (Risk A3 mitigation).
        cbRegistry.find("whatsapp-cloud").ifPresent(CircuitBreaker::reset);
    }

    @Test
    @DisplayName("aspect invoca verificarJanela apenas 1 vez em 3 retries do Resilience4j (HIGHEST_PRECEDENCE)")
    void aspect_invoca_apenas_uma_vez_em_3_retries() {
        // ARRANGE: window aberta (spy nao lanca)
        doNothing().when(windowSpy).verificarJanela(any());

        // WireMock scenarioState: 500 -> 500 -> 200 (2 retries antes de sucesso = 3 tentativas total)
        wireMock.stubFor(post(urlPathEqualTo("/dummy"))
                .inScenario("retries").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("attempt-2"));
        wireMock.stubFor(post(urlPathEqualTo("/dummy"))
                .inScenario("retries").whenScenarioStateIs("attempt-2")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("attempt-3"));
        wireMock.stubFor(post(urlPathEqualTo("/dummy"))
                .inScenario("retries").whenScenarioStateIs("attempt-3")
                .willReturn(aResponse().withStatus(200)));

        // ACT
        dummyClient.chamarComJanelaProtegida("554784178525", wireMock.baseUrl() + "/dummy");

        // ASSERT
        // 3 chamadas HTTP recebidas pelo WireMock prova que Resilience4j @Retry funcionou.
        wireMock.verify(3, postRequestedFor(urlPathEqualTo("/dummy")));
        // CRITICO: APENAS 1 verificarJanela prova @Order(HIGHEST_PRECEDENCE) — aspect
        // outermost do retry loop. Sem esse ordering, counter == 3 (1 por tentativa).
        verify(windowSpy, times(1)).verificarJanela("554784178525");
    }

    @Test
    @DisplayName("aspect lanca IllegalStateException se primeiro arg nao for String (fail-fast convencao posicional)")
    void aspect_lanca_se_args_nao_string() {
        // ARRANGE: spy permite qualquer chamada (mas nao deve ser invocado pois
        // aspect aborta ANTES de chegar a verificarJanela)
        doNothing().when(windowSpy).verificarJanela(any());

        // ACT + ASSERT: chama metodo com Long como primeiro arg — aspect deve
        // lancar IllegalStateException antes de proceder.
        assertThatThrownBy(() -> dummyClient.metodoComArgErrado(123L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("primeiro argumento String");
    }

    @Test
    @DisplayName("aspect propaga JanelaConversaFechadaException sem swallowing (curto-circuita Cloud API)")
    void aspect_propaga_janela_fechada_exception() {
        // ARRANGE: window FECHADA — spy lanca quando verificarJanela e invocado
        doThrow(new JanelaConversaFechadaException("554784178525", null))
                .when(windowSpy).verificarJanela(any());

        // ACT + ASSERT: chamada deve lancar JanelaConversaFechadaException
        assertThatThrownBy(() -> dummyClient.chamarComJanelaProtegida(
                "554784178525", wireMock.baseUrl() + "/dummy"))
                .isInstanceOf(JanelaConversaFechadaException.class)
                .satisfies(t -> {
                    JanelaConversaFechadaException ex = (JanelaConversaFechadaException) t;
                    assertThat(ex.getCodigo()).isEqualTo("JANELA_24H_FECHADA");
                });

        // verificarJanela chamado 1x (aspect curto-circuitou); HTTP NUNCA tentado
        // (aspect lancou ANTES do pjp.proceed()). CRITICO: prova que JanelaConversaFechadaException
        // NAO esta na whitelist de retry-exceptions de whatsapp-cloud — ou Resilience4j
        // teria retentado e o counter HTTP seria > 0.
        verify(windowSpy, times(1)).verificarJanela("554784178525");
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/dummy")));
    }

    /**
     * Dummy bean anotado com {@code @JanelaProtegida} + {@code @Retry(name="whatsapp-cloud")}
     * para reproduzir o ordering production-real (aspect HIGHEST_PRECEDENCE outermost,
     * Resilience4j Retry inner). Sem CircuitBreaker aqui — Retry e suficiente para
     * counter==1 assertion (CB shared singleton causaria pollution cross-test).
     */
    static class DummyAspectClient {
        private final RestClient restClient = RestClient.create();

        @JanelaProtegida
        @Retry(name = "whatsapp-cloud")
        public void chamarComJanelaProtegida(String telefone, String url) {
            restClient.post().uri(url).retrieve().toBodilessEntity();
        }

        @JanelaProtegida
        public String metodoComArgErrado(Long naoEhString) {
            return "nunca alcanca";
        }
    }

    @TestConfiguration
    static class DummyClientConfig {
        @Bean
        DummyAspectClient dummyAspectClient() {
            return new DummyAspectClient();
        }
    }
}
