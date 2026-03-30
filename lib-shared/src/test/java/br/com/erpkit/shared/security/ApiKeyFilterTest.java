package br.com.erpkit.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

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
}
