package br.com.erpkit.contabil.dto;

import jakarta.validation.constraints.NotNull;

/** Requisição de reabertura de exercício encerrado. */
public class ReabrirExercicioDTO {

    @NotNull(message = "Ano é obrigatório")
    private Integer ano;

    private String por;

    private String motivo;

    public Integer getAno() { return ano; }
    public void setAno(Integer ano) { this.ano = ano; }

    public String getPor() { return por; }
    public void setPor(String por) { this.por = por; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
