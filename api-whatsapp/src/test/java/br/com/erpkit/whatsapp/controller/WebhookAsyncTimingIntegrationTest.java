package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StreamUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test E2E SC-1 da Phase 3 (Wave 6 PLAN 03-06).
 *
 * <p><b>SC-1 ROADMAP:</b> {@code POST /webhook/whatsapp} retorna 200 em &lt;1s
 * mesmo com ERP delay 10s — ack precede toda I/O externa via {@code @Async}.
 *
 * <p><b>NAO importa {@code AsyncTestConfig}</b> — usa pool real
 * ({@code AsyncConfig.whatsappTaskExecutor}) para validar empiricamente o
 * comportamento async em prod. Com SyncTaskExecutor (que e o default em outros
 * tests E2E desta wave), o listener bloqueia a thread do MockMvc e o test
 * falharia o limite de timing.
 *
 * <p><b>Awaitility:</b> apos asser elapsed &lt; 1000ms, valida que o listener
 * async eventualmente chega no callback ERP (mesmo que falhe por timeout
 * Resilience4j em test profile com {@code callbackTimeout=500ms}). O importante
 * pra SC-1 e o tempo do ack 200, nao o sucesso do callback.
 *
 * <p><b>Reset CB:</b> {@link #resetPerTest()} reseta circuit breaker e
 * contadores WireMock (Risk A3 — Singleton state cross-test).
 *
 * <p><b>Por que classe SEPARADA de {@link WebhookAsyncIntegrationTest}:</b>
 * AsyncTestConfig (SyncTaskExecutor) e injetado a nivel de classe via
 * {@code @Import} — diferentes classes carregam diferentes SpringContext caches.
 * Mais simples e robusto que {@code @Nested} com config separada.
 */
@SpringBootTest(classes = WhatsAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookAsyncTimingIntegrationTest {

    private static WireMockServer wireMockErp;

    @BeforeAll
    static void startWireMock() {
        wireMockErp = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockErp.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockErp != null) {
            wireMockErp.stop();
        }
    }

    @DynamicPropertySource
    static void overrideErpUrl(DynamicPropertyRegistry registry) {
        registry.add("app.modulos.whatsapp.erp-callback-url", () -> wireMockErp.baseUrl());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WhatsAppProperties properties;

    @Autowired
    private CircuitBreakerRegistry cbRegistry;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetPerTest() {
        wireMockErp.resetAll();
        cbRegistry.find("erp-callback").ifPresent(CircuitBreaker::reset);
        // Cleanup DB cross-test — H2 in-memory tem DB_CLOSE_DELAY=-1 e mesma URL
        // entre SpringContexts; rows de tests anteriores (ex: WebhookAsyncIntegrationTest)
        // persistem. Sem cleanup, wamid duplicado -> tx outer rollback-only ->
        // AFTER_COMMIT listener NAO dispara -> Awaitility timeout (15s).
        jdbc.update("DELETE FROM whatsapp.mensagens_log");
        jdbc.update("DELETE FROM whatsapp.clientes_zap");
    }

    private static String computeSignature(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmac = mac.doFinal(body);
        return "sha256=" + HexFormat.of().formatHex(hmac);
    }

    private byte[] fixture(String nome) throws Exception {
        try (InputStream in = new ClassPathResource("fixtures/webhook/" + nome).getInputStream()) {
            return StreamUtils.copyToByteArray(in);
        }
    }

    @Test
    @DisplayName("SC-1: POST retorna 200 em <1s mesmo com ERP delay 10s (pool real, listener @Async)")
    void sc1_ack_first_under_1s_mesmo_com_erp_delay_10s() throws Exception {
        // ERP responde com delay de 10s (muito maior que callbackTimeout=500ms do test
        // profile). Em test profile, Resilience4j vai timeoutar e retentar; o que
        // importa para SC-1 e que a thread do MockMvc NAO espera por isso.
        wireMockErp.stubFor(WireMock.post(urlEqualTo("/api/modulos/whatsapp/comando"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(10000)));

        byte[] body = fixture("text-portugues.json");
        String sig = computeSignature(body, properties.getAppSecret());

        long start = System.currentTimeMillis();
        mockMvc.perform(post("/webhook/whatsapp")
                        .header("X-Hub-Signature-256", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body))
                .andExpect(status().isOk());
        long elapsed = System.currentTimeMillis() - start;

        // SC-1 do ROADMAP: ack 200 em <1s mesmo com ERP delay 10s.
        // Em CI lento, jitter pode adicionar ate ~500ms. Limite 1000ms permite
        // headroom para jitter mas trava regressao significativa (ack-first
        // garante <100ms na pratica em hardware razoavel).
        assertThat(elapsed)
                .as("Ack-first: 200 ao Meta deve retornar em <1s mesmo com ERP delay 10s "
                        + "(pool real - prod-like)")
                .isLessThan(1000);

        // Awaitility: confirma que listener async eventualmente chamou o ERP
        // (mesmo que callback falhe por timeout Resilience4j 500ms < delay 10s).
        // Para SC-1, o que importa e o elapsed do ack; este passo confirma que
        // o listener REALMENTE rodou async (nao foi swallowed silenciosamente
        // antes do listener disparar).
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(
                        wireMockErp.findAll(postRequestedFor(urlEqualTo("/api/modulos/whatsapp/comando"))))
                        .as("Listener async eventualmente chamou ERP callback")
                        .isNotEmpty());
    }
}
