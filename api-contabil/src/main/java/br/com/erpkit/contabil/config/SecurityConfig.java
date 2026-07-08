package br.com.erpkit.contabil.config;

import br.com.erpkit.contabil.security.ContabilAuthFilter;
import br.com.erpkit.contabil.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra o {@link ContabilAuthFilter}, que aceita X-API-Key (service/ERP) OU Bearer JWT
 * (contador). Substitui o antigo ApiKeyFilter puro, preservando a semântica de X-API-Key
 * para não quebrar os eventos do ERP. O CORS (origem do app standalone) já vem liberado
 * pelo CorsFilter do lib-shared.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<ContabilAuthFilter> authFilter(
            @Value("${modulo.api-key:}") String apiKey, JwtService jwtService) {
        FilterRegistrationBean<ContabilAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ContabilAuthFilter(apiKey, jwtService));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
