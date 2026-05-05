package br.com.erpkit.whatsapp.config;

import br.com.erpkit.shared.security.ApiKeyFilter;
import br.com.erpkit.whatsapp.service.HmacValidator;
import br.com.erpkit.whatsapp.web.HmacSignatureFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.Set;

/**
 * Registra os 2 filters de seguranca do api-whatsapp:
 *
 * <ol>
 *   <li>{@link HmacSignatureFilter} — ordem {@link Ordered#HIGHEST_PRECEDENCE},
 *       restrito a {@code /webhook/*}. Valida HMAC-SHA256 do header
 *       {@code X-Hub-Signature-256} antes de qualquer processamento Spring MVC
 *       (PITFALLS C-02 — gate mais cedo possivel).</li>
 *   <li>{@link ApiKeyFilter} (lib-shared) — ordem {@code HIGHEST_PRECEDENCE + 10},
 *       aplicado a {@code /*}. Construido com {@code Set.of("/webhook")} como
 *       {@code additionalPublicPaths} (D-02 do CONTEXT.md) — webhook e publico
 *       de API key, validado pelo HMAC.</li>
 * </ol>
 *
 * <p>Comportamento esperado em runtime:
 * <ul>
 *   <li>POST {@code /webhook/whatsapp} → HMAC valida ou rejeita → ApiKey libera (path publico)
 *       → Controller</li>
 *   <li>GET {@code /webhook/whatsapp} → HMAC pula (nao e POST) → ApiKey libera
 *       → Controller (que valida verifyToken)</li>
 *   <li>GET {@code /api/whatsapp/...} (futuro Phase 4) → HMAC pula (URL pattern nao casa)
 *       → ApiKey exige X-API-Key → Controller</li>
 *   <li>GET {@code /health} → HMAC pula → ApiKey libera (DEFAULT_PUBLIC_PATHS) → HealthController</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    /**
     * HMAC filter — ordem {@link Ordered#HIGHEST_PRECEDENCE}, so aplicado a
     * {@code /webhook/*}. Endpoints internos do ERP (Phase 4) nao precisam HMAC,
     * so API key.
     */
    @Bean
    public FilterRegistrationBean<HmacSignatureFilter> hmacSignatureFilter(
            HmacValidator validator, WhatsAppProperties properties) {
        FilterRegistrationBean<HmacSignatureFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new HmacSignatureFilter(validator, properties));
        registration.addUrlPatterns("/webhook/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * API Key filter (lib-shared) — depois do HMAC filter. Construtor de 2 args
     * (PLAN-01) recebe {@code Set.of("/webhook")} como path publico extra:
     * webhook e validado por HMAC, nao por API key.
     */
    @Bean
    public FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(@Value("${modulo.api-key:}") String apiKey) {
        FilterRegistrationBean<ApiKeyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ApiKeyFilter(apiKey, Set.of("/webhook")));
        registration.addUrlPatterns("/*");
        // HIGHEST_PRECEDENCE + 10 — depois do HmacSignatureFilter, antes de
        // qualquer outro filter custom que apareca em phases futuras.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
