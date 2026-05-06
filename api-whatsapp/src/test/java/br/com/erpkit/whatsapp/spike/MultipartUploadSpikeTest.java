package br.com.erpkit.whatsapp.spike;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spike Wave 0 (Phase 4) — valida empiricamente que Spring RestClient + MultiValueMap +
 * ByteArrayResource + MediaType.MULTIPART_FORM_DATA produz multipart corretamente para
 * o endpoint Cloud API Meta {@code /v22.0/{phoneNumberId}/media}:
 * <ul>
 *   <li>Boundary auto-injetado (PITFALLS Pitfall 5 RESEARCH §Pattern Multipart)</li>
 *   <li>3 fields obrigatorios: {@code messaging_product}, {@code type}, {@code file}
 *       (PITFALLS C-15)</li>
 *   <li>Bearer NUNCA em query param — sempre Authorization header (PITFALLS C-14)</li>
 * </ul>
 *
 * <p>Sem este spike, 04-04 ({@code WhatsAppCloudClient.uploadMedia}) seria construido
 * contra pattern textual de RESEARCH sem prova empirica em Boot 3.5.9 + WireMock 3.10.0.
 * Mesmo padrao de Phase 1 spike {@code OnConflictSpikeTest} (Wave 0 antes de Wave 1 do
 * Plan 02-01).
 */
@SpringBootTest(classes = WhatsAppApplication.class)
@ActiveProfiles("test")
@DisplayName("Spike Wave 0 — multipart Cloud API upload via Spring RestClient")
class MultipartUploadSpikeTest {

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
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Test
    @DisplayName("upload multipart envia 3 fields obrigatorios + Bearer header (sem query)")
    void upload_multipart_envia_3_fields_e_bearer_header_sem_query() {
        // Stub: Meta retorna media_id
        wireMock.stubFor(post(urlPathEqualTo("/test-phone-id/media"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"meta-media-id-123\"}")));

        // Construcao do multipart per RESEARCH §Code Examples §2.
        // ByteArrayResource override do getFilename() e CRUCIAL — sem nome o Spring
        // RestClient nao serializa como filename no multipart part (RESEARCH §Pitfall 5).
        byte[] bytes = "hello pdf bytes".getBytes();
        ByteArrayResource fileResource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "test.pdf";
            }
        };
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("messaging_product", "whatsapp");
        parts.add("type", "application/pdf");
        parts.add("file", fileResource);

        RestClient restClient = RestClient.builder()
                .baseUrl(wireMock.baseUrl())
                .build();

        @SuppressWarnings("rawtypes")
        Map response = restClient.post()
                .uri("/{phoneNumberId}/media", "test-phone-id")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-access-token")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(Map.class);

        // ASSERTION 1: response shape correto (Meta retorna {"id":"<media-id>"}).
        assertThat(response).isNotNull();
        assertThat(response.get("id")).isEqualTo("meta-media-id-123");

        // ASSERTION 2: WireMock recebeu request com Content-Type multipart com boundary
        // auto-injetado pelo Spring RestClient (RESEARCH §Pitfall 5).
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/test-phone-id/media"))
                .withHeader("Content-Type", matching("multipart/form-data;.*boundary=.*")));

        // ASSERTION 3 (PITFALLS C-15): 3 fields obrigatorios presentes no body multipart.
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/test-phone-id/media"))
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

        // ASSERTION 4 (PITFALLS C-14): Bearer header presente, sem query param access_token=.
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/test-phone-id/media"))
                .withHeader("Authorization", equalTo("Bearer test-access-token")));
        wireMock.getAllServeEvents().forEach(event ->
                assertThat(event.getRequest().getUrl())
                        .as("Bearer NUNCA em query param (PITFALLS C-14)")
                        .doesNotContain("access_token="));
    }
}
