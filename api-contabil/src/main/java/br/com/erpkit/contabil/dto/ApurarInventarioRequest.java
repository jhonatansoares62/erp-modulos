package br.com.erpkit.contabil.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

/** Requisição de apuração de CMV: período + estoque final contado (centavos). */
public class ApurarInventarioRequest {

    @NotNull
    private LocalDate de;

    @NotNull
    private LocalDate ate;

    @NotNull
    @PositiveOrZero
    private Long estoqueFinalCentavos;

    private String apuradoPor;

    public LocalDate getDe() { return de; }
    public void setDe(LocalDate de) { this.de = de; }
    public LocalDate getAte() { return ate; }
    public void setAte(LocalDate ate) { this.ate = ate; }
    public Long getEstoqueFinalCentavos() { return estoqueFinalCentavos; }
    public void setEstoqueFinalCentavos(Long estoqueFinalCentavos) { this.estoqueFinalCentavos = estoqueFinalCentavos; }
    public String getApuradoPor() { return apuradoPor; }
    public void setApuradoPor(String apuradoPor) { this.apuradoPor = apuradoPor; }
}
