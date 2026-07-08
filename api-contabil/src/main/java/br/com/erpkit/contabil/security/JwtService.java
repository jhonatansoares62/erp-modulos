package br.com.erpkit.contabil.security;

import br.com.erpkit.contabil.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Emissão/validação de JWT HS256 para o contador. Subject = e-mail; claim "nome".
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final int expirationHours;

    public JwtService(AuthProperties props) {
        byte[] secret = props.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("contabil.auth.jwt-secret deve ter no mínimo 32 caracteres");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.expirationHours = props.getJwtExpirationHours();
    }

    public String gerar(String email, String nome) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("nome", nome)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(expirationHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /** Retorna o e-mail (subject) se o token for válido; null caso contrário. */
    public String validar(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}
