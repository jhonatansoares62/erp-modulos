package br.com.erpkit.email.controller;

import br.com.erpkit.email.dto.EmailResponse;
import br.com.erpkit.email.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmailController.class)
class EmailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/emails deve retornar 201 ao criar email")
    void deveRetornar201AoCriarEmail() throws Exception {
        EmailResponse response = criarEmailResponse(1L, "pendente");
        when(emailService.criar(any())).thenReturn(response);

        String json = """
                {
                    "destinatario": "dest@example.com",
                    "assunto": "Teste",
                    "corpo": "Corpo do email"
                }
                """;

        mockMvc.perform(post("/api/emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "test-key-123")
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("pendente"))
                .andExpect(jsonPath("$.destinatario").value("dest@example.com"));
    }

    @Test
    @DisplayName("GET /api/emails/{id} deve retornar 200 com email")
    void deveRetornar200AoBuscarEmail() throws Exception {
        EmailResponse response = criarEmailResponse(1L, "pendente");
        when(emailService.buscar(1L)).thenReturn(response);

        mockMvc.perform(get("/api/emails/1")
                        .header("X-API-Key", "test-key-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.destinatario").value("dest@example.com"));
    }

    @Test
    @DisplayName("GET /api/emails/estatisticas deve retornar mapa de contagens")
    void deveRetornarEstatisticas() throws Exception {
        Map<String, Long> stats = Map.of("pendente", 5L, "enviado", 10L, "falha", 2L);
        when(emailService.estatisticas()).thenReturn(stats);

        mockMvc.perform(get("/api/emails/estatisticas")
                        .header("X-API-Key", "test-key-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendente").value(5))
                .andExpect(jsonPath("$.enviado").value(10))
                .andExpect(jsonPath("$.falha").value(2));
    }

    @Test
    @DisplayName("POST /api/emails sem body deve retornar 400")
    void deveRetornar400SemBody() throws Exception {
        mockMvc.perform(post("/api/emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "test-key-123")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private EmailResponse criarEmailResponse(Long id, String status) {
        EmailResponse response = new EmailResponse();
        response.setId(id);
        response.setDestinatario("dest@example.com");
        response.setAssunto("Teste");
        response.setStatus(status);
        response.setTentativas(0);
        response.setContaId(1L);
        response.setCriadoEm(LocalDateTime.now());
        return response;
    }
}
