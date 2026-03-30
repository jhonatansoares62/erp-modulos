package br.com.erpkit.shared.dto;

import java.util.List;

public class InfoResponse {

    private String nome;
    private String versao;
    private String descricao;
    private List<String> capabilities;

    public InfoResponse() {
    }

    public InfoResponse(String nome, String versao, String descricao, List<String> capabilities) {
        this.nome = nome;
        this.versao = versao;
        this.descricao = descricao;
        this.capabilities = capabilities;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getVersao() {
        return versao;
    }

    public void setVersao(String versao) {
        this.versao = versao;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public List<String> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<String> capabilities) {
        this.capabilities = capabilities;
    }
}
