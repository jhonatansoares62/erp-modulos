package br.com.erpkit.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Resposta JSON padrao para erros — usado por {@code GlobalExceptionHandler} (lib-shared)
 * e por todos os modulos {@code api-*}.
 *
 * <p><b>@JsonInclude(NON_NULL)</b> garante que campos opcionais ({@link #campos},
 * {@link #codigo}, {@link #metaErrorCode}) sejam OMITIDOS do JSON quando nao setados.
 * Isso preserva backward-compat com api-email/api-storage/api-consultas que nunca
 * setam codigo/metaErrorCode (Phase 4 introduziu esses campos para api-whatsapp).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int status;
    private String erro;
    private String mensagem;
    private LocalDateTime timestamp;
    private Map<String, String> campos;

    /**
     * Codigo de erro estruturado (Phase 4 — Modulo WhatsApp).
     * Ex: {@code "JANELA_24H_FECHADA"}, {@code "META_ERROR"}, {@code "CIRCUIT_OPEN"}.
     * Populado por {@code GlobalExceptionHandler} quando a excecao implementa
     * {@code CodigoCarrier}. Null para outros modulos.
     */
    private String codigo;

    /**
     * Codigo numerico do provedor externo (Phase 4 — Meta API).
     * Ex: {@code 131026} (invalid phone), {@code 131051} (unsupported message type).
     * Null quando nao aplicavel (ex: erros locais como JANELA_24H_FECHADA).
     */
    private Integer metaErrorCode;

    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(int status, String erro, String mensagem) {
        this.status = status;
        this.erro = erro;
        this.mensagem = mensagem;
        this.timestamp = LocalDateTime.now();
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getErro() {
        return erro;
    }

    public void setErro(String erro) {
        this.erro = erro;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, String> getCampos() {
        return campos;
    }

    public void setCampos(Map<String, String> campos) {
        this.campos = campos;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public Integer getMetaErrorCode() {
        return metaErrorCode;
    }

    public void setMetaErrorCode(Integer metaErrorCode) {
        this.metaErrorCode = metaErrorCode;
    }
}
