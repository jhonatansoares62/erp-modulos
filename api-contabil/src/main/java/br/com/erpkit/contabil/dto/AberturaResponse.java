package br.com.erpkit.contabil.dto;

import java.time.LocalDate;
import java.util.List;

/** Estado dos saldos de abertura informados (para a tela). informada=false quando não há nenhum. */
public class AberturaResponse {

    private boolean informada;
    private LocalDate dataAbertura;
    private List<Saldo> saldos;

    public AberturaResponse(boolean informada, LocalDate dataAbertura, List<Saldo> saldos) {
        this.informada = informada;
        this.dataAbertura = dataAbertura;
        this.saldos = saldos;
    }

    public boolean isInformada() { return informada; }
    public LocalDate getDataAbertura() { return dataAbertura; }
    public List<Saldo> getSaldos() { return saldos; }

    public static class Saldo {
        private final String codigo;
        private final String nome;
        private final long valorCentavos;

        public Saldo(String codigo, String nome, long valorCentavos) {
            this.codigo = codigo;
            this.nome = nome;
            this.valorCentavos = valorCentavos;
        }

        public String getCodigo() { return codigo; }
        public String getNome() { return nome; }
        public long getValorCentavos() { return valorCentavos; }
    }
}
