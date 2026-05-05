package br.com.erpkit.whatsapp.web;

import br.com.erpkit.shared.dto.ErrorResponse;
import br.com.erpkit.whatsapp.config.WhatsAppProperties;
import br.com.erpkit.whatsapp.service.HmacValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtra POST /webhook/* validando HMAC-SHA256 contra o header
 * {@code X-Hub-Signature-256}. So aplicavel a metodos POST — GET (hub.challenge
 * handshake) passa direto para o controller.
 *
 * <p>Registrado em {@code SecurityConfig} via
 * {@code FilterRegistrationBean.addUrlPatterns("/webhook/*")} +
 * {@code setOrder(Ordered.HIGHEST_PRECEDENCE)} — gate mais cedo possivel
 * (PITFALLS C-02). Embrulha {@link HttpServletRequest} em
 * {@link CachedBodyHttpServletRequest} ANTES de qualquer leitura, garantindo que
 * downstream (controller, demais filters) consigam consumir o body novamente.
 *
 * <p>Em invalido: response 401 + {@link ErrorResponse} JSON, NUNCA chama
 * {@code chain.doFilter}. Em valido: {@code chain.doFilter(cached, response)} —
 * o wrapper segue downstream.
 *
 * <p>Logs nivel WARN apenas com URI e metodo HTTP. NUNCA inclui body, valor de
 * signature header nem secret (potencial PII / dados de attacker —
 * PITFALLS Security Mistakes).
 */
public class HmacSignatureFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HmacSignatureFilter.class);
    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final HmacValidator validator;
    private final WhatsAppProperties properties;

    public HmacSignatureFilter(HmacValidator validator, WhatsAppProperties properties) {
        this.validator = validator;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // GET hub.challenge nao tem body assinado — passa direto para o controller,
        // que valida verifyToken via MessageDigest.isEqual.
        if (!HttpMethod.POST.matches(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // Wrap PRIMEIRO — qualquer outro filter ou MVC downstream consome o cached
        // array (PITFALLS C-02). Falha de IO ao ler body retorna 400.
        CachedBodyHttpServletRequest cached;
        try {
            cached = new CachedBodyHttpServletRequest(request);
        } catch (IOException ex) {
            log.warn("Falha ao ler body do webhook em POST {} — rejeitado com 400", request.getRequestURI());
            writeBadRequest(response);
            return;
        }

        byte[] body = cached.getCachedBody();
        // Header lido do request original — header nao mexe no body, qualquer
        // request wrapper preserva headers via super.
        String signature = request.getHeader(SIGNATURE_HEADER);

        if (!validator.isValid(body, signature, properties.getAppSecret())) {
            log.warn("HMAC invalido em POST {} — rejeitado com 401", request.getRequestURI());
            // NAO logar body, NAO logar signature header (potencial PII / dados de attacker)
            writeUnauthorized(response);
            return;
        }

        // HMAC valido — segue para o controller com o body cacheado
        chain.doFilter(cached, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse error = new ErrorResponse(401, "Nao autorizado", "Assinatura HMAC invalida ou ausente");
        response.getWriter().write(MAPPER.writeValueAsString(error));
    }

    private void writeBadRequest(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse error = new ErrorResponse(400, "Requisicao invalida", "Body do webhook ilegivel");
        response.getWriter().write(MAPPER.writeValueAsString(error));
    }
}
