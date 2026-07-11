package br.com.erpkit.whatsapp.security;

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
 * Auth do modulo whatsapp. Substitui o {@code ApiKeyFilter} puro da lib-shared para o
 * namespace {@code /api/whatsapp/**}, aceitando DUAS credenciais sem quebrar o ERP nem o
 * webhook:
 *
 * <ol>
 *   <li><b>X-API-Key</b> (service-to-service): endpoints internos do ERP
 *       ({@code /api/whatsapp/enviar-*}, {@code /status}, {@code /config},
 *       {@code /relatorios}). Mantem 100% o comportamento do ApiKeyFilter anterior —
 *       inclusive "aberto" quando a api-key nao esta configurada (dev).</li>
 *   <li><b>Bearer JWT</b> (app standalone do atendente): validado localmente pelo
 *       {@link JwtService}. Se um Bearer e enviado, ele e a fonte da verdade — token
 *       invalido devolve 401.</li>
 * </ol>
 *
 * <p>O webhook do Meta ({@code /webhook/**}, validado por HMAC no {@code HmacSignatureFilter}),
 * o painel {@code /monitor/**} (dev/meta), {@code /health}, o Swagger, o Actuator, os estaticos
 * do SPA (servidos da raiz) e o proprio {@code /api/whatsapp/auth/login} passam livres — o app
 * precisa carregar para entao autenticar. So {@code /api/whatsapp/**} (menos o login) e guardado.
 *
 * <p>O e-mail do atendente autenticado fica em {@code request.getAttribute("atendente.email")}.
 */
public class WhatsappAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_EMAIL = "atendente.email";

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_PREFIX = "/api/whatsapp/";
    private static final String LOGIN_PATH = "/api/whatsapp/auth/login";
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final String apiKey;
    private final JwtService jwtService;

    public WhatsappAuthFilter(String apiKey, JwtService jwtService) {
        this.apiKey = apiKey;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // Preflight CORS, estaticos/SPA e endpoints publicos (webhook/monitor/health/swagger/
        // actuator/login) passam direto — so a API /api/whatsapp/** (menos o login) e guardada.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !isProtected(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        // 1) Bearer JWT presente -> e a credencial do atendente; valida obrigatoriamente.
        String bearer = extrairBearer(request);
        if (bearer != null) {
            String email = jwtService.validar(bearer);
            if (email != null) {
                request.setAttribute(ATTR_EMAIL, email);
                chain.doFilter(request, response);
            } else {
                unauthorized(response, "Token invalido ou expirado");
            }
            return;
        }

        // 2) Sem Bearer -> semantica X-API-Key (service/ERP). Aberto quando api-key em branco (dev).
        if (apiKey != null && !apiKey.isBlank() && !apiKey.equals(request.getHeader(API_KEY_HEADER))) {
            unauthorized(response, "API Key invalida ou ausente");
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

    /**
     * Apenas a API {@code /api/whatsapp/**} e protegida (menos o login). Webhook, monitor,
     * health, swagger, actuator, recursos estaticos do app (index.html, JS/CSS, monitor.html)
     * e as rotas do SPA passam livres.
     */
    private boolean isProtected(String path) {
        return path.startsWith(API_PREFIX) && !path.equals(LOGIN_PATH);
    }

    private void unauthorized(HttpServletResponse response, String detalhe) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(MAPPER.writeValueAsString(
                new ErrorResponse(401, "Nao autorizado", detalhe)));
    }
}
