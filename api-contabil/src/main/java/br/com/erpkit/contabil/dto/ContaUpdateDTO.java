package br.com.erpkit.contabil.dto;

import jakarta.validation.constraints.NotBlank;

/** Edição de conta. Só nome, natureza e retificadora são alteráveis; código, tipo e grupo são imutáveis. */
public class ContaUpdateDTO {

    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    @NotBlank(message = "Natureza é obrigatória (D|C)")
    private String natureza;

    private boolean retificadora;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getNatureza() { return natureza; }
    public void setNatureza(String natureza) { this.natureza = natureza; }

    public boolean isRetificadora() { return retificadora; }
    public void setRetificadora(boolean retificadora) { this.retificadora = retificadora; }
}
