package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.whatsapp.WhatsAppApplication;
import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test end-to-end da Phase 1 — phase gate.
 *
 * <p>Sobe o {@link WhatsAppApplication} completo via {@link SpringBootTest} com
 * {@code webEnvironment = MOCK} e usa {@link AutoConfigureMockMvc} para construir
 * o {@link MockMvc} com TODOS os {@code FilterRegistrationBean} (HmacSignatureFilter
 * + ApiKeyFilter de {@code SecurityConfig}) registrados na chain real. Diferente
 * de {@link WebhookControllerTest} (Wave 6) que usa {@code @WebMvcTest} e NAO
 * carrega filters customizados, esta wave exercita o stack inteiro:
 * HmacSignatureFilter + CachedBodyHttpServletRequest + HmacValidator + ApiKeyFilter
 * + WebhookController.
 *
 * <p>Cobre os 5 ROADMAP success criteria de Phase 1 + 2 bonus (D-02 webhook publico
 * de API key, /health endpoint publico). Mapeamento test → SC documentado em cada
 * cenario via {@code @DisplayName}.
 *
 * <p>Helper {@link #computeSignature(byte[], String)} produz fixtures vivas: a
 * signature e calculada em runtime contra o {@code appSecret} de
 * {@code application-test.yml}, eliminando hex hardcoded que rebem se algo mudar.
 *
 * <p><b>Por que {@link AutoConfigureMockMvc} e nao
 * {@code MockMvcBuilders.webAppContextSetup}:</b> a forma manual com
 * {@code webAppContextSetup} NAO registra automaticamente os filters declarados
 * via {@code FilterRegistrationBean} — o filter chain efetivo deixa de aplicar
 * o HMAC gate, e tests de "POST sem signature deve dar 401" voltam 200
 * espuriamente. {@code @AutoConfigureMockMvc} respeita os {@code FilterRegistrationBean}
 * registrados em {@code SecurityConfig} e e o caminho recomendado pelo Spring Boot.
 */
@SpringBootTest(classes = WhatsAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WhatsAppProperties properties;

    /**
     * Computa o header {@code "sha256=<hex>"} valido para o body + secret dados.
     * Mirror do helper de {@link br.com.erpkit.whatsapp.web.HmacSignatureFilterTest}
     * — fixture viva, automaticamente alinhada com o {@code appSecret} de
     * {@code application-test.yml}.
     */
    private static String computeSignature(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmac = mac.doFinal(body);
        return "sha256=" + HexFormat.of().formatHex(hmac);
    }

    // =========================================================================
    // ROADMAP Phase 1 Success Criterion 1 — GET hub.challenge plain text + 403
    // =========================================================================

    @Test
    @DisplayName("SC-1: GET com mode=subscribe + verifyToken correto retorna 200 + text/plain + body literal challenge (PITFALLS C-10)")
    void sc1_get_handshake_com_token_correto_retorna_challenge_plain_text() throws Exception {
        mockMvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", properties.getVerifyToken())
                        .param("hub.challenge", "challenge-12345"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                // Body LITERAL — sem aspas, sem JSON wrap. Meta rejeita qualquer ruido.
                .andExpect(content().string("challenge-12345"));
    }

    @Test
    @DisplayName("SC-1: GET com verifyToken errado retorna 403")
    void sc1_get_handshake_com_token_errado_retorna_403() throws Exception {
        mockMvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "token-errado-totalmente")
                        .param("hub.challenge", "abc"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SC-1: GET com mode != subscribe retorna 403 (mesmo com verifyToken correto)")
    void sc1_get_handshake_com_mode_diferente_de_subscribe_retorna_403() throws Exception {
        mockMvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "unsubscribe")
                        .param("hub.verify_token", properties.getVerifyToken())
                        .param("hub.challenge", "abc"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // ROADMAP Phase 1 Success Criterion 2 — POST HMAC valido em <1s + body modificado 401
    // =========================================================================

    @Test
    @DisplayName("SC-2: POST com X-Hub-Signature-256 valido retorna 200 em <1s")
    void sc2_post_com_hmac_valido_retorna_200_em_menos_de_1s() throws Exception {
        byte[] body = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}"
                .getBytes(StandardCharsets.UTF_8);
        String signature = computeSignature(body, properties.getAppSecret());

        long start = System.currentTimeMillis();
        mockMvc.perform(post("/webhook/whatsapp")
                        .header("X-Hub-Signature-256", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        long elapsed = System.currentTimeMillis() - start;

        // SC-2 do ROADMAP: 200 em <1s. Margem de seguranca contra 5s do Meta.
        // Em CI tipico isso roda em <100ms; 1000ms protege contra ambientes lentos.
        assertThat(elapsed)
                .as("POST com HMAC valido deve retornar em <1s (ROADMAP SC-2)")
                .isLessThan(1000L);
    }

    @Test
    @DisplayName("SC-2: POST com signature de body diferente (1 byte modificado) retorna 401")
    void sc2_post_com_body_modificado_retorna_401() throws Exception {
        byte[] originalBody = "{\"valor\":1}".getBytes(StandardCharsets.UTF_8);
        String signatureOriginal = computeSignature(originalBody, properties.getAppSecret());

        byte[] modifiedBody = originalBody.clone();
        modifiedBody[modifiedBody.length - 2] ^= 0x01; // flip 1 bit em 1 byte

        mockMvc.perform(post("/webhook/whatsapp")
                        .header("X-Hub-Signature-256", signatureOriginal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modifiedBody))
                .andExpect(status().isUnauthorized())
                // ErrorResponse JSON contem mensagem identificando o problema
                .andExpect(content().string(containsString("Assinatura HMAC invalida")));
    }

    @Test
    @DisplayName("SC-2: POST sem header X-Hub-Signature-256 retorna 401 (filter gate antes do MVC)")
    void sc2_post_sem_signature_retorna_401() throws Exception {
        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // ROADMAP Phase 1 Success Criterion 3 — Body UTF-8 portugues valida corretamente
    // =========================================================================

    @Test
    @DisplayName("SC-3: POST com payload portugues UTF-8 (Olá, gostaria de um orçamento) + HMAC valido retorna 200 (PITFALLS C-04)")
    void sc3_post_com_payload_portugues_utf8_valida_corretamente() throws Exception {
        // Payload Meta-realistic com texto portugues acentuado nao-ASCII.
        // CachedBodyHttpServletRequest deve preservar bytes brutos UTF-8;
        // se converter via String em algum ponto e re-encodar com charset diferente,
        // a signature falha (C-04 — gate empirico).
        String payload = "{\"object\":\"whatsapp_business_account\","
                + "\"entry\":[{\"id\":\"123\",\"changes\":[{\"value\":{\"messages\":["
                + "{\"from\":\"5547999999999\",\"text\":{\"body\":\"Olá, gostaria de um orçamento\"}}"
                + "]},\"field\":\"messages\"}]}]}";
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        String signature = computeSignature(body, properties.getAppSecret());

        mockMvc.perform(post("/webhook/whatsapp")
                        // contentType com charset UTF-8 explicito — defesa contra default
                        // ISO-8859-1 do MockMvc em algumas versoes (Wave 6 SUMMARY concern 3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-Hub-Signature-256", signature)
                        .content(body))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // ROADMAP Phase 1 Success Criterion 4 — Boot fail-fast + secrets nao em logs
    // (Cobertura primaria: WhatsAppPropertiesValidationTest da Wave 3 — 7 tests.
    //  Aqui um smoke test do toString mascarado.)
    // =========================================================================

    @Test
    @DisplayName("SC-4: toString() de WhatsAppProperties mascara accessToken/appSecret/verifyToken")
    void sc4_secrets_nao_aparecem_no_toString_das_properties() {
        String s = properties.toString();

        // Nenhum dos 3 secrets deve aparecer literalmente no toString.
        // Se algum aparecer, log de boot/erro de bind vaza secret para SIEM/operadores.
        assertThat(s)
                .as("toString nao deve conter accessToken")
                .doesNotContain(properties.getAccessToken());
        assertThat(s)
                .as("toString nao deve conter appSecret")
                .doesNotContain(properties.getAppSecret());
        assertThat(s)
                .as("toString nao deve conter verifyToken")
                .doesNotContain(properties.getVerifyToken());

        // Marcador [REDACTED] presente
        assertThat(s).contains("[REDACTED]");

        // Nao-secrets PODEM aparecer (defesa em profundidade — phoneNumberId nao e secret,
        // erpCallbackUrl idem). Confirmar que o toString nao virou um catch-all redaction.
        assertThat(s).contains(properties.getPhoneNumberId());
        assertThat(s).contains(properties.getErpCallbackUrl());
    }

    // =========================================================================
    // ROADMAP Phase 1 Success Criterion 5 — Flyway V1-V4 + mvnw verify verde
    // (Cobertura primaria: FlywayMigrationTest da Wave 4 — 6 tests com schema introspection.
    //  Aqui o smoke test e o proprio fato deste @SpringBootTest bootar — se Flyway
    //  falhasse, o ApplicationContext nao sobe e nenhum teste roda.)
    // =========================================================================
    // Sem teste explicito — implicit pelo @SpringBootTest carregar com sucesso.

    // =========================================================================
    // BONUS — D-02: ApiKeyFilter NAO bloqueia /webhook (HMAC ja substitui API key)
    // =========================================================================

    @Test
    @DisplayName("D-02: POST /webhook/whatsapp com HMAC valido + sem X-API-Key retorna 200 (NAO 401 do ApiKeyFilter)")
    void d02_webhook_passa_sem_api_key_quando_hmac_valido() throws Exception {
        // Phase 1 ApiKeyFilter foi construido com Set.of("/webhook") como path publico
        // adicional. Sem esse argumento, ApiKeyFilter retornaria 401 antes do HMAC
        // sequer ser checado. Este teste prova que D-02 + Wave 6 SecurityConfig
        // wire-up estao corretos (webhook validado por HMAC, nao por API key).
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String signature = computeSignature(body, properties.getAppSecret());

        mockMvc.perform(post("/webhook/whatsapp")
                        .header("X-Hub-Signature-256", signature)
                        // NENHUM header X-API-Key — gate explicito
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk()); // 200, NAO 401 do ApiKeyFilter
    }

    // =========================================================================
    // BONUS — Health endpoint publico
    // =========================================================================

    @Test
    @DisplayName("/health endpoint e publico (DEFAULT_PUBLIC_PATHS de ApiKeyFilter), retorna 200 sem X-API-Key")
    void health_endpoint_publico_retorna_200_sem_api_key() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.modulo").value("api-whatsapp"));
    }
}
