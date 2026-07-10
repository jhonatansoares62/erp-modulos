package br.com.erpkit.whatsapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Credenciais Meta editaveis em runtime — mapeia 1:1 com {@code whatsapp.config_meta}
 * (V6). Linha unica: {@code id} SEMPRE 1 (singleton, CHECK no banco). O
 * {@code MetaConfigService} le no boot e sobrepoe no {@link
 * br.com.erpkit.whatsapp.config.WhatsAppProperties}; o {@code PUT /api/whatsapp/config}
 * faz upsert aqui e reaplica no bean vivo.
 *
 * <p>Sem {@code @GeneratedValue}: o id e atribuido pela aplicacao (=1). Spring Data
 * trata a entity como "nao nova" (id != null) e faz merge — INSERT na primeira vez,
 * UPDATE nas seguintes.
 *
 * <p>{@link #toString()} mascara os 3 secrets — a entity pode aparecer em log de erro.
 */
@Entity
@Table(schema = "whatsapp", name = "config_meta")
public class ConfigMeta {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "phone_number_id", length = 64)
    private String phoneNumberId;

    @Column(name = "access_token", length = 1024)
    private String accessToken;

    @Column(name = "app_secret", length = 255)
    private String appSecret;

    @Column(name = "verify_token", length = 255)
    private String verifyToken;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public ConfigMeta() {
        // JPA exige construtor padrao
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public String getVerifyToken() { return verifyToken; }
    public void setVerifyToken(String verifyToken) { this.verifyToken = verifyToken; }

    public Instant getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(Instant atualizadoEm) { this.atualizadoEm = atualizadoEm; }

    @Override
    public String toString() {
        return "ConfigMeta{id=" + id
             + ", phoneNumberId=" + phoneNumberId
             + ", accessToken=[REDACTED]"
             + ", appSecret=[REDACTED]"
             + ", verifyToken=[REDACTED]"
             + ", atualizadoEm=" + atualizadoEm + "}";
    }
}
