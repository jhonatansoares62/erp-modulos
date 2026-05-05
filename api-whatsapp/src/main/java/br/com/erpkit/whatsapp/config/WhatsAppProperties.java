package br.com.erpkit.whatsapp.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuracao do modulo api-whatsapp. Falha rapido no boot via Bean Validation
 * se qualquer secret estiver ausente — mensagens em PT-BR identificando a env var.
 *
 * <p>Mensagens dos {@code @NotBlank} usam "nao" sem til por consistencia com a
 * suite de testes (WhatsAppPropertiesValidationTest faz match exato da string).
 *
 * <p>{@link #toString()} mascara accessToken/appSecret/verifyToken — Spring pode
 * imprimir Properties em log de erro de bind, e secret em log de PROD vaza para
 * operadores e SIEM (CFG-03 / PITFALLS).
 */
@ConfigurationProperties(prefix = "app.modulos.whatsapp")
@Validated
public class WhatsAppProperties {

    @NotBlank(message = "WHATSAPP_PHONE_NUMBER_ID nao definida")
    private String phoneNumberId;

    @NotBlank(message = "WHATSAPP_ACCESS_TOKEN nao definida")
    private String accessToken;

    @NotBlank(message = "WHATSAPP_APP_SECRET nao definida")
    private String appSecret;

    @NotBlank(message = "WHATSAPP_VERIFY_TOKEN nao definida")
    private String verifyToken;

    @NotBlank(message = "WHATSAPP_ERP_CALLBACK_URL nao definida")
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
