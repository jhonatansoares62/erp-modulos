package br.com.erpkit.consultas.client.dto;

import java.time.LocalDate;

public class FornecedorResponse {

    private String cnpj;
    private String razaoSocial;
    private String nomeFantasia;
    private String situacao;
    private LocalDate dataAbertura;
    private String naturezaJuridica;
    private String cnaePrincipalCodigo;
    private String cnaePrincipalDescricao;
    private String email;
    private String telefone;
    private EnderecoResponse endereco;
    private String provedor;

    public FornecedorResponse() {
    }

    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }

    public String getRazaoSocial() { return razaoSocial; }
    public void setRazaoSocial(String razaoSocial) { this.razaoSocial = razaoSocial; }

    public String getNomeFantasia() { return nomeFantasia; }
    public void setNomeFantasia(String nomeFantasia) { this.nomeFantasia = nomeFantasia; }

    public String getSituacao() { return situacao; }
    public void setSituacao(String situacao) { this.situacao = situacao; }

    public LocalDate getDataAbertura() { return dataAbertura; }
    public void setDataAbertura(LocalDate dataAbertura) { this.dataAbertura = dataAbertura; }

    public String getNaturezaJuridica() { return naturezaJuridica; }
    public void setNaturezaJuridica(String naturezaJuridica) { this.naturezaJuridica = naturezaJuridica; }

    public String getCnaePrincipalCodigo() { return cnaePrincipalCodigo; }
    public void setCnaePrincipalCodigo(String cnaePrincipalCodigo) { this.cnaePrincipalCodigo = cnaePrincipalCodigo; }

    public String getCnaePrincipalDescricao() { return cnaePrincipalDescricao; }
    public void setCnaePrincipalDescricao(String cnaePrincipalDescricao) { this.cnaePrincipalDescricao = cnaePrincipalDescricao; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public EnderecoResponse getEndereco() { return endereco; }
    public void setEndereco(EnderecoResponse endereco) { this.endereco = endereco; }

    public String getProvedor() { return provedor; }
    public void setProvedor(String provedor) { this.provedor = provedor; }
}
