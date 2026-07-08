package br.com.erpkit.contabil.dto;

public class MeResponse {

    private final String email;
    private final String nome;
    private final String empresa;

    public MeResponse(String email, String nome, String empresa) {
        this.email = email;
        this.nome = nome;
        this.empresa = empresa;
    }

    public String getEmail() { return email; }
    public String getNome() { return nome; }
    public String getEmpresa() { return empresa; }
}
