package br.com.erpkit.contabil.config;

import br.com.erpkit.shared.security.ApiKeyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra o {@link ApiKeyFilter} (lib-shared) para proteger os endpoints internos
 * com {@code X-API-Key}. Paths públicos padrão (/health, /api/info, /swagger-ui,
 * /v3/api-docs) já são liberados pelo filtro.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(@Value("${modulo.api-key:}") String apiKey) {
        FilterRegistrationBean<ApiKeyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ApiKeyFilter(apiKey));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
