package br.com.erpkit.whatsapp.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste unitario do {@link HealthController} via {@link WebMvcTest}.
 *
 * <p>{@code @ActiveProfiles("test")} carrega {@code application-test.yml} com
 * dummy values dos 5 secrets — necessario porque
 * {@link br.com.erpkit.whatsapp.WhatsAppApplication} ativa
 * {@code @EnableConfigurationProperties(WhatsAppProperties.class)} e Bean
 * Validation rejeita placeholders vazios mesmo em WebMvcTest.
 */
@WebMvcTest(HealthController.class)
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /health retorna 200 com {\"status\":\"UP\",\"modulo\":\"api-whatsapp\"}")
    void health_retorna_200_com_status_up() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.modulo").value("api-whatsapp"));
    }
}
