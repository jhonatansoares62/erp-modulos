package br.com.erpkit.whatsapp.client;

import br.com.erpkit.whatsapp.client.dto.BotaoDto;
import br.com.erpkit.whatsapp.client.dto.EnvioResponse;
import br.com.erpkit.whatsapp.client.dto.ItemDto;
import br.com.erpkit.whatsapp.client.dto.MetaConfigRequest;
import br.com.erpkit.whatsapp.client.dto.MetaConfigResponse;
import br.com.erpkit.whatsapp.client.dto.ResumoUsoResponse;
import br.com.erpkit.whatsapp.client.dto.SecaoDto;
import br.com.erpkit.whatsapp.client.dto.StatusResponse;
import br.com.erpkit.whatsapp.client.dto.WhatsAppRespostaDto;
import br.com.erpkit.whatsapp.client.exception.WhatsAppException;
import br.com.erpkit.whatsapp.client.exception.WhatsAppIndisponivelException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests do {@link WhatsAppClientImpl} contra o contrato REST do
 * api-whatsapp mockado via WireMock (QA-02). Cobre: 4 envios + status + despachar
 * + Resilience4j (4xx sem retry, 5xx retry/esgota, conexao recusada) + header
 * {@code X-API-Key} condicional + {@code isOnline} + guard de modulo desabilitado.
 *
 * <p>Sem Spring: o client e instanciado direto ({@code new WhatsAppClientImpl(props)})
 * apontando para {@code wireMock.baseUrl()}. Um client novo por teste ({@code @BeforeEach})
 * garante circuit breaker limpo (a instancia de CB e criada no construtor).
 */
class WhatsAppClientImplWireMockTest {

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

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    private static WhatsAppProperties props(String url, boolean enabled, String apiKey) {
        WhatsAppProperties p = new WhatsAppProperties();
        p.setUrl(url);
        p.setEnabled(enabled);
        p.setApiKey(apiKey);
        p.setTimeout(Duration.ofSeconds(2));
        return p;
    }

    private WhatsAppClientImpl client() {
        return new WhatsAppClientImpl(props(wireMock.baseUrl(), true, null));
    }

