package br.com.erpkit.contabil.dto;

import jakarta.validation.constraints.NotNull;

/** Requisição de encerramento de exercício. */
public class EncerrarExercicioDTO {

    @NotNull(message = "Ano é obrigatório")
    private Integer ano;

    private String por;

    public Integer getAno() { return ano; }
    public void setAno(Integer ano) { this.ano = ano; }

    public String getPor() { return por; }
    public void setPor(String por) { this.por = por; }
}
