package br.com.erpkit.contabil.dto;

import jakarta.validation.constraints.NotBlank;

/** Fechamento de período mensal. */
public class FecharPeriodoDTO {

    @NotBlank(message = "Competência é obrigatória (YYYY-MM)")
    private String competencia;

    private String fechadoPor;

    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }

    public String getFechadoPor() { return fechadoPor; }
    public void setFechadoPor(String fechadoPor) { this.fechadoPor = fechadoPor; }
}
