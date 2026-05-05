package br.com.erpkit.whatsapp.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
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
import org.springframework.web.client.HttpServerErrorException;

import java.util.Optional;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import br.com.erpkit.whatsapp.dto.MetaMediaResultado;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de integracao do {@link MetaMediaClient} com WireMock 3.10.0 standalone
 * (Jetty 12 shadow — evita conflito com Boot 3.5.9).
 *
 * <p>Cobertura (Plan 03-03 success criteria):
 * <ul>
 *   <li>happy_path 2-step download: 200 metadata + 200 bytes -&gt; Resultado completo</li>
 *   <li>404 step 1: URL expirada antes do step 1 -&gt; {@link Optional#empty}</li>
 *   <li>404 step 2: metadata ok mas URL absoluta retorna 404 -&gt; {@link Optional#empty}</li>
 *   <li>5xx step 1: sem retry (sem Resilience4j neste cliente) — propaga
 *       {@link HttpServerErrorException}; counter == 1 confirma A6 indiretamente</li>
 *   <li>bearer_header_presente: ambas requests carregam {@code Authorization: Bearer ...}</li>
 *   <li>bearer_nunca_em_query_param: PITFALLS C-14 — nenhuma URL contem
 *       {@code ?access_token=}</li>
 * </ul>
 *
 * <p><b>Setup:</b> {@link WireMockServer} static iniciado em {@code @BeforeAll}
 * com {@code dynamicPort()}; {@link DynamicPropertySource} sobrescreve
 * {@code app.modulos.whatsapp.metaApiBaseUrl} com {@code wm.baseUrl()} antes do
 * Spring context inicializar — {@link MetaMediaClient}{@code .restClient} fica
 * apontado para o mock.
 *
 * <p>Token de teste vem do {@code application-test.yml} linha 76:
 * {@code accessToken: test-access-token}.
 */
@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
class MetaMediaClientTest {

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
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("app.modulos.whatsapp.metaApiBaseUrl", () -> wireMock.baseUrl());
    }

    @Autowired
    private MetaMediaClient client;

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Test
    @DisplayName("Happy path: step 1 retorna metadata + step 2 retorna bytes — Optional preenchido")
    void happy_path_2_step_retorna_resultado_completo() {
        // Step 1: metadata
        wireMock.stubFor(get(urlEqualTo("/MEDIA-ID-001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "url": "%s/cdn/MEDIA-ID-001/data",
                                  "mime_type": "application/pdf",
                                  "filename": "fatura.pdf",
                                  "sha256": "abc123",
                                  "file_size": 12345,
                                  "id": "MEDIA-ID-001",
                                  "messaging_product": "whatsapp"
                                }
                                """.formatted(wireMock.baseUrl()))));
        // Step 2: bytes
        wireMock.stubFor(get(urlEqualTo("/cdn/MEDIA-ID-001/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(new byte[] {1, 2, 3, 4, 5})));

        Optional<MetaMediaResultado> resultado = client.baixar("MEDIA-ID-001");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().bytes()).containsExactly(1, 2, 3, 4, 5);
        assertThat(resultado.get().mimeType()).isEqualTo("application/pdf");
        assertThat(resultado.get().filename()).isEqualTo("fatura.pdf");

        // Assert: ambas requests com Bearer header. Token vem do application-test.yml.
        wireMock.verify(getRequestedFor(urlEqualTo("/MEDIA-ID-001"))
                .withHeader("Authorization", equalTo("Bearer test-access-token")));
        wireMock.verify(getRequestedFor(urlEqualTo("/cdn/MEDIA-ID-001/data"))
                .withHeader("Authorization", equalTo("Bearer test-access-token")));
    }

    @Test
    @DisplayName("404 no step 1: Optional.empty + step 2 NAO chamado (URL expirada antes do listener rodar)")
    void quatrocentos_quatro_step_1_retorna_empty() {
        wireMock.stubFor(get(urlEqualTo("/MEDIA-ID-EXPIRED"))
                .willReturn(aResponse().withStatus(404).withBody("{\"error\":\"expired\"}")));

        Optional<MetaMediaResultado> resultado = client.baixar("MEDIA-ID-EXPIRED");

        assertThat(resultado).isEmpty();
        // Step 2 nao deve ter sido chamado — short-circuit no 404 do step 1.
        wireMock.verify(0, getRequestedFor(urlMatching("/cdn/.*")));
    }

    @Test
    @DisplayName("404 no step 2: step 1 ok mas URL absoluta retorna 404 — Optional.empty")
    void quatrocentos_quatro_step_2_retorna_empty() {
        wireMock.stubFor(get(urlEqualTo("/MEDIA-ID-002"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "url": "%s/cdn/MEDIA-ID-002/data",
                                  "mime_type": "image/jpeg",
                                  "filename": "foto.jpg"
                                }
                                """.formatted(wireMock.baseUrl()))));
        wireMock.stubFor(get(urlEqualTo("/cdn/MEDIA-ID-002/data"))
                .willReturn(aResponse().withStatus(404)));

        Optional<MetaMediaResultado> resultado = client.baixar("MEDIA-ID-002");

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("5xx no step 1: sem retry (sem Resilience4j neste cliente) — excecao propaga; counter == 1")
    void cinco_centos_step_1_propaga_sem_retry() {
        wireMock.stubFor(get(urlEqualTo("/MEDIA-ID-500"))
                .willReturn(aResponse().withStatus(500)));

        // Espera-se que client.baixar lance HttpServerErrorException (sem captura interna).
        // Listener Wave 5 tem try/catch generico em volta da chamada.
        assertThatThrownBy(() -> client.baixar("MEDIA-ID-500"))
                .isInstanceOf(HttpServerErrorException.class);

        // Counter == 1 (sem retry — sem Resilience4j neste cliente, por design D-04).
        wireMock.verify(1, getRequestedFor(urlEqualTo("/MEDIA-ID-500")));
    }

    @Test
    @DisplayName("Bearer NUNCA em query param (regressao PITFALLS C-14)")
    void bearer_nunca_em_query_param() {
        wireMock.stubFor(get(urlEqualTo("/MEDIA-ID-003"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "url": "%s/cdn/MEDIA-ID-003/data",
                                  "mime_type": "application/pdf",
                                  "filename": "f.pdf"
                                }
                                """.formatted(wireMock.baseUrl()))));
        wireMock.stubFor(get(urlEqualTo("/cdn/MEDIA-ID-003/data"))
                .willReturn(aResponse().withStatus(200).withBody(new byte[]{1, 2, 3})));

        client.baixar("MEDIA-ID-003");

        // Assert: NENHUMA das requests recebidas teve "?access_token=" na query string.
        // Validamos via getAllServeEvents (lista de RecordedRequest do WireMock).
        wireMock.getAllServeEvents().forEach(event -> {
            String url = event.getRequest().getUrl();
            assertThat(url)
                    .as("PITFALLS C-14: token NUNCA em query param")
                    .doesNotContain("access_token=");
        });
    }

    @Test
    @DisplayName("Metadata sem URL: defesa em profundidade — Optional.empty + log.warn")
    void metadata_sem_url_retorna_empty() {
        // Step 1 retorna 200 mas o JSON nao tem campo "url" — defesa contra Meta
        // mudar contrato sem aviso.
        wireMock.stubFor(get(urlEqualTo("/MEDIA-ID-004"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "mime_type": "image/png",
                                  "id": "MEDIA-ID-004"
                                }
                                """)));

        Optional<MetaMediaResultado> resultado = client.baixar("MEDIA-ID-004");

        assertThat(resultado).isEmpty();
        // Step 2 nao chamado (sem URL para chamar).
        wireMock.verify(0, getRequestedFor(urlMatching("/cdn/.*")));
    }
}
