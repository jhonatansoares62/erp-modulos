package br.com.erpkit.email.dto;

public class PresetSmtpResponse {

    private String codigo;
    private String host;
    private int porta;
    private boolean tls;
    private String instrucoes;

    public PresetSmtpResponse() {
    }

    public PresetSmtpResponse(String codigo, String host, int porta, boolean tls, String instrucoes) {
        this.codigo = codigo;
        this.host = host;
        this.porta = porta;
        this.tls = tls;
        this.instrucoes = instrucoes;
    }

    // Getters e Setters

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPorta() {
        return porta;
    }

    public void setPorta(int porta) {
        this.porta = porta;
    }

    public boolean isTls() {
        return tls;
    }

    public void setTls(boolean tls) {
        this.tls = tls;
    }

    public String getInstrucoes() {
        return instrucoes;
    }

    public void setInstrucoes(String instrucoes) {
        this.instrucoes = instrucoes;
    }
}
