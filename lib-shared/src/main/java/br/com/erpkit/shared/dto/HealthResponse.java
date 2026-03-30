package br.com.erpkit.shared.dto;

public class HealthResponse {

    private String status;
    private String modulo;
    private String versao;

    public HealthResponse() {
    }

    public HealthResponse(String status, String modulo, String versao) {
        this.status = status;
        this.modulo = modulo;
        this.versao = versao;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getModulo() {
        return modulo;
    }

    public void setModulo(String modulo) {
        this.modulo = modulo;
    }

    public String getVersao() {
        return versao;
    }

    public void setVersao(String versao) {
        this.versao = versao;
    }
}
