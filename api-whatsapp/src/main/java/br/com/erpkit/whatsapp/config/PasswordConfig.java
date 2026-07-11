package br.com.erpkit.whatsapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt para as senhas do atendente. Usa apenas spring-security-crypto (sem starter-security),
 * entao NAO ha filter chain do Spring Security — a auth do modulo e feita pelo WhatsappAuthFilter.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
