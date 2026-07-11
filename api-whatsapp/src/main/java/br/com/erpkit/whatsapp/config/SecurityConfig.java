package br.com.erpkit.whatsapp.config;

import br.com.erpkit.whatsapp.security.JwtService;
import br.com.erpkit.whatsapp.security.WhatsappAuthFilter;
import br.com.erpkit.whatsapp.service.HmacValidator;
import br.com.erpkit.whatsapp.web.HmacSignatureFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra os 2 filters de seguranca do api-whatsapp:
 *
 * <ol>
 *   <li>{@link HmacSignatureFilter} — ordem {@link Ordered#HIGHEST_PRECEDENCE},
 *       restrito a {@code /webhook/*}. Valida HMAC-SHA256 do header
 *       {@code X-Hub-Signature-256} antes de qualquer processamento Spring MVC
 *       (PITFALLS C-02 — gate mais cedo possivel).</li>
 *   <li>{@link WhatsappAuthFilter} — ordem {@code HIGHEST_PRECEDENCE + 10}, aplicado
 *       a {@code /*}. SUBSTITUI o antigo {@code ApiKeyFilter} puro da lib-shared:
 *       para {@code /api/whatsapp/**} aceita X-API-Key (ERP) OU Bearer JWT (atendente),
 *       preservando a semantica de X-API-Key para nao quebrar os endpoints internos do
 *       ERP, e libera webhook/monitor/health/swagger/estaticos como antes.</li>
 * </ol>
 *
 * <p>Comportamento esperado em runtime:
 * <ul>
 *   <li>POST {@code /webhook/whatsapp} → HMAC valida ou rejeita → Auth libera (nao e /api/whatsapp/)
 *       → Controller</li>
 *   <li>GET {@code /webhook/whatsapp} → HMAC pula (nao e POST) → Auth libera
 *       → Controller (que valida verifyToken)</li>
 *   <li>{@code /api/whatsapp/enviar-*} (ERP) → HMAC pula → Auth exige X-API-Key OU Bearer → Controller</li>
 *   <li>{@code POST /api/whatsapp/auth/login} → HMAC pula → Auth libera (login publico) → AuthController</li>
 *   <li>{@code GET /api/whatsapp/auth/me} (atendente) → HMAC pula → Auth exige Bearer → AuthController</li>
 *   <li>GET {@code /health} → HMAC pula → Auth libera → HealthController</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    /**
     * HMAC filter — ordem {@link Ordered#HIGHEST_PRECEDENCE}, so aplicado a
     * {@code /webhook/*}. Endpoints internos do ERP nao precisam HMAC, so a auth abaixo.
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
     * Auth filter do modulo — depois do HMAC filter. Substitui o {@code ApiKeyFilter} puro:
     * guarda apenas {@code /api/whatsapp/**} (menos o login), aceitando X-API-Key (ERP) OU
     * Bearer JWT (atendente). Webhook, monitor, health, swagger e estaticos passam livres —
     * mesma cobertura publica que o ApiKeyFilter dava, agora dentro do proprio filtro.
     */
    @Bean
    public FilterRegistrationBean<WhatsappAuthFilter> whatsappAuthFilter(
            @Value("${modulo.api-key:}") String apiKey, JwtService jwtService) {
        FilterRegistrationBean<WhatsappAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new WhatsappAuthFilter(apiKey, jwtService));
        registration.addUrlPatterns("/*");
        // HIGHEST_PRECEDENCE + 10 — depois do HmacSignatureFilter, antes de
        // qualquer outro filter custom que apareca em phases futuras.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
