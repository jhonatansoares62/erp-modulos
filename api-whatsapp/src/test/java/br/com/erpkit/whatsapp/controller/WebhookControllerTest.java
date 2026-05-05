package br.com.erpkit.whatsapp.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes unitarios do {@link WebhookController} via {@link WebMvcTest}.
 *
 * <p>{@code @WebMvcTest} NAO registra {@code FilterRegistrationBean} —
 * portanto o {@link br.com.erpkit.whatsapp.web.HmacSignatureFilter} NAO atua
 * nestes testes. Validacao end-to-end com filter wired vem em PLAN-07
 * (integration tests via {@code @SpringBootTest}).
 *
 * <p>{@code @ActiveProfiles("test")} carrega {@code application-test.yml} que
 * tem dummy values para os 5 secrets — necessario porque
 * {@link br.com.erpkit.whatsapp.WhatsAppApplication} ativa
 * {@code @EnableConfigurationProperties(WhatsAppProperties.class)} e Bean
 * Validation rejeita placeholders vazios. {@code @TestPropertySource} sobrescreve
 * apenas {@code verifyToken} para casar com o valor esperado pelos testes.
 *
 * <p>Foco: GET handshake (challenge plain text + 403 em token errado / mode errado),
 * POST stub minimo (200 vazio).
 */
@WebMvcTest(WebhookController.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.modulos.whatsapp.verifyToken=tk-correto")
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET com mode=subscribe + verifyToken correto retorna 200 plain text com challenge")
    void get_hub_challenge_com_verify_token_correto_retorna_plain_text() throws Exception {
        mockMvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "tk-correto")
                        .param("hub.challenge", "abc123"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("abc123")); // body LITERAL "abc123" (sem aspas, sem JSON wrap)
    }

    @Test
    @DisplayName("GET com verifyToken errado retorna 403")
    void get_hub_challenge_com_verify_token_errado_retorna_403() throws Exception {
        mockMvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "tk-ERRADO")
                        .param("hub.challenge", "abc123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET com mode != subscribe retorna 403")
    void get_hub_challenge_com_mode_diferente_retorna_403() throws Exception {
        mockMvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "unsubscribe")
                        .param("hub.verify_token", "tk-correto")
                        .param("hub.challenge", "abc123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /webhook/whatsapp retorna 200 com body vazio (D-04 stub minimo)")
    void post_webhook_retorna_200_vazio() throws Exception {
        // Sem filter wired aqui (WebMvcTest pula FilterRegistrationBean) —
        // POST passa direto para o controller, que retorna 200 imediato.
        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }
}
