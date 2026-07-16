package br.com.erpkit.backup.security;

import br.com.erpkit.shared.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Auth service-to-service do modulo de backup: X-API-Key. Guarda apenas a API {@code /v1/**}
 * (o {@code /health} e o swagger passam livres). Mantem a semantica "aberto quando a api-key
 * nao esta configurada" (dev) — em producao o WinSW injeta a API_KEY e o acesso exige a chave.
 */
public class BackupApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final String apiKey;

    public BackupApiKeyFilter(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !isProtected(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        if (apiKey != null && !apiKey.isBlank() && !apiKey.equals(request.getHeader(API_KEY_HEADER))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(MAPPER.writeValueAsString(
                    new ErrorResponse(401, "Nao autorizado", "API Key invalida ou ausente")));
            return;
        }
        chain.doFilter(request, response);
    }

    /** So a API {@code /v1/**} e protegida; estaticos, /health e swagger passam livres. */
    private boolean isProtected(String path) {
        return path.startsWith("/v1/");
    }
}
