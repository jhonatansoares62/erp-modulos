package br.com.erpkit.backup.config;

import br.com.erpkit.backup.security.BackupApiKeyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra o {@link BackupApiKeyFilter}. O modulo de backup e HEADLESS (sem persona/login):
 * so o ERP fala com ele, via X-API-Key. Sem starter-security (evita subir uma filter chain
 * completa); o CORS ja vem liberado pelo CorsFilter do lib-shared.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<BackupApiKeyFilter> backupAuthFilter(
            @Value("${modulo.api-key:}") String apiKey) {
        FilterRegistrationBean<BackupApiKeyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new BackupApiKeyFilter(apiKey));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
