package br.com.erpkit.whatsapp.web;

import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.service.HmacValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitarios do {@link HmacSignatureFilter} usando {@link MockHttpServletRequest},
 * {@link MockHttpServletResponse} e {@link MockFilterChain} (sem Spring context).
 *
 * <p>Foco no contrato do filter: GET passa direto, POST com HMAC valido
 * encaminha o {@link CachedBodyHttpServletRequest} downstream, POST sem header
 * ou com signature errada retorna 401 e NUNCA chama {@code chain.doFilter}.
 *
 * <p>Helper {@link #computeHeader(byte[], String)} produz o header
 * {@code "sha256=<hex>"} valido para o secret dado — fixtures vivas, sem
 * hardcoded hex que rebem se algo mudar na crypto.
 */
class HmacSignatureFilterTest {

    private static final String APP_SECRET = "test-app-secret";

    private HmacSignatureFilter filter;

    @BeforeEach
    void setUp() {
        WhatsAppProperties properties = new WhatsAppProperties();
        properties.setPhoneNumberId("test-phone-id");
        properties.setAccessToken("test-access-token");
        properties.setAppSecret(APP_SECRET);
        properties.setVerifyToken("test-verify-token");
        properties.setErpCallbackUrl("http://localhost:0/test");
        filter = new HmacSignatureFilter(new HmacValidator(), properties);
    }

    @Test
    @DisplayName("GET request passa direto sem validacao HMAC")
    void get_request_passa_sem_validacao() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/webhook/whatsapp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // chain foi chamada com o request ORIGINAL (nao wrapped), sem validacao HMAC
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200); // default do MockResponse
    }

    @Test
    @DisplayName("POST com HMAC valido encaminha CachedBodyHttpServletRequest downstream")
    void post_com_hmac_valido_passa() throws Exception {
        byte[] body = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);
        String header = computeHeader(body, APP_SECRET);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhook/whatsapp");
        request.setContent(body);
        request.addHeader("X-Hub-Signature-256", header);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // chain.doFilter chamado com o wrapper (CachedBodyHttpServletRequest)
        assertThat(chain.getRequest()).isInstanceOf(CachedBodyHttpServletRequest.class);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST sem header X-Hub-Signature-256 retorna 401 e NAO chama chain")
    void post_sem_header_retorna_401_e_nao_chama_chain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhook/whatsapp");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString())
                .contains("Nao autorizado")
                .contains("Assinatura HMAC invalida");
        // chain NAO foi chamada — getRequest() retorna null
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("POST com HMAC invalido (signature errada) retorna 401")
    void post_com_hmac_invalido_retorna_401() throws Exception {
        byte[] body = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);
        String headerErrado = "sha256=" + "0".repeat(64); // 64 zeros, signature obviamente errada

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhook/whatsapp");
        request.setContent(body);
        request.addHeader("X-Hub-Signature-256", headerErrado);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("POST com signature de body diferente retorna 401 (tampering)")
    void post_com_body_modificado_retorna_401() throws Exception {
        byte[] bodyA = "{\"valor\":\"A\"}".getBytes(StandardCharsets.UTF_8);
        byte[] bodyB = "{\"valor\":\"B\"}".getBytes(StandardCharsets.UTF_8);
        String headerDeA = computeHeader(bodyA, APP_SECRET);

        // Envia bodyB com signature de bodyA
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhook/whatsapp");
        request.setContent(bodyB);
        request.addHeader("X-Hub-Signature-256", headerDeA);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("POST com body UTF-8 portugues e signature valida passa (PITFALLS C-04)")
    void post_com_body_portugues_utf8_passa() throws Exception {
        byte[] body = "{\"text\":\"Olá, gostaria de um orçamento\"}".getBytes(StandardCharsets.UTF_8);
        String header = computeHeader(body, APP_SECRET);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhook/whatsapp");
        request.setContent(body);
        request.addHeader("X-Hub-Signature-256", header);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // Cache do body preserva os bytes originais — chain recebeu o wrapper
        assertThat(chain.getRequest()).isInstanceOf(CachedBodyHttpServletRequest.class);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    /**
     * Computa o header {@code "sha256=<hex>"} valido para o body + secret dados.
     * Mirror do helper de {@code HmacValidatorTest} para fixtures vivas.
     */
    private static String computeHeader(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmac = mac.doFinal(body);
        return "sha256=" + HexFormat.of().formatHex(hmac);
    }
}
