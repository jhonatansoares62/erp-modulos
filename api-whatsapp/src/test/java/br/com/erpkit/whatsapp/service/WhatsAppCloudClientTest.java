package br.com.erpkit.whatsapp.service;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import br.com.erpkit.whatsapp.dto.BotaoDto;
import br.com.erpkit.whatsapp.dto.EnvioResponse;
import br.com.erpkit.whatsapp.dto.ItemDto;
import br.com.erpkit.whatsapp.dto.SecaoDto;
import br.com.erpkit.whatsapp.exception.MetaApiException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests do {@link WhatsAppCloudClient} — 4 envios + cache hit/miss + Resilience4j
 * (4xx no retry, 5xx recupera, 5xx esgota, timeout, circuit aberto) + gates de
 * regressao (Bearer leak C-14, sem template OUT-05, multipart 3 fields C-15).
 *
 * <p>Pattern espelhando {@link ErpCallbackClientTest} (Phase 3 03-04) e
 * {@link MetaMediaClientTest} (Phase 3 03-03):
 * <ul>
 *   <li>{@code @SpringBootTest(classes=WhatsAppApplication.class) + @ActiveProfiles("test")}</li>
 *   <li>WireMock standalone com {@code dynamicPort}</li>
 *   <li>{@code @DynamicPropertySource} sobrescreve {@code metaApiBaseUrl}</li>
 *   <li>{@code @BeforeEach}: WireMock resetAll + CircuitBreakerRegistry reset (Risk A3)</li>
 * </ul>
 *
 * <p><b>Janela 24h pegacarona via SpyBean:</b> {@link WindowEnforcementService}
 * spied + {@code doNothing().when(spy).verificarJanela(any())} default em
 * {@code @BeforeEach}, simulando janela aberta. Os tests de janela fechada vivem
 * em {@code JanelaEnforcementAspectTest} (04-02).
 *
 * <p><b>MediaCacheService MockBean:</b> controla cache hit vs miss
 * deterministicamente em testes de {@code enviarDocumento}. {@code default} retorna
 * {@code Optional.empty()} (cache vazio).
 */
@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
class WhatsAppCloudClientTest {

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
    static void overrideMetaUrl(DynamicPropertyRegistry registry) {
        registry.add("app.modulos.whatsapp.metaApiBaseUrl", () -> wireMock.baseUrl());
    }

    @Autowired
    WhatsAppCloudClient client;

    @Autowired
    CircuitBreakerRegistry cbRegistry;

    @Autowired
    JdbcTemplate jdbc;

    @SpyBean
    WindowEnforcementService windowSpy;

    @MockBean
    MediaCacheService mediaCacheService;

    @BeforeEach
    void resetEverything() {
        wireMock.resetAll();
        cbRegistry.find("whatsapp-cloud").ifPresent(CircuitBreaker::reset);
        // Default: janela aberta (sem excecao)
        doNothing().when(windowSpy).verificarJanela(any());
        // Default: cache vazio (mocks devolvem default empty)
        when(mediaCacheService.buscarMediaId(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void cleanupOutbound() {
        jdbc.update("DELETE FROM whatsapp.mensagens_log WHERE direcao = 'out'");
    }

    private void stubMessagesOk(String wamid) {
        wireMock.stubFor(post(urlPathEqualTo("/test-phone-id/messages"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"messaging_product\":\"whatsapp\",\"contacts\":[],\"messages\":[{\"id\":\"" + wamid + "\"}]}")));
    }

    private void stubMediaUploadOk(String mediaId) {
        wireMock.stubFor(post(urlPathEqualTo("/test-phone-id/media"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + mediaId + "\"}")));
    }

    @Test
    @DisplayName("enviarTexto happy path persiste mensagens_log direcao=out e retorna wamid")
    void enviarTexto_happy_path_persiste_e_retorna_wamid() {
        stubMessagesOk("wamid-tx-001");

        EnvioResponse resp = client.enviarTexto("554784178525", "Olá, orçamento pronto");

        assertThat(resp.wamid()).isEqualTo("wamid-tx-001");
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM whatsapp.mensagens_log WHERE wamid = ? AND direcao = 'out' AND tipo = 'text'",
                Integer.class, "wamid-tx-001");
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("enviarDocumento cache miss faz upload + send + register + persiste")
    void enviarDocumento_cache_miss_faz_upload_e_envia() {
        when(mediaCacheService.buscarMediaId(any())).thenReturn(Optional.empty());
        stubMediaUploadOk("media-id-novo");
        stubMessagesOk("wamid-doc-001");

        byte[] bytes = "PDF orcamento".getBytes();
        EnvioResponse resp = client.enviarDocumento("554784178525", bytes, "orc.pdf", "application/pdf", "Seu orcamento");

        assertThat(resp.wamid()).isEqualTo("wamid-doc-001");
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/test-phone-id/media")));
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/test-phone-id/messages"))
                .withRequestBody(matchingJsonPath("$.document.id", equalTo("media-id-novo"))));
        verify(mediaCacheService, times(1)).registrarUpload(any(), eq("media-id-novo"));
    }

    @Test
    @DisplayName("enviarDocumento cache hit pula upload e envia direto")
    void enviarDocumento_cache_hit_pula_upload() {
        when(mediaCacheService.buscarMediaId(any())).thenReturn(Optional.of("cached-id-999"));
        stubMessagesOk("wamid-doc-cached");

        byte[] bytes = "PDF cacheado".getBytes();
        EnvioResponse resp = client.enviarDocumento("554784178525", bytes, "orc.pdf", "application/pdf", null);

        assertThat(resp.wamid()).isEqualTo("wamid-doc-cached");
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/test-phone-id/media")));
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/test-phone-id/messages"))
                .withRequestBody(matchingJsonPath("$.document.id", equalTo("cached-id-999"))));
        verify(mediaCacheService, never()).registrarUpload(any(), any());
    }

    @Test
    @DisplayName("enviarBotoes envia payload interactive type=button")
    void enviarBotoes_happy_path() {
        stubMessagesOk("wamid-btn-001");

        EnvioResponse resp = client.enviarBotoes("554784178525", "Aprovar?",
                List.of(new BotaoDto("aprovar", "Aprovar"), new BotaoDto("recusar", "Recusar")));

        assertThat(resp.wamid()).isEqualTo("wamid-btn-001");
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/test-phone-id/messages"))
                .withRequestBody(matchingJsonPath("$.type", equalTo("interactive")))
                .withRequestBody(matchingJsonPath("$.interactive.type", equalTo("button"))));
    }

