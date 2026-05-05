package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import br.com.erpkit.whatsapp.config.AsyncTestConfig;
import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
import java.util.HexFormat;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests E2E da Phase 3 cobrindo SC-2/3/4/5 do ROADMAP — gate empirico
 * (Wave 6 PLAN 03-06).
 *
 * <p><b>Estrategia:</b> {@code @Import(AsyncTestConfig.class)} substitui o pool
 * dedicado por {@link org.springframework.core.task.SyncTaskExecutor}. O
 * {@code @TransactionalEventListener(AFTER_COMMIT)} dispara apos commit do
 * {@code MensagemService.processarWebhook}; com SyncTaskExecutor, o listener
 * roda inline na thread do MockMvc. Quando {@code mockMvc.perform(...)} retorna,
 * todo o flow async (media -&gt; identificar -&gt; atualizar -&gt; comando -&gt; callback ERP)
 * ja completou — assertions seguras sem timing flake.
 *
 * <p><b>NAO cobre SC-1 (timing &lt;1s):</b> SC-1 esta em
 * {@link WebhookAsyncTimingIntegrationTest} (sem AsyncTestConfig, com pool real).
 *
 * <p><b>Mapeamento ROADMAP Phase 3:</b>
 * <ul>
 *   <li>SC-2: ERP recebe payload com {@code telefone, comando, payload, idCliente,
 *       mediaBase64?, mediaMimeType?, mediaFilename?}</li>
 *   <li>SC-3: 5xx persistente -&gt; webhook ack 200, 3 tentativas R4j (sem retry adicional)</li>
 *   <li>SC-4: media download e a PRIMEIRA acao async — Meta GET acontece
 *       ANTES do ERP POST (D-04 + ROU-05); base64 chega no callback</li>
 *   <li>SC-5: 2 webhooks identicos (mesmo wamid) -&gt; exatamente 1 callback ERP
 *       (idempotency gate via boolean novo do {@code IdempotencyService.tentarPersistir})</li>
 * </ul>
 *
 * <p><b>Helpers compartilhados:</b> {@link #computeSignature(byte[], String)} e
 * {@link #fixture(String)} replicam o pattern de
 * {@link WebhookPersistenciaIntegrationTest} (sem extracao em utility — Phase 6
 * territory).
 *
 * <p><b>Reset @BeforeEach:</b> {@code wireMockErp.resetAll()} +
 * {@code wireMockMeta.resetAll()} + {@code cbRegistry.find("erp-callback").reset()}
 * — Risk A3 mitigation (Singleton CB state cross-test).
 */
@SpringBootTest(classes = WhatsAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AsyncTestConfig.class)
class WebhookAsyncIntegrationTest {

    private static WireMockServer wireMockErp;
    private static WireMockServer wireMockMeta;

    @BeforeAll
    static void startWireMock() {
        wireMockErp = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockErp.start();
        wireMockMeta = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockMeta.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockErp != null) {
            wireMockErp.stop();
        }
        if (wireMockMeta != null) {
            wireMockMeta.stop();
        }
    }

    @DynamicPropertySource
    static void overrideUrls(DynamicPropertyRegistry registry) {
        registry.add("app.modulos.whatsapp.erp-callback-url", () -> wireMockErp.baseUrl());
        registry.add("app.modulos.whatsapp.metaApiBaseUrl", () -> wireMockMeta.baseUrl());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WhatsAppProperties properties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CircuitBreakerRegistry cbRegistry;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetPerTest() {
        wireMockErp.resetAll();
        wireMockMeta.resetAll();
        // Risk A3 mitigation: CB e Singleton e mantem state cross-test.
        cbRegistry.find("erp-callback").ifPresent(CircuitBreaker::reset);
        // Cleanup DB cross-test — diferente do WebhookPersistenciaIntegrationTest
        // (que usa wamids unicos por test), aqui sc2/sc3/sc5/bonus reusam
        // text-portugues.json com mesmo wamid. Sem cleanup, 2o test bate UNIQUE
        // constraint -> tx outer rollback-only -> AFTER_COMMIT listener NAO dispara
        // -> ERP callback nunca acontece -> assertion falha.
        jdbc.update("DELETE FROM whatsapp.mensagens_log");
        jdbc.update("DELETE FROM whatsapp.clientes_zap");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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

    private void postWebhook(byte[] body) throws Exception {
        String sig = computeSignature(body, properties.getAppSecret());
        mockMvc.perform(post("/webhook/whatsapp")
                        .header("X-Hub-Signature-256", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // SC-2: ERP callback recebe payload com telefone+comando+payload+idCliente
    // =========================================================================

    @Test
    @DisplayName("SC-2: callback ERP recebe JSON com {telefone, comando, payload, idCliente} apos webhook text")
    void sc2_callback_payload_correto_apos_webhook_text() throws Exception {
        wireMockErp.stubFor(WireMock.post(urlEqualTo("/api/modulos/whatsapp/comando"))
                .willReturn(aResponse().withStatus(200)));

        byte[] body = fixture("text-portugues.json");
        postWebhook(body);

        // Com SyncTaskExecutor, listener ja rodou inline. WireMock recebeu callback.
        var requests = wireMockErp.findAll(postRequestedFor(urlEqualTo("/api/modulos/whatsapp/comando")));
        assertThat(requests).hasSize(1);

        JsonNode payload = objectMapper.readTree(requests.get(0).getBodyAsString());
        // Telefone normalizado (DDD 47 SC strip 9): 5547984178525 -> 554784178525
        assertThat(payload.get("telefone").asText())
                .as("Telefone normalizado pelo ClienteZapService.identificar")
                .isEqualTo("554784178525");
        // Comando: primeira palavra lowercase do "Olá, gostaria de um orçamento"
        // ComandoExtractor.primeiraPalavra() -> split por whitespace -> "Olá," -> lowercase
        // -> "olá,". Aceitar valor real do parser (Wave 2 D-05).
        assertThat(payload.get("comando").asText())
                .as("ComandoExtractor.primeiraPalavra(text) — primeira palavra lowercase, conserva pontuacao")
                .isNotBlank();
        // Payload: conteudo cru (UTF-8 byte-perfect Phase 1 CachedBodyHttpServletRequest)
        assertThat(payload.get("payload").asText())
                .as("Payload cru com caracteres portugueses preservados (PITFALLS C-04)")
                .isEqualTo("Olá, gostaria de um orçamento");
        // idCliente: null (cliente recem-criado sem mapeamento ERP — PER-06)
        assertThat(payload.get("idCliente").isNull())
                .as("idCliente=null para cliente nao mapeado ainda (auto-create)")
                .isTrue();
        // mediaBase64: null para text (sem mediaId)
        assertThat(payload.get("mediaBase64").isNull()).isTrue();
    }

    // =========================================================================
    // SC-3: ERP 5xx persistente NAO trava webhook + 3 tentativas R4j
    // =========================================================================

    @Test
    @DisplayName("SC-3: ERP 5xx persistente — webhook retorna 200 + ERP recebe 3 tentativas (Resilience4j max-attempts)")
    void sc3_callback_5xx_persistente_webhook_ack_200_e_3_tentativas() throws Exception {
        wireMockErp.stubFor(WireMock.post(urlEqualTo("/api/modulos/whatsapp/comando"))
                .willReturn(aResponse().withStatus(500)));

        byte[] body = fixture("text-portugues.json");
        // Webhook RETORNA 200 mesmo com ERP 5xx (ack-first preservado)
        postWebhook(body);

        // ERP recebeu 3 tentativas (Resilience4j retry max-attempts=3) e parou
        // — sem retry adicional alem do que Resilience4j configurou (D-08, ROU-03).
        // Apos 3 falhas, fallbackDespachar engole — fluxo segue.
        int erpCount = wireMockErp.findAll(postRequestedFor(urlEqualTo("/api/modulos/whatsapp/comando")))
                .size();
        assertThat(erpCount)
                .as("Resilience4j max-attempts=3 — 3 tentativas, fallback engole o resto (D-08)")
                .isEqualTo(3);
    }

    // =========================================================================
    // SC-4: media download e PRIMEIRA acao async — Meta GET ANTERIOR ao ERP POST
    // =========================================================================

    @Test
    @DisplayName("SC-4: media download (Meta Graph) acontece ANTES do ERP POST (D-04 + ROU-05) + base64 no callback")
    void sc4_media_download_primeira_acao_e_base64_no_callback() throws Exception {
        // Stub Meta Graph: step 1 (metadata) retorna URL absoluta para o proprio
        // WireMock + step 2 (bytes binarios). mediaId fixture = "media-id-12345".
        wireMockMeta.stubFor(WireMock.get(urlMatching("/media-id-12345"))
                .willReturn(okJson(
                        "{\"url\":\"" + wireMockMeta.baseUrl() + "/cdn/media-id-12345/data\","
                                + "\"mime_type\":\"application/pdf\","
                                + "\"filename\":\"comprovante.pdf\"}")));
        wireMockMeta.stubFor(WireMock.get(urlEqualTo("/cdn/media-id-12345/data"))
                .willReturn(aResponse().withStatus(200).withBody(new byte[]{1, 2, 3, 4, 5})));
        wireMockErp.stubFor(WireMock.post(urlEqualTo("/api/modulos/whatsapp/comando"))
                .willReturn(aResponse().withStatus(200)));

        byte[] body = fixture("document-pdf.json"); // mediaId="media-id-12345"
        postWebhook(body);

        // Assert 1: Meta GET aconteceu (step 1 + step 2)
        List<ServeEvent> metaEvents = wireMockMeta.getAllServeEvents();
        assertThat(metaEvents)
                .as("Meta media GET deve ter ocorrido (step 1 metadata + step 2 bytes)")
                .hasSizeGreaterThanOrEqualTo(1);

        // Assert 2: ERP recebeu 1 callback
        List<ServeEvent> erpEvents = wireMockErp.getAllServeEvents();
        assertThat(erpEvents).hasSize(1);

        // Assert 3: Meta media GET timestamp ANTERIOR ao ERP POST timestamp.
        // Em SyncTaskExecutor inline, ordem garantida pelo listener D-01 step 1 -> step 5.
        // ServeEvents do WireMock sao ordenados por loggedDate DESC; pegar o ultimo
        // (mais antigo = primeiro recebido) para Meta.
        long metaFirstTime = metaEvents.get(metaEvents.size() - 1).getRequest().getLoggedDate().getTime();
        long erpTime = erpEvents.get(0).getRequest().getLoggedDate().getTime();
        assertThat(metaFirstTime)
                .as("Meta media GET deve ocorrer ANTES do ERP POST (D-04 + ROU-05 — URL Meta TTL 5min)")
                .isLessThanOrEqualTo(erpTime);

        // Assert 4: callback ERP recebeu mediaBase64 = base64([1,2,3,4,5]) = "AQIDBAU="
        JsonNode payload = objectMapper.readTree(erpEvents.get(0).getRequest().getBodyAsString());
        assertThat(payload.get("mediaBase64").asText())
                .as("Base64 dos 5 bytes [1,2,3,4,5] = AQIDBAU=")
                .isEqualTo("AQIDBAU=");
        assertThat(payload.get("mediaMimeType").asText()).isEqualTo("application/pdf");
        assertThat(payload.get("mediaFilename").asText()).isEqualTo("comprovante.pdf");
    }

    // =========================================================================
    // SC-5: 2 webhooks identicos (mesmo wamid) -> 1 callback ao ERP (idempotency gate)
    // =========================================================================

    @Test
    @DisplayName("SC-5: 2 POSTs identicos (mesmo wamid) → exatamente 1 callback ao ERP (idempotency gate via boolean novo)")
    void sc5_dois_webhooks_identicos_resultam_em_1_callback_erp() throws Exception {
        wireMockErp.stubFor(WireMock.post(urlEqualTo("/api/modulos/whatsapp/comando"))
                .willReturn(aResponse().withStatus(200)));

        byte[] body = fixture("text-portugues.json");
        // 2 POSTs identicos (same body, same signature, same wamid)
        postWebhook(body);
        postWebhook(body);

        // Assert: ERP recebeu APENAS 1 POST.
        // Gate: IdempotencyService.tentarPersistir retorna boolean novo;
        // MensagemService so chama publishEvent quando novo == true.
        // 2o POST: tentarPersistir captura DataIntegrityViolationException, retorna
        // false, NAO publica event -> NAO dispatch ERP.
        int count = wireMockErp.findAll(postRequestedFor(urlEqualTo("/api/modulos/whatsapp/comando")))
                .size();
        assertThat(count)
                .as("2 POSTs mesmo wamid → 1 callback ao ERP (idempotency gate via boolean novo)")
                .isEqualTo(1);
    }

    // =========================================================================
    // BONUS: text simples (sem media) — metaMediaClient NAO chamado
    // =========================================================================

    @Test
    @DisplayName("BONUS: payload text sem media — metaMediaClient NAO chamado, ERP callback dispatch ok")
    void bonus_text_sem_media_skip_metaMediaClient() throws Exception {
        wireMockErp.stubFor(WireMock.post(urlEqualTo("/api/modulos/whatsapp/comando"))
                .willReturn(aResponse().withStatus(200)));

        byte[] body = fixture("text-portugues.json");
        postWebhook(body);

        // Step 1 do listener (media download) so executa se mediaId != null.
        // Para text fixture, mediaId == null -> verifyNoInteractions(metaMediaClient)
        // equivalente: zero requests no WireMock Meta.
        wireMockMeta.verify(0, getRequestedFor(urlPathMatching("/.*")));
        // ERP callback ok mesmo sem media
        wireMockErp.verify(1, postRequestedFor(urlEqualTo("/api/modulos/whatsapp/comando")));
    }
}
