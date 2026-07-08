package br.com.erpkit.contabil.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Config da auth própria do módulo contábil (JWT + seed do contador + CORS do app standalone).
 * Todos os valores têm default de dev e são sobrescritos por env/erpkit em produção.
 */
@Component
@ConfigurationProperties(prefix = "contabil.auth")
public class AuthProperties {

    /** Segredo HS256 — deve ter >= 32 chars. */
    private String jwtSecret = "dev-contabil-secret-troque-em-producao-0123456789";
    private int jwtExpirationHours = 12;
    private String seedEmail = "contador@erpkit.local";
    private String seedPassword = "Contabil@2026";
    private String seedNome = "Contador";
    /** Nome da empresa exibido no header do app (single-company). */
    private String empresaNome = "ERP Odonto";

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
