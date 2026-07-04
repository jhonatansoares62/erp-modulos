package br.com.erpkit.whatsapp.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuracao do cliente WhatsApp. Prefixo {@code app.modulos.whatsapp}.
 *
 * <ul>
 *   <li>{@code enabled} — default {@code false}: adicionar a lib ao pom sem habilitar
 *       explicitamente NAO cria beans (conservative, espelha lib-consultas-client).</li>
 *   <li>{@code url} — default {@code http://localhost:9193}: ERP roda on-premise no
 *       mesmo host do api-whatsapp. Override via {@code APP_MODULOS_WHATSAPP_URL}.</li>
 *   <li>{@code apiKey} — opcional; quando nao-blank, enviado no header {@code X-API-Key}.</li>
 *   <li>{@code timeout} — default 5s (connect + read).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.modulos.whatsapp")
public class WhatsAppProperties {

    private boolean enabled = false;
    private String url = "http://localhost:9193";
    private String apiKey;
    private Duration timeout = Duration.ofSeconds(5);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
}
