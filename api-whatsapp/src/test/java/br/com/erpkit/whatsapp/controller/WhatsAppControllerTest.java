package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.dto.EnvioResponse;
import br.com.erpkit.whatsapp.exception.JanelaConversaFechadaException;
import br.com.erpkit.whatsapp.exception.MetaApiException;
import br.com.erpkit.whatsapp.service.WhatsAppCloudClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests do {@link WhatsAppController} via {@code @WebMvcTest} — sem WireMock,
 * sem Spring Boot full context. Mocka {@link WhatsAppCloudClient}, {@link
 * WhatsAppProperties} e {@link CircuitBreakerRegistry} para validar:
 * <ul>
 *   <li>5 endpoints happy path (200)</li>
 *   <li>Bean Validation 400 paths (telefone vazio, telefone com letras, base64
 *       invalido, 4 botoes, 11 itens cross-secao)</li>
 *   <li>Excecoes propagadas via {@code GlobalExceptionHandler} (409, 422, 502, 503)</li>
 *   <li>Status endpoint le state via {@code cbRegistry.find}</li>
 * </ul>
 *
 * <p>{@code @AutoConfigureMockMvc(addFilters = false)} bypass {@code ApiKeyFilter}
 * (Phase 1 SecurityConfig herdado) — pattern alinhado com WebhookControllerTest.
 */