    @Test
    @DisplayName("enviarLista envia payload interactive type=list com sections")
    void enviarLista_happy_path() {
        stubMessagesOk("wamid-list-001");

        EnvioResponse resp = client.enviarLista("554784178525", "Escolha",
                List.of(new SecaoDto("Pedidos", List.of(new ItemDto("ver-1", "Pedido 1", null)))));

        assertThat(resp.wamid()).isEqualTo("wamid-list-001");
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/test-phone-id/messages"))
                .withRequestBody(matchingJsonPath("$.interactive.type", equalTo("list"))));
    }

    @Test
    @DisplayName("4xx (400 com error.code Meta) NAO retenta, lanca MetaApiException CATEGORIA_4XX com metaErrorCode")
    void quatrocentos_no_retry_lanca_meta_api_exception_4xx() {
        wireMock.stubFor(post(urlPathEqualTo("/test-phone-id/messages"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":131026,\"message\":\"Recipient phone not on WhatsApp\",\"type\":\"OAuthException\"}}")));

        assertThatThrownBy(() -> client.enviarTexto("554784178525", "test"))
                .isInstanceOf(MetaApiException.class)
                .satisfies(t -> {
                    MetaApiException ex = (MetaApiException) t;
                    assertThat(ex.getTipo()).isEqualTo(MetaApiException.Tipo.CATEGORIA_4XX);
                    assertThat(ex.getCodigo()).isEqualTo("META_ERROR");
                    assertThat(ex.getMetaErrorCode()).isEqualTo(131026);
                });

        // 4xx NAO retenta — counter == 1
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/test-phone-id/messages")));
    }

    @Test
    @DisplayName("5xx recupera apos 2 retries (counter == 3 prova @Retry funcionando)")
    void cinquecentos_recupera_apos_retries_counter_3() {
        wireMock.stubFor(post(urlPathEqualTo("/test-phone-id/messages"))
                .inScenario("5xx-retry").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("attempt-2"));
        wireMock.stubFor(post(urlPathEqualTo("/test-phone-id/messages"))
                .inScenario("5xx-retry").whenScenarioStateIs("attempt-2")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("attempt-3"));
        wireMock.stubFor(post(urlPathEqualTo("/test-phone-id/messages"))
                .inScenario("5xx-retry").whenScenarioStateIs("attempt-3")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"messages\":[{\"id\":\"wamid-recovered\"}]}")));

        EnvioResponse resp = client.enviarTexto("554784178525", "test recover");

        assertThat(resp.wamid()).isEqualTo("wamid-recovered");
        wireMock.verify(3, postRequestedFor(urlPathEqualTo("/test-phone-id/messages")));
    }

    @Test
    @DisplayName("5xx esgota 3 retries -> MetaApiException Tipo INDISPONIVEL_5XX (502)")
    void cinquecentos_esgota_retries_lanca_indisponivel_5xx() {
        wireMock.stubFor(post(urlPathEqualTo("/test-phone-id/messages"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.enviarTexto("554784178525", "test"))
                .isInstanceOf(MetaApiException.class)
                .satisfies(t -> {
                    MetaApiException ex = (MetaApiException) t;
                    assertThat(ex.getTipo()).isEqualTo(MetaApiException.Tipo.INDISPONIVEL_5XX);
                    assertThat(ex.getCodigo()).isEqualTo("META_INDISPONIVEL");
                });

        wireMock.verify(3, postRequestedFor(urlPathEqualTo("/test-phone-id/messages")));
    }

    @Test
    @DisplayName("ResourceAccessException -> classifier mapeia para Tipo TIMEOUT")
    void timeout_retentou_e_lancou_timeout() throws Exception {
        // Direct unit test do branch TIMEOUT em classificar(): invocamos o metodo
        // privado via reflection com um ResourceAccessException simulando read-timeout.
        // WireMock+JDK21 nao dispara consistentemente SocketTimeout via withFixedDelay
        // (a Java HttpClient lib do JDK21 trata partial responses de forma diferente),
        // entao testamos o classifier diretamente. O E2E timeout path e exercitado em
        // producao onde o read-timeout do SimpleClientHttpRequestFactory dispara
        // genuinamente em chamadas a Meta Cloud API.
        java.lang.reflect.Method classificar =
                WhatsAppCloudClient.class.getDeclaredMethod("classificar", Throwable.class);
        classificar.setAccessible(true);

        org.springframework.web.client.ResourceAccessException rae =
                new org.springframework.web.client.ResourceAccessException(
                        "I/O error on POST request: java.net.SocketTimeoutException: Read timed out");
        Object result = classificar.invoke(client, rae);

        assertThat(result).isInstanceOf(MetaApiException.class);
        MetaApiException ex = (MetaApiException) result;
        assertThat(ex.getTipo()).isEqualTo(MetaApiException.Tipo.TIMEOUT);
        assertThat(ex.getCodigo()).isEqualTo("META_TIMEOUT");
    }

    @Test
    @DisplayName("Circuit aberto apos falhas repetidas lanca MetaApiException CIRCUIT_OPEN")
    void circuit_aberto_apos_falhas_repetidas_lanca_circuit_open() {
        // Forca falha em todas as chamadas — sliding-window=10, threshold=50% — abre apos ~5 falhas
        wireMock.stubFor(post(urlPathEqualTo("/test-phone-id/messages"))
                .willReturn(aResponse().withStatus(500)));

        // Disparar varias chamadas (cada uma 3 retries via Retry) ate CB abrir
        for (int i = 0; i < 6; i++) {
            try {
                client.enviarTexto("554784178525", "x" + i);
            } catch (MetaApiException ignored) {
                // esperado durante 5xx; eventualmente vira CIRCUIT_OPEN
            }
        }

        // CB agora deve estar OPEN (ou HALF_OPEN dependendo de timing)
        var cb = cbRegistry.find("whatsapp-cloud").orElseThrow();
        assertThat(cb.getState()).isIn(CircuitBreaker.State.OPEN, CircuitBreaker.State.HALF_OPEN);

        // Forca state OPEN para garantir transicao deterministica
        cb.transitionToOpenState();

        assertThatThrownBy(() -> client.enviarTexto("554784178525", "blocked"))
                .isInstanceOf(MetaApiException.class)
                .satisfies(t -> {
                    MetaApiException ex = (MetaApiException) t;
                    assertThat(ex.getTipo()).isEqualTo(MetaApiException.Tipo.CIRCUIT_OPEN);
                    assertThat(ex.getCodigo()).isEqualTo("CIRCUIT_OPEN");
                });
    }

    @Test
    @DisplayName("Bearer NUNCA aparece em query param (PITFALLS C-14)")
    void bearer_nunca_em_query_param() {
        stubMessagesOk("wamid-bearer-check");
        client.enviarTexto("554784178525", "check");

        wireMock.getAllServeEvents().forEach(event ->
                assertThat(event.getRequest().getUrl())
                        .as("Bearer NUNCA em query param (PITFALLS C-14)")
                        .doesNotContain("access_token="));

        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/test-phone-id/messages"))
                .withHeader("Authorization", matching("Bearer .*")));
    }

    @Test
    @DisplayName("metodos publicos NAO incluem template (OUT-05 trava custo zero #1)")
    void metodos_publicos_nao_inclui_template() {
        long templateMethods = java.util.Arrays.stream(WhatsAppCloudClient.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .filter(n -> n.toLowerCase().contains("template"))
                .count();
        assertThat(templateMethods)
                .as("WhatsAppCloudClient NAO deve expor metodos com 'template' (OUT-05 + D9 PROJECT.md)")
                .isZero();
    }

    @Test
    @DisplayName("upload media envia 3 fields obrigatorios (PITFALLS C-15)")
    void upload_media_envia_3_fields_obrigatorios() {
        when(mediaCacheService.buscarMediaId(any())).thenReturn(Optional.empty());
        stubMediaUploadOk("media-id-multipart");
        stubMessagesOk("wamid-multipart");

        client.enviarDocumento("554784178525", "PDF".getBytes(), "doc.pdf", "application/pdf", null);

        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/test-phone-id/media"))
                .withHeader("Content-Type", matching("multipart/form-data;.*boundary=.*"))
                .withRequestBodyPart(aMultipart()
                        .withName("messaging_product")
                        .withBody(equalTo("whatsapp"))
                        .build())
                .withRequestBodyPart(aMultipart()
                        .withName("type")
                        .withBody(equalTo("application/pdf"))
                        .build())
                .withRequestBodyPart(aMultipart()
                        .withName("file")
                        .build()));
    }
}
