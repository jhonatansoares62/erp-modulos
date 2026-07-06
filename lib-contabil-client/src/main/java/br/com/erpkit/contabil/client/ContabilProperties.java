package br.com.erpkit.contabil.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.modulos.contabil")
public class ContabilProperties {

    private boolean enabled = false;
    private String url = "http://localhost:9194";
    private String apiKey;
    private int timeout = 5000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }
}