@WebMvcTest(WhatsAppController.class)
@AutoConfigureMockMvc(addFilters = false)
class WhatsAppControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockBean WhatsAppCloudClient cloudClient;
    @MockBean WhatsAppProperties properties;
    @MockBean CircuitBreakerRegistry cbRegistry;

    @BeforeEach
    void setupDefaults() {
        when(properties.getPhoneNumberId()).thenReturn("test-phone-id");
    }

    @Test
    @DisplayName("POST /enviar-texto happy path retorna 200 + wamid")
    void enviar_texto_happy_200() throws Exception {
        when(cloudClient.enviarTexto(anyString(), anyString())).thenReturn(new EnvioResponse("wamid-tx-001"));

        mvc.perform(post("/api/whatsapp/enviar-texto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"554784178525\",\"texto\":\"Olá\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wamid").value("wamid-tx-001"));
    }

    @Test
    @DisplayName("POST /enviar-texto com telefone vazio retorna 400 + campos.telefone")
    void enviar_texto_validation_400_telefone_vazio() throws Exception {
        mvc.perform(post("/api/whatsapp/enviar-texto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"\",\"texto\":\"hi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.telefone").exists());
    }

    @Test
    @DisplayName("POST /enviar-texto com telefone com letras retorna 400")
    void enviar_texto_validation_400_telefone_letras() throws Exception {
        mvc.perform(post("/api/whatsapp/enviar-texto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"abc123\",\"texto\":\"hi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.telefone").exists());
    }

    @Test
    @DisplayName("POST /enviar-documento com mediaBase64 invalido retorna 400")
    void enviar_documento_base64_invalido_400() throws Exception {
        mvc.perform(post("/api/whatsapp/enviar-documento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"554784178525\",\"mediaBase64\":\"!!!\",\"mimeType\":\"application/pdf\",\"filename\":\"a.pdf\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("mediaBase64 invalido")));
    }

    @Test
    @DisplayName("POST /enviar-botoes happy path com 2 botoes retorna 200")
    void enviar_botoes_happy_200() throws Exception {
        when(cloudClient.enviarBotoes(anyString(), anyString(), anyList())).thenReturn(new EnvioResponse("wamid-btn-1"));

        String body = "{\"telefone\":\"554784178525\",\"texto\":\"Aprovar?\",\"botoes\":[" +
                "{\"id\":\"aprovar\",\"title\":\"Aprovar\"}," +
                "{\"id\":\"recusar\",\"title\":\"Recusar\"}]}";

        mvc.perform(post("/api/whatsapp/enviar-botoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wamid").value("wamid-btn-1"));
    }

    @Test
    @DisplayName("POST /enviar-botoes com 4 botoes retorna 400 (Cloud API limit OUT-03)")
    void enviar_botoes_validation_400_4_botoes() throws Exception {
        String body = "{\"telefone\":\"554784178525\",\"texto\":\"x\",\"botoes\":[" +
                "{\"id\":\"a\",\"title\":\"A\"},{\"id\":\"b\",\"title\":\"B\"}," +
                "{\"id\":\"c\",\"title\":\"C\"},{\"id\":\"d\",\"title\":\"D\"}]}";

        mvc.perform(post("/api/whatsapp/enviar-botoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.botoes").exists());
    }

    @Test
    @DisplayName("POST /enviar-lista com total 11 itens cross-secao retorna 400 (@AssertTrue OUT-04)")
    void enviar_lista_validation_400_total_11_itens() throws Exception {
        StringBuilder sb = new StringBuilder("{\"telefone\":\"554784178525\",\"texto\":\"x\",\"secoes\":[{\"titulo\":\"S1\",\"itens\":[");
        for (int i = 0; i < 11; i++) {
            sb.append("{\"id\":\"i").append(i).append("\",\"title\":\"T").append(i).append("\"}");
            if (i < 10) sb.append(",");
        }
        sb.append("]}]}");

        mvc.perform(post("/api/whatsapp/enviar-lista")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sb.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.totalItensValido").exists());
    }

    @Test
    @DisplayName("POST /enviar-lista com 2 secoes 5+5 itens retorna 200")
    void enviar_lista_happy_200() throws Exception {
        when(cloudClient.enviarLista(anyString(), anyString(), anyList())).thenReturn(new EnvioResponse("wamid-list-1"));

        String body = "{\"telefone\":\"554784178525\",\"texto\":\"Escolha\",\"secoes\":[" +
                "{\"titulo\":\"S1\",\"itens\":[{\"id\":\"i1\",\"title\":\"T1\"},{\"id\":\"i2\",\"title\":\"T2\"}]}," +
                "{\"titulo\":\"S2\",\"itens\":[{\"id\":\"i3\",\"title\":\"T3\"}]}]}";

        mvc.perform(post("/api/whatsapp/enviar-lista")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wamid").value("wamid-list-1"));
    }

    @Test
    @DisplayName("Janela 24h fechada propaga 409 + codigo=JANELA_24H_FECHADA")
    void janela_fechada_retorna_409_codigo_janela() throws Exception {
        when(cloudClient.enviarTexto(anyString(), anyString()))
                .thenThrow(new JanelaConversaFechadaException("554784178525", null));

        mvc.perform(post("/api/whatsapp/enviar-texto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"554784178525\",\"texto\":\"hi\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("JANELA_24H_FECHADA"));
    }

    @Test
    @DisplayName("Meta 4xx propaga 422 + codigo=META_ERROR + metaErrorCode")
    void meta_4xx_retorna_422_codigo_meta_error() throws Exception {
        when(cloudClient.enviarTexto(anyString(), anyString()))
                .thenThrow(new MetaApiException(MetaApiException.Tipo.CATEGORIA_4XX, 131026, "Recipient phone not on WhatsApp"));

        mvc.perform(post("/api/whatsapp/enviar-texto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"554784178525\",\"texto\":\"hi\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("META_ERROR"))
                .andExpect(jsonPath("$.metaErrorCode").value(131026));
    }

    @Test
    @DisplayName("Meta 5xx propaga 502 + codigo=META_INDISPONIVEL")
    void meta_5xx_retorna_502() throws Exception {
        when(cloudClient.enviarTexto(anyString(), anyString()))
                .thenThrow(new MetaApiException(MetaApiException.Tipo.INDISPONIVEL_5XX, null, "Meta down"));

        mvc.perform(post("/api/whatsapp/enviar-texto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"554784178525\",\"texto\":\"hi\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigo").value("META_INDISPONIVEL"));
    }

    @Test
    @DisplayName("Circuit aberto propaga 503 + codigo=CIRCUIT_OPEN")
    void circuit_open_retorna_503() throws Exception {
        when(cloudClient.enviarTexto(anyString(), anyString()))
                .thenThrow(new MetaApiException(MetaApiException.Tipo.CIRCUIT_OPEN, null, "circuit open"));

        mvc.perform(post("/api/whatsapp/enviar-texto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"554784178525\",\"texto\":\"hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("CIRCUIT_OPEN"));
    }

    @Test
    @DisplayName("GET /status retorna circuitBreakerState + phoneNumberId (D-04 minimal)")
    void status_endpoint_retorna_state_cb() throws Exception {
        CircuitBreaker cb = org.mockito.Mockito.mock(CircuitBreaker.class);
        when(cb.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(cbRegistry.find("whatsapp-cloud")).thenReturn(Optional.of(cb));

        mvc.perform(get("/api/whatsapp/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.circuitBreakerState").value("CLOSED"))
                .andExpect(jsonPath("$.phoneNumberId").value("test-phone-id"));
    }
}