    private void stubEnvioOk(String path, String wamid) {
        wireMock.stubFor(post(urlPathEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"wamid\":\"" + wamid + "\"}")));
    }

    @Test
    @DisplayName("enviarTexto faz POST /api/whatsapp/enviar-texto e retorna wamid")
    void enviarTexto_happy() {
        stubEnvioOk("/api/whatsapp/enviar-texto", "wamid-tx-1");

        EnvioResponse resp = client().enviarTexto("554784178525", "Olá, orçamento pronto");

        assertThat(resp.wamid()).isEqualTo("wamid-tx-1");
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/api/whatsapp/enviar-texto"))
                .withRequestBody(matchingJsonPath("$.telefone", equalTo("554784178525")))
                .withRequestBody(matchingJsonPath("$.texto", equalTo("Olá, orçamento pronto"))));
    }

    @Test
    @DisplayName("enviarDocumento codifica bytes em base64 no campo mediaBase64")
    void enviarDocumento_codifica_base64() {
        stubEnvioOk("/api/whatsapp/enviar-documento", "wamid-doc-1");
        byte[] bytes = "PDF orcamento".getBytes();
        String esperadoB64 = Base64.getEncoder().encodeToString(bytes);

        EnvioResponse resp = client().enviarDocumento("554784178525", bytes, "orc.pdf", "application/pdf", "Seu orcamento");

        assertThat(resp.wamid()).isEqualTo("wamid-doc-1");
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/api/whatsapp/enviar-documento"))
                .withRequestBody(matchingJsonPath("$.mediaBase64", equalTo(esperadoB64)))
                .withRequestBody(matchingJsonPath("$.mimeType", equalTo("application/pdf")))
                .withRequestBody(matchingJsonPath("$.filename", equalTo("orc.pdf")))
                .withRequestBody(matchingJsonPath("$.caption", equalTo("Seu orcamento"))));
    }

    @Test
    @DisplayName("enviarBotoes serializa a lista de botoes")
    void enviarBotoes_happy() {
        stubEnvioOk("/api/whatsapp/enviar-botoes", "wamid-btn-1");

        EnvioResponse resp = client().enviarBotoes("554784178525", "Aprovar?",
                List.of(new BotaoDto("aprovar", "Aprovar"), new BotaoDto("recusar", "Recusar")));

        assertThat(resp.wamid()).isEqualTo("wamid-btn-1");
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/api/whatsapp/enviar-botoes"))
                .withRequestBody(matchingJsonPath("$.botoes[0].id", equalTo("aprovar")))
                .withRequestBody(matchingJsonPath("$.botoes[0].title", equalTo("Aprovar"))));
    }

    @Test
    @DisplayName("enviarLista serializa secoes e itens")
    void enviarLista_happy() {
        stubEnvioOk("/api/whatsapp/enviar-lista", "wamid-list-1");

        EnvioResponse resp = client().enviarLista("554784178525", "Escolha",
                List.of(new SecaoDto("Pedidos", List.of(new ItemDto("ver-1", "Pedido 1", "detalhe")))));

        assertThat(resp.wamid()).isEqualTo("wamid-list-1");
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/api/whatsapp/enviar-lista"))
                .withRequestBody(matchingJsonPath("$.secoes[0].titulo", equalTo("Pedidos")))
                .withRequestBody(matchingJsonPath("$.secoes[0].itens[0].id", equalTo("ver-1"))));
    }

    @Test
    @DisplayName("status faz GET /api/whatsapp/status e desserializa StatusResponse")
    void status_happy() {
        wireMock.stubFor(get(urlPathEqualTo("/api/whatsapp/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"UP\",\"circuitBreakerState\":\"CLOSED\",\"phoneNumberId\":\"123\"}")));

        StatusResponse status = client().status();

        assertThat(status.status()).isEqualTo("UP");
        assertThat(status.circuitBreakerState()).isEqualTo("CLOSED");
        assertThat(status.phoneNumberId()).isEqualTo("123");
    }

    @Test
    @DisplayName("obterConfig faz GET /api/whatsapp/config e desserializa MetaConfigResponse")
    void obterConfig_happy() {
        wireMock.stubFor(get(urlPathEqualTo("/api/whatsapp/config"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"phoneNumberId\":\"123\",\"accessTokenConfigurado\":true,"
                                + "\"appSecretConfigurado\":true,\"verifyTokenConfigurado\":false,"
                                + "\"configurado\":false,\"atualizadoEm\":\"2026-07-10T12:00:00Z\"}")));

        MetaConfigResponse cfg = client().obterConfig();

        assertThat(cfg.phoneNumberId()).isEqualTo("123");
        assertThat(cfg.accessTokenConfigurado()).isTrue();
        assertThat(cfg.verifyTokenConfigurado()).isFalse();
        assertThat(cfg.configurado()).isFalse();
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/whatsapp/config")));
    }

    @Test
    @DisplayName("relatorioResumo faz GET /relatorios/resumo com de/ate e desserializa ResumoUsoResponse")
    void relatorioResumo_happy() {
        wireMock.stubFor(get(urlPathEqualTo("/api/whatsapp/relatorios/resumo"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"de\":\"2026-07-01T00:00:00Z\",\"ate\":\"2026-07-31T00:00:00Z\","
                                + "\"total\":4,\"entrada\":1,\"saida\":3,\"faturaveis\":2,"
                                + "\"porTipo\":{\"text\":3,\"interactive_list\":1},"
                                + "\"statusSaida\":{\"delivered\":1,\"read\":1,\"failed\":1},"
                                + "\"categoriaSaida\":{\"service\":2,\"sem_categoria\":1}}")));

        ResumoUsoResponse r = client().relatorioResumo("2026-07-01T00:00:00Z", "2026-07-31T00:00:00Z");

        assertThat(r.total()).isEqualTo(4);
        assertThat(r.entrada()).isEqualTo(1);
        assertThat(r.saida()).isEqualTo(3);
        assertThat(r.faturaveis()).isEqualTo(2);
        assertThat(r.porTipo()).containsEntry("text", 3L).containsEntry("interactive_list", 1L);
        assertThat(r.statusSaida()).containsEntry("failed", 1L);
        assertThat(r.categoriaSaida()).containsEntry("service", 2L).containsEntry("sem_categoria", 1L);
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/api/whatsapp/relatorios/resumo"))
                .withQueryParam("de", equalTo("2026-07-01T00:00:00Z"))
                .withQueryParam("ate", equalTo("2026-07-31T00:00:00Z")));
    }

    @Test
    @DisplayName("salvarConfig faz PUT /api/whatsapp/config com as credenciais no body")
    void salvarConfig_happy() {
        wireMock.stubFor(put(urlPathEqualTo("/api/whatsapp/config"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"phoneNumberId\":\"55999\",\"accessTokenConfigurado\":true,"
                                + "\"appSecretConfigurado\":true,\"verifyTokenConfigurado\":true,"
                                + "\"configurado\":true,\"atualizadoEm\":\"2026-07-10T13:00:00Z\"}")));

        MetaConfigResponse cfg = client().salvarConfig(
                new MetaConfigRequest("55999", "tok", "sec", "ver"));

        assertThat(cfg.configurado()).isTrue();
        wireMock.verify(1, putRequestedFor(urlPathEqualTo("/api/whatsapp/config"))
                .withRequestBody(matchingJsonPath("$.phoneNumberId", equalTo("55999")))
                .withRequestBody(matchingJsonPath("$.accessToken", equalTo("tok")))
                .withRequestBody(matchingJsonPath("$.appSecret", equalTo("sec")))
                .withRequestBody(matchingJsonPath("$.verifyToken", equalTo("ver"))));
    }

    @Test
    @DisplayName("despachar(TEXTO) roteia para enviarTexto")
    void despachar_texto() {
        stubEnvioOk("/api/whatsapp/enviar-texto", "wamid-desp-1");

        EnvioResponse resp = client().despachar("554784178525", WhatsAppRespostaDto.texto("resposta"));

        assertThat(resp.wamid()).isEqualTo("wamid-desp-1");
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/api/whatsapp/enviar-texto")));
    }

    @Test
    @DisplayName("despachar(null) retorna null sem chamar HTTP")
    void despachar_null_sem_http() {
        EnvioResponse resp = client().despachar("554784178525", null);

        assertThat(resp).isNull();
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/api/whatsapp/enviar-texto")));
    }

    @Test
    @DisplayName("4xx NAO retenta e lanca WhatsAppException com status")
    void quatrocentos_nao_retenta() {
        wireMock.stubFor(post(urlPathEqualTo("/api/whatsapp/enviar-texto"))
                .willReturn(aResponse().withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"codigo\":\"JANELA_24H_FECHADA\"}")));

        assertThatThrownBy(() -> client().enviarTexto("554784178525", "fora da janela"))
                .isInstanceOf(WhatsAppException.class)
                .satisfies(t -> assertThat(((WhatsAppException) t).getStatus()).isEqualTo(409));

        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/api/whatsapp/enviar-texto")));
    }

    @Test
    @DisplayName("5xx retenta e recupera (counter == 3 prova o @Retry)")
    void cinquecentos_retenta_e_recupera() {
        wireMock.stubFor(post(urlPathEqualTo("/api/whatsapp/enviar-texto"))
                .inScenario("5xx").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500)).willSetStateTo("t2"));
        wireMock.stubFor(post(urlPathEqualTo("/api/whatsapp/enviar-texto"))
                .inScenario("5xx").whenScenarioStateIs("t2")
                .willReturn(aResponse().withStatus(500)).willSetStateTo("t3"));
        wireMock.stubFor(post(urlPathEqualTo("/api/whatsapp/enviar-texto"))
                .inScenario("5xx").whenScenarioStateIs("t3")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"wamid\":\"wamid-recuperado\"}")));

        EnvioResponse resp = client().enviarTexto("554784178525", "recupera");

        assertThat(resp.wamid()).isEqualTo("wamid-recuperado");
        wireMock.verify(3, postRequestedFor(urlPathEqualTo("/api/whatsapp/enviar-texto")));
    }

    @Test
    @DisplayName("5xx persistente esgota 3 tentativas e lanca WhatsAppException")
    void cinquecentos_esgota() {
        wireMock.stubFor(post(urlPathEqualTo("/api/whatsapp/enviar-texto"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client().enviarTexto("554784178525", "indisponivel"))
                .isInstanceOf(WhatsAppException.class)
                .satisfies(t -> assertThat(((WhatsAppException) t).getStatus()).isEqualTo(503));

        wireMock.verify(3, postRequestedFor(urlPathEqualTo("/api/whatsapp/enviar-texto")));
    }

    @Test
    @DisplayName("conexao recusada lanca WhatsAppIndisponivelException")
    void conexao_recusada_indisponivel() {
        // porta fechada — connection refused vira ResourceAccessException (RestClientException)
        WhatsAppClientImpl offline = new WhatsAppClientImpl(props("http://localhost:59999", true, null));

        assertThatThrownBy(() -> offline.enviarTexto("554784178525", "sem servidor"))
                .isInstanceOf(WhatsAppIndisponivelException.class);
    }

    @Test
    @DisplayName("apiKey configurada envia header X-API-Key")
    void apiKey_envia_header() {
        stubEnvioOk("/api/whatsapp/enviar-texto", "wamid-key");
        WhatsAppClientImpl comKey = new WhatsAppClientImpl(props(wireMock.baseUrl(), true, "secret-123"));

        comKey.enviarTexto("554784178525", "com key");

        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/api/whatsapp/enviar-texto"))
                .withHeader("X-API-Key", equalTo("secret-123")));
    }

    @Test
    @DisplayName("apiKey ausente NAO envia header X-API-Key")
    void apiKey_ausente_sem_header() {
        stubEnvioOk("/api/whatsapp/enviar-texto", "wamid-nokey");

        client().enviarTexto("554784178525", "sem key");

        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/api/whatsapp/enviar-texto"))
                .withHeader("X-API-Key", absent()));
    }

    @Test
    @DisplayName("isOnline true quando /health responde status UP")
    void isOnline_true() {
        wireMock.stubFor(get(urlPathEqualTo("/health"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"UP\"}")));

        assertThat(client().isOnline()).isTrue();
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/health")));
    }

    @Test
    @DisplayName("isOnline false quando /health responde erro (sem lancar excecao)")
    void isOnline_false_em_erro() {
        wireMock.stubFor(get(urlPathEqualTo("/health"))
                .willReturn(aResponse().withStatus(503)));

        assertThat(client().isOnline()).isFalse();
    }

    @Test
    @DisplayName("modulo desabilitado lanca WhatsAppIndisponivelException sem chamar HTTP")
    void desabilitado_sem_http() {
        WhatsAppClientImpl desligado = new WhatsAppClientImpl(props(wireMock.baseUrl(), false, null));

        assertThatThrownBy(() -> desligado.enviarTexto("554784178525", "off"))
                .isInstanceOf(WhatsAppIndisponivelException.class);
        assertThat(desligado.isHabilitado()).isFalse();
        assertThat(desligado.isOnline()).isFalse();
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/api/whatsapp/enviar-texto")));
    }
}
