package br.com.erpkit.contabil.dto;

/** Resultado do reprocessamento em lote das pendências (eventos sem_regra). */
public class ReprocessamentoResponse {

    private int total;
    private int reprocessados;
    private int aindaPendentes;

    public ReprocessamentoResponse(int total, int reprocessados, int aindaPendentes) {
        this.total = total;
        this.reprocessados = reprocessados;
        this.aindaPendentes = aindaPendentes;
    }

    public int getTotal() { return total; }
    public int getReprocessados() { return reprocessados; }
    public int getAindaPendentes() { return aindaPendentes; }
}
