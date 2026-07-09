package br.com.erpkit.contabil.fiscal.dto;

/** Item de receita bruta histórica por competência ('YYYY-MM'). Usado no GET/PUT /v1/fiscal/receita-historica. */
public class ReceitaHistoricaDTO {

    private String competencia;
    private long receitaBrutaCentavos;

    public ReceitaHistoricaDTO() {
    }

    public ReceitaHistoricaDTO(String competencia, long receitaBrutaCentavos) {
        this.competencia = competencia;
        this.receitaBrutaCentavos = receitaBrutaCentavos;
    }

    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }
    public long getReceitaBrutaCentavos() { return receitaBrutaCentavos; }
    public void setReceitaBrutaCentavos(long receitaBrutaCentavos) { this.receitaBrutaCentavos = receitaBrutaCentavos; }
}
