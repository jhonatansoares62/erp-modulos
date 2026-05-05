package br.com.erpkit.shared.security;

import br.com.erpkit.shared.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final Set<String> DEFAULT_PUBLIC_PATHS =
            Set.of("/health", "/api/info", "/swagger-ui", "/v3/api-docs");
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final String apiKey;
    private final Set<String> publicPaths;

    /** Construtor original — preservado para backward-compat. */
    public ApiKeyFilter(String apiKey) {
        this(apiKey, Set.of());
    }

    /** Novo construtor — permite paths publicos adicionais por modulo (ex: /webhook do api-whatsapp). */
    public ApiKeyFilter(String apiKey, Set<String> additionalPublicPaths) {
        this.apiKey = apiKey;
        Set<String> merged = new HashSet<>(DEFAULT_PUBLIC_PATHS);
        if (additionalPublicPaths != null) {
            merged.addAll(additionalPublicPaths);
        }
        this.publicPaths = Set.copyOf(merged); // imutavel
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Endpoints públicos não precisam de API Key
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank() && !apiKey.equals(key)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            ErrorResponse error = new ErrorResponse(401, "Não autorizado", "API Key inválida ou ausente");
            response.getWriter().write(MAPPER.writeValueAsString(error));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return this.publicPaths.stream().anyMatch(path::startsWith);
    }
}
