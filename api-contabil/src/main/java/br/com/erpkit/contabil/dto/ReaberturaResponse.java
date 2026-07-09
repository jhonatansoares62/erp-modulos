package br.com.erpkit.contabil.dto;

/** Resultado da reabertura de exercício: lançamentos de encerramento revertidos e pendências reprocessadas. */
public class ReaberturaResponse {

    private int ano;
    private int lancamentosEstornados;
    private int pendenciasReprocessadas;

    public ReaberturaResponse() {
    }

    public ReaberturaResponse(int ano, int lancamentosEstornados, int pendenciasReprocessadas) {
        this.ano = ano;
        this.lancamentosEstornados = lancamentosEstornados;
        this.pendenciasReprocessadas = pendenciasReprocessadas;
    }

    public int getAno() { return ano; }
    public void setAno(int ano) { this.ano = ano; }

    public int getLancamentosEstornados() { return lancamentosEstornados; }
    public void setLancamentosEstornados(int lancamentosEstornados) { this.lancamentosEstornados = lancamentosEstornados; }

    public int getPendenciasReprocessadas() { return pendenciasReprocessadas; }
    public void setPendenciasReprocessadas(int pendenciasReprocessadas) { this.pendenciasReprocessadas = pendenciasReprocessadas; }
}
