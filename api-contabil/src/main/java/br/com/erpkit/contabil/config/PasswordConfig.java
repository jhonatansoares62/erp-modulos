package br.com.erpkit.contabil.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt para as senhas do contador. Usa apenas spring-security-crypto (sem starter-security),
 * então NÃO há filter chain do Spring Security — a auth do módulo é feita pelo ContabilAuthFilter.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
