package br.com.erpkit.whatsapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Config da auth propria do modulo whatsapp (JWT + seed do atendente + nome da empresa
 * no header do app standalone). Todos os valores tem default de dev e sao sobrescritos
 * por env/erpkit em producao. Aditiva — nao interfere no X-API-Key (ERP) nem no HMAC (webhook).
 */
@Component
@ConfigurationProperties(prefix = "whatsapp.auth")
public class AuthProperties {

    /** Segredo HS256 — deve ter >= 32 chars. */
    private String jwtSecret = "dev-whatsapp-secret-troque-em-producao-0123456789";
    private int jwtExpirationHours = 12;
    private String seedEmail = "atendente@erpkit.local";
    private String seedPassword = "Whats@2026";
    private String seedNome = "Atendente";
    /** Nome da empresa exibido no header do app (single-company). */
    private String empresaNome = "ERP";

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    public int getJwtExpirationHours() { return jwtExpirationHours; }
    public void setJwtExpirationHours(int jwtExpirationHours) { this.jwtExpirationHours = jwtExpirationHours; }
    public String getSeedEmail() { return seedEmail; }
    public void setSeedEmail(String seedEmail) { this.seedEmail = seedEmail; }
    public String getSeedPassword() { return seedPassword; }
    public void setSeedPassword(String seedPassword) { this.seedPassword = seedPassword; }
    public String getSeedNome() { return seedNome; }
    public void setSeedNome(String seedNome) { this.seedNome = seedNome; }
    public String getEmpresaNome() { return empresaNome; }
    public void setEmpresaNome(String empresaNome) { this.empresaNome = empresaNome; }
}
