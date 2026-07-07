package br.com.erpkit.contabil.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Saldos de abertura informados pelo usuário. Cada saldo é o valor natural (positivo) da conta
 * na data de abertura; o módulo deriva D/C pela natureza da conta e balanceia o eventual
 * residual em Lucros/Prejuízos Acumulados.
 */
public class AberturaInformadaRequest {

    private LocalDate dataAbertura;
    private String informadoPor;
    private List<Saldo> saldos;

    public LocalDate getDataAbertura() { return dataAbertura; }
    public void setDataAbertura(LocalDate dataAbertura) { this.dataAbertura = dataAbertura; }
    public String getInformadoPor() { return informadoPor; }
    public void setInformadoPor(String informadoPor) { this.informadoPor = informadoPor; }
    public List<Saldo> getSaldos() { return saldos; }
    public void setSaldos(List<Saldo> saldos) { this.saldos = saldos; }

    public static class Saldo {
        private String codigo;
        private long valorCentavos;

        public String getCodigo() { return codigo; }
        public void setCodigo(String codigo) { this.codigo = codigo; }
        public long getValorCentavos() { return valorCentavos; }
        public void setValorCentavos(long valorCentavos) { this.valorCentavos = valorCentavos; }
    }
}
