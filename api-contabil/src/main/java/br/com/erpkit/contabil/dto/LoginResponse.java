package br.com.erpkit.contabil.dto;

public class LoginResponse {

    private final String token;
    private final String email;
    private final String nome;
    private final String empresa;

    public LoginResponse(String token, String email, String nome, String empresa) {
        this.token = token;
        this.email = email;
        this.nome = nome;
        this.empresa = empresa;
    }

    public String getToken() { return token; }
    public String getEmail() { return email; }
    public String getNome() { return nome; }
    public String getEmpresa() { return empresa; }
}
