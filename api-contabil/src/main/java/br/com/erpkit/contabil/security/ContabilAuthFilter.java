package br.com.erpkit.contabil.security;

import br.com.erpkit.shared.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Auth do módulo contábil. Aceita DUAS credenciais, sem quebrar o ERP:
 *
 * <ol>
 *   <li><b>X-API-Key</b> (service-to-service): fluxo de eventos e proxy de relatórios do ERP.
 *       Mantém 100% o comportamento anterior — inclusive "aberto" quando a api-key não está
 *       configurada (dev).</li>
 *   <li><b>Bearer JWT</b> (app standalone do contador): validado localmente pelo JwtService.
 *       Se um Bearer é enviado, ele é a fonte da verdade — token inválido devolve 401.</li>
 * </ol>
 *
 * O e-mail do contador autenticado fica em {@code request.getAttribute("contador.email")}.
 */
public class ContabilAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_EMAIL = "contador.email";

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/health", "/api/info", "/swagger-ui", "/v3/api-docs", "/v1/auth/login");
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final String apiKey;
    private final JwtService jwtService;

    public ContabilAuthFilter(String apiKey, JwtService jwtService) {
        this.apiKey = apiKey;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // Preflight CORS e paths públicos passam direto.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPublic(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        // 1) Bearer JWT presente -> é a credencial do contador; valida obrigatoriamente.
        String bearer = extrairBearer(request);
        if (bearer != null) {
            String email = jwtService.validar(bearer);
            if (email != null) {
                request.setAttribute(ATTR_EMAIL, email);
                chain.doFilter(request, response);
            } else {
                unauthorized(response, "Token inválido ou expirado");
            }
            return;
        }

        // 2) Sem Bearer -> semântica X-API-Key (service). Aberto quando api-key em branco (dev).
        if (apiKey != null && !apiKey.isBlank() && !apiKey.equals(request.getHeader(API_KEY_HEADER))) {
            unauthorized(response, "API Key inválida ou ausente");
            return;
        }
        chain.doFilter(request, response);
    }

    private String extrairBearer(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        if (h != null && h.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String t = h.substring(7).trim();
            return t.isEmpty() ? null : t;
        }
        return null;
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private void unauthorized(HttpServletResponse response, String detalhe) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(MAPPER.writeValueAsString(
                new ErrorResponse(401, "Não autorizado", detalhe)));
    }
}
