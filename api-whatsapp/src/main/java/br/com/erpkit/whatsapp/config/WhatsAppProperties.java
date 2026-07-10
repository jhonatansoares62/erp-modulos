package br.com.erpkit.whatsapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuracao do modulo api-whatsapp.
 *
 * <p><b>Config Meta em runtime:</b> as 4 credenciais Meta (phoneNumberId,
 * accessToken, appSecret, verifyToken) NAO sao mais obrigatorias no boot — o
 * modulo sobe sem elas. O {@code MetaConfigService} le a linha
 * {@code whatsapp.config_meta} (V6) no boot e sobrepoe estes campos no bean vivo;
 * o {@code PUT /api/whatsapp/config} reaplica em runtime. A env var
 * ({@code WHATSAPP_*}) continua sendo o seed/fallback quando o banco esta vazio.
 * Estes 4 campos sao {@code volatile}: escritos pelo MetaConfigService e lidos por
 * threads de request (WhatsAppCloudClient, HmacSignatureFilter, WebhookController).
 *
 * <p>{@code erpCallbackUrl} continua vindo do ambiente (URL do ERP co-instalado,
 * definida pelo instalador) — nao e credencial Meta e o {@code ErpCallbackClient}
 * a captura na construcao, portanto NAO e editavel por este fluxo de runtime.
 *
 * <p>{@link #toString()} mascara accessToken/appSecret/verifyToken — Spring pode
 * imprimir Properties em log de erro de bind, e secret em log de PROD vaza para
 * operadores e SIEM (CFG-03 / PITFALLS).
 */
@ConfigurationProperties(prefix = "app.modulos.whatsapp")
public class WhatsAppProperties {

    private volatile String phoneNumberId;

    private volatile String accessToken;

    private volatile String appSecret;

    private volatile String verifyToken;

    private String erpCallbackUrl;

    /** Timeout do callback ao ERP (Phase 3+). Default 5s. */
    private Duration callbackTimeout = Duration.ofSeconds(5);

    /** Base URL da Graph API do Meta (override em tests). Default v22.0. */
    private String metaApiBaseUrl = "https://graph.facebook.com/v22.0";

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getVerifyToken() {
        return verifyToken;
    }

    public void setVerifyToken(String verifyToken) {
        this.verifyToken = verifyToken;
    }

    public String getErpCallbackUrl() {
        return erpCallbackUrl;
    }

    public void setErpCallbackUrl(String erpCallbackUrl) {
        this.erpCallbackUrl = erpCallbackUrl;
    }

    public Duration getCallbackTimeout() {
        return callbackTimeout;
    }

    public void setCallbackTimeout(Duration callbackTimeout) {
        this.callbackTimeout = callbackTimeout;
    }

    public String getMetaApiBaseUrl() {
        return metaApiBaseUrl;
    }

    public void setMetaApiBaseUrl(String metaApiBaseUrl) {
        this.metaApiBaseUrl = metaApiBaseUrl;
    }

    /**
     * {@code true} sse as 4 credenciais Meta estao preenchidas. Enviar mensagem e
     * responder o webhook do Meta so funcionam quando configurado; o painel de
     * Modulos do ERP usa esse flag para sinalizar "pronto para operar".
     */
    public boolean isMetaConfigurado() {
        return naoVazio(phoneNumberId)
            && naoVazio(accessToken)
            && naoVazio(appSecret)
            && naoVazio(verifyToken);
    }

    private static boolean naoVazio(String v) {
        return v != null && !v.isBlank();
    }

    @Override
    public String toString() {
        return "WhatsAppProperties{phoneNumberId=" + phoneNumberId
                + ", accessToken=[REDACTED]"
                + ", appSecret=[REDACTED]"
                + ", verifyToken=[REDACTED]"
                + ", erpCallbackUrl=" + erpCallbackUrl
                + ", callbackTimeout=" + callbackTimeout
                + ", metaApiBaseUrl=" + metaApiBaseUrl + "}";
    }
}
