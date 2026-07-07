package br.com.erpkit.contabil.fiscal.dto;

import java.time.LocalDate;

/** Config fiscal informada pela tela. */
public class FiscalConfigDTO {

    private String regime;
    private Boolean calculoAutomatico;
    private String anexo;                 // I|II|III|V|AUTO
    private LocalDate dataInicioAtividade;
    private Long folha12mCentavos;

    public String getRegime() { return regime; }
    public void setRegime(String regime) { this.regime = regime; }
    public Boolean getCalculoAutomatico() { return calculoAutomatico; }
    public void setCalculoAutomatico(Boolean calculoAutomatico) { this.calculoAutomatico = calculoAutomatico; }
    public String getAnexo() { return anexo; }
    public void setAnexo(String anexo) { this.anexo = anexo; }
    public LocalDate getDataInicioAtividade() { return dataInicioAtividade; }
    public void setDataInicioAtividade(LocalDate dataInicioAtividade) { this.dataInicioAtividade = dataInicioAtividade; }
    public Long getFolha12mCentavos() { return folha12mCentavos; }
    public void setFolha12mCentavos(Long folha12mCentavos) { this.folha12mCentavos = folha12mCentavos; }
}
