package br.com.erpkit.contabil.dto;

/**
 * Prévia do encerramento de exercício, sem postar nada. Espelha exatamente o que o encerramento
 * vai apurar (mesma soma, excluindo lançamentos de encerramento anteriores), para o contador ver
 * receitas, custos, despesas e o resultado (lucro/prejuízo) antes de confirmar. 'encerrado'
 * indica se o exercício já foi encerrado (bloqueia novo encerramento).
 */
public class EncerramentoPreviewResponse {

    private int ano;
    private boolean encerrado;
    private long receitas;
    private long custos;
    private long despesas;
    private long resultado;

    public EncerramentoPreviewResponse() {
    }

    public EncerramentoPreviewResponse(int ano, boolean encerrado, long receitas, long custos,
                                       long despesas, long resultado) {
        this.ano = ano;
        this.encerrado = encerrado;
        this.receitas = receitas;
        this.custos = custos;
        this.despesas = despesas;
        this.resultado = resultado;
    }

    public int getAno() { return ano; }
    public void setAno(int ano) { this.ano = ano; }

    public boolean isEncerrado() { return encerrado; }
    public void setEncerrado(boolean encerrado) { this.encerrado = encerrado; }

    public long getReceitas() { return receitas; }
    public void setReceitas(long receitas) { this.receitas = receitas; }

    public long getCustos() { return custos; }
    public void setCustos(long custos) { this.custos = custos; }

    public long getDespesas() { return despesas; }
    public void setDespesas(long despesas) { this.despesas = despesas; }

    public long getResultado() { return resultado; }
    public void setResultado(long resultado) { this.resultado = resultado; }
}
