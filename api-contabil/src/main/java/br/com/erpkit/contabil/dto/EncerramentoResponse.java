package br.com.erpkit.contabil.dto;

import java.util.List;

/** Resumo do encerramento de exercício: totais apurados e os lançamentos gerados. */
public class EncerramentoResponse {

    private int ano;
    private long totalReceitas;
    private long totalDespesas;
    private long resultado;
    private List<Long> lancamentoIds;

    public EncerramentoResponse() {
    }

    public EncerramentoResponse(int ano, long totalReceitas, long totalDespesas, long resultado, List<Long> lancamentoIds) {
        this.ano = ano;
        this.totalReceitas = totalReceitas;
        this.totalDespesas = totalDespesas;
        this.resultado = resultado;
        this.lancamentoIds = lancamentoIds;
    }

    public int getAno() { return ano; }
    public void setAno(int ano) { this.ano = ano; }

    public long getTotalReceitas() { return totalReceitas; }
    public void setTotalReceitas(long totalReceitas) { this.totalReceitas = totalReceitas; }

    public long getTotalDespesas() { return totalDespesas; }
    public void setTotalDespesas(long totalDespesas) { this.totalDespesas = totalDespesas; }

    public long getResultado() { return resultado; }
    public void setResultado(long resultado) { this.resultado = resultado; }

    public List<Long> getLancamentoIds() { return lancamentoIds; }
    public void setLancamentoIds(List<Long> lancamentoIds) { this.lancamentoIds = lancamentoIds; }
}
