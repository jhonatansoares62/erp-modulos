package br.com.erpkit.contabil.dto;

import jakarta.validation.constraints.NotBlank;

/** Criação de conta. tipo/natureza/grupo validados no serviço; nível derivado do código. */
public class ContaCreateDTO {

    @NotBlank(message = "Código é obrigatório")
    private String codigo;

    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    @NotBlank(message = "Tipo é obrigatório (sintetica|analitica)")
    private String tipo;

    @NotBlank(message = "Natureza é obrigatória (D|C)")
    private String natureza;

    @NotBlank(message = "Grupo é obrigatório")
    private String grupo;

    private boolean retificadora;

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getNatureza() { return natureza; }
    public void setNatureza(String natureza) { this.natureza = natureza; }

    public String getGrupo() { return grupo; }
    public void setGrupo(String grupo) { this.grupo = grupo; }

    public boolean isRetificadora() { return retificadora; }
    public void setRetificadora(boolean retificadora) { this.retificadora = retificadora; }
}
