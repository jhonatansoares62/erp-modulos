package br.com.erpkit.contabil.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Livro Diário: lançamentos do período em ordem cronológica, cada um com suas partidas (D=C). */
public class DiarioResponse {

    private LocalDate de;
    private LocalDate ate;
    private List<Lancamento> lancamentos;

    public DiarioResponse(LocalDate de, LocalDate ate, List<Lancamento> lancamentos) {
        this.de = de;
        this.ate = ate;
        this.lancamentos = lancamentos;
    }

    public LocalDate getDe() { return de; }
    public LocalDate getAte() { return ate; }
    public List<Lancamento> getLancamentos() { return lancamentos; }

    public static class Lancamento {
        private long numero;
        private LocalDate data;
        private String historico;
        private long totalDebitoCentavos;
        private long totalCreditoCentavos;
        private final List<Partida> partidas = new ArrayList<>();

        public Lancamento(long numero, LocalDate data, String historico) {
            this.numero = numero;
            this.data = data;
            this.historico = historico;
        }

        public void addDebito(long v) { this.totalDebitoCentavos += v; }
        public void addCredito(long v) { this.totalCreditoCentavos += v; }

        public long getNumero() { return numero; }
        public LocalDate getData() { return data; }
        public String getHistorico() { return historico; }
        public long getTotalDebitoCentavos() { return totalDebitoCentavos; }
        public long getTotalCreditoCentavos() { return totalCreditoCentavos; }
        public boolean isBalanceado() { return totalDebitoCentavos == totalCreditoCentavos; }
        public List<Partida> getPartidas() { return partidas; }
    }

    public static class Partida {
        private String codigo;
        private String nome;
        private String tipo;            // D | C
        private long valorCentavos;

        public Partida(String codigo, String nome, String tipo, long valorCentavos) {
            this.codigo = codigo;
            this.nome = nome;
            this.tipo = tipo;
            this.valorCentavos = valorCentavos;
        }

        public String getCodigo() { return codigo; }
        public String getNome() { return nome; }
        public String getTipo() { return tipo; }
        public long getValorCentavos() { return valorCentavos; }
    }
}
