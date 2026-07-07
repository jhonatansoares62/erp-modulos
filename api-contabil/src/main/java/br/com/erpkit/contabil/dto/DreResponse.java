package br.com.erpkit.contabil.dto;

import java.time.LocalDate;

/**
 * DRE simplificada (modelo Anexo 3 da ITG 1000). Valores em centavos.
 * No Simples, o DAS entra em deduções (não há linha de IRPJ/CSLL).
 */
public class DreResponse {

    private LocalDate de;
    private LocalDate ate;
    private long receitaBruta;
    private long deducoes;
    private long receitaLiquida;
    private long custos;
    private long lucroBruto;
    private long despesasOperacionais;
    private long despesasFinanceiras;
    private long receitasFinanceiras;
    private long resultadoLiquido;

    public LocalDate getDe() { return de; }
    public void setDe(LocalDate de) { this.de = de; }

    public LocalDate getAte() { return ate; }
    public void setAte(LocalDate ate) { this.ate = ate; }

    public long getReceitaBruta() { return receitaBruta; }
    public void setReceitaBruta(long receitaBruta) { this.receitaBruta = receitaBruta; }

    public long getDeducoes() { return deducoes; }
    public void setDeducoes(long deducoes) { this.deducoes = deducoes; }

    public long getReceitaLiquida() { return receitaLiquida; }
    public void setReceitaLiquida(long receitaLiquida) { this.receitaLiquida = receitaLiquida; }

    public long getCustos() { return custos; }
    public void setCustos(long custos) { this.custos = custos; }

    public long getLucroBruto() { return lucroBruto; }
    public void setLucroBruto(long lucroBruto) { this.lucroBruto = lucroBruto; }

    public long getDespesasOperacionais() { return despesasOperacionais; }
    public void setDespesasOperacionais(long despesasOperacionais) { this.despesasOperacionais = despesasOperacionais; }

    public long getDespesasFinanceiras() { return despesasFinanceiras; }
    public void setDespesasFinanceiras(long despesasFinanceiras) { this.despesasFinanceiras = despesasFinanceiras; }

    public long getReceitasFinanceiras() { return receitasFinanceiras; }
    public void setReceitasFinanceiras(long receitasFinanceiras) { this.receitasFinanceiras = receitasFinanceiras; }

    public long getResultadoLiquido() { return resultadoLiquido; }
    public void setResultadoLiquido(long resultadoLiquido) { this.resultadoLiquido = resultadoLiquido; }
}
