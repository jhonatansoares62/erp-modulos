package br.com.erpkit.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyFilterTest {

    private static final String API_KEY = "minha-chave-secreta";

    @Test
    @DisplayName("Deve permitir acesso a /health sem API Key")
    void devePermitirAcessoHealthSemApiKey() throws ServletException, IOException {
        ApiKeyFilter filter = new ApiKeyFilter(API_KEY);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.setRequestURI("/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus(), "Deve retornar 200 para path publico /health");
    }

    @Test
    @DisplayName("Deve permitir acesso a /api/info sem API Key")
    void devePermitirAcessoApiInfoSemApiKey() throws ServletException, IOException {
        ApiKeyFilter filter = new ApiKeyFilter(API_KEY);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/info");
        request.setRequestURI("/api/info");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus(), "Deve retornar 200 para path publico /api/info");
    }

    @Test
    @DisplayName("Deve permitir acesso a /swagger-ui sem API Key")
    void devePermitirAcessoSwaggerSemApiKey() throws ServletException, IOException {
        ApiKeyFilter filter = new ApiKeyFilter(API_KEY);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        request.setRequestURI("/swagger-ui/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus(), "Deve retornar 200 para path publico /swagger-ui");
    }

    @Test
    @DisplayName("Deve permitir acesso a /v3/api-docs sem API Key")
    void devePermitirAcessoApiDocsSemApiKey() throws ServletException, IOException {
        ApiKeyFilter filter = new ApiKeyFilter(API_KEY);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v3/api-docs");
        request.setRequestURI("/v3/api-docs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus(), "Deve retornar 200 para path publico /v3/api-docs");
    }

    @Test
    @DisplayName("Deve permitir acesso com API Key valida")
    void devePermitirAcessoComApiKeyValida() throws ServletException, IOException {
        ApiKeyFilter filter = new ApiKeyFilter(API_KEY);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/emails");
        request.setRequestURI("/api/emails");
        request.addHeader("X-API-Key", API_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus(), "Deve retornar 200 com API Key valida");
    }

    @Test
    @DisplayName("Deve retornar 401 com API Key invalida")
    void deveRetornar401ComApiKeyInvalida() throws ServletException, IOException {
        ApiKeyFilter filter = new ApiKeyFilter(API_KEY);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/emails");
        request.setRequestURI("/api/emails");
        request.addHeader("X-API-Key", "chave-errada");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus(), "Deve retornar 401 com API Key invalida");
        assertTrue(response.getContentAsString().contains("API Key"), "Deve conter mensagem sobre API Key");
    }

    @Test
    @DisplayName("Deve retornar 401 sem header X-API-Key")
    void deveRetornar401SemHeaderApiKey() throws ServletException, IOException {
        ApiKeyFilter filter = new ApiKeyFilter(API_KEY);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/emails");
        request.setRequestURI("/api/emails");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus(), "Deve retornar 401 sem header X-API-Key");
    }

    @Test
    @DisplayName("Deve desabilitar autenticacao com API Key em branco")
    void deveDesabilitarAutenticacaoComApiKeyEmBranco() throws ServletException, IOException {
        ApiKeyFilter filter = new ApiKeyFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/emails");
        request.setRequestURI("/api/emails");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus(), "Deve permitir acesso quando API Key esta em branco");
    }

    @Test
    @DisplayName("Deve desabilitar autenticacao com API Key null")
    void deveDesabilitarAutenticacaoComApiKeyNull() throws ServletException, IOException {
        ApiKeyFilter filter = new ApiKeyFilter(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/emails");
        request.setRequestURI("/api/emails");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus(), "Deve permitir acesso quando API Key e null");
    }

    // ==========================================================================
    // Tests novos — construtor de 2 args com additionalPublicPaths (PLAN 01-01)
    // ==========================================================================

    @Test
    @DisplayName("Construtor de 1 arg continua funcionando — regression")
    void construtor_1_arg_continua_funcionando() throws ServletException, IOException {
        // Regressao: garante que o construtor de 1 arg (usado por api-email/api-storage/api-consultas)
        // ainda rejeita paths nao-publicos sem header X-API-Key.
        ApiKeyFilter filter = new ApiKeyFilter(API_KEY);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/qualquer-coisa");
        request.setRequestURI("/api/qualquer-coisa");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus(),
                "Construtor 1-arg deve continuar rejeitando paths nao-publicos sem API Key");
    }

    @Test
    @DisplayName("Construtor de 2 args permite path adicional como publico")
    void construtor_2_args_permite_path_adicional_como_publico() throws ServletException, IOException {
        // Cenario que habilita PLAN-06: api-whatsapp registra /webhook como path publico
        // (validacao via HMAC, nao via API Key). Filter nao deve bloquear.
        ApiKeyFilter filter = new ApiKeyFilter(API_KEY, Set.of("/webhook"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhook/whatsapp");
        request.setRequestURI("/webhook/whatsapp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus(),
                "Path adicional /webhook/whatsapp deve passar sem X-API-Key (validado externamente via HMAC)");
    }

    @Test
    @DisplayName("Construtor de 2 args com Set vazio comporta-se como 1-arg")
    void construtor_2_args_com_set_vazio_comporta_se_como_1_arg() throws ServletException, IOException {
        ApiKeyFilter filter = new ApiKeyFilter(API_KEY, Set.of());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/qualquer-coisa");
        request.setRequestURI("/api/qualquer-coisa");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus(),
                "Set vazio nao deve adicionar paths publicos — comportamento identico ao 1-arg");
    }

    @Test
    @DisplayName("Construtor de 2 args com null nao quebra e preserva defaults")
    void construtor_2_args_com_null_nao_quebra() throws ServletException, IOException {
        // null em additionalPublicPaths deve ser tratado como Set vazio (sem NPE).
        ApiKeyFilter filter = new ApiKeyFilter(API_KEY, null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.setRequestURI("/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus(),
                "Default path /health deve continuar publico mesmo com additionalPublicPaths null");
    }

    @Test
    @DisplayName("additionalPublicPaths somam-se aos defaults — uniao, nao substituicao")
    void additional_paths_somam_se_aos_defaults() throws ServletException, IOException {
        // Garante que o construtor de 2 args faz UNIAO (DEFAULT_PUBLIC_PATHS + additional),
        // nao substituicao. /health (default) e /webhook (additional) ambos devem passar.
        ApiKeyFilter filter = new ApiKeyFilter(API_KEY, Set.of("/webhook"));

        // Default path ainda funciona
        MockHttpServletRequest reqHealth = new MockHttpServletRequest("GET", "/health");
        reqHealth.setRequestURI("/health");
        MockHttpServletResponse respHealth = new MockHttpServletResponse();
        filter.doFilter(reqHealth, respHealth, new MockFilterChain());
        assertEquals(200, respHealth.getStatus(),
                "Default path /health deve continuar publico apos adicionar /webhook");

        // Additional path tambem funciona
        MockHttpServletRequest reqWebhook = new MockHttpServletRequest("POST", "/webhook/x");
        reqWebhook.setRequestURI("/webhook/x");
        MockHttpServletResponse respWebhook = new MockHttpServletResponse();
        filter.doFilter(reqWebhook, respWebhook, new MockFilterChain());
        assertEquals(200, respWebhook.getStatus(),
                "Additional path /webhook/x deve ser publico (uniao com defaults)");
    }
}
