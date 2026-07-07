package br.com.erpkit.contabil.dto;

import java.time.LocalDate;
import java.util.List;

/** Razão de uma conta: movimentos cronológicos com saldo acumulado (na direção natural da conta). */
public class RazaoResponse {

    private String codigo;
    private String nome;
    private LocalDate de;
    private LocalDate ate;
    private List<Linha> linhas;
    private long saldoInicialCentavos;
    private long saldoFinalCentavos;

    public RazaoResponse(String codigo, String nome, LocalDate de, LocalDate ate,
                         List<Linha> linhas, long saldoInicialCentavos, long saldoFinalCentavos) {
        this.codigo = codigo;
        this.nome = nome;
        this.de = de;
        this.ate = ate;
        this.linhas = linhas;
        this.saldoInicialCentavos = saldoInicialCentavos;
        this.saldoFinalCentavos = saldoFinalCentavos;
    }

    public String getCodigo() { return codigo; }
    public String getNome() { return nome; }
    public LocalDate getDe() { return de; }
    public LocalDate getAte() { return ate; }
    public List<Linha> getLinhas() { return linhas; }
    public long getSaldoInicialCentavos() { return saldoInicialCentavos; }
    public long getSaldoFinalCentavos() { return saldoFinalCentavos; }

    public static class Linha {
        private LocalDate data;
        private Long numero;
        private String historico;
        private String tipo;            // D | C
        private long valorCentavos;
        private long saldoAcumuladoCentavos;

        public Linha(LocalDate data, Long numero, String historico, String tipo,
                     long valorCentavos, long saldoAcumuladoCentavos) {
            this.data = data;
            this.numero = numero;
            this.historico = historico;
            this.tipo = tipo;
            this.valorCentavos = valorCentavos;
            this.saldoAcumuladoCentavos = saldoAcumuladoCentavos;
        }

        public LocalDate getData() { return data; }
        public Long getNumero() { return numero; }
        public String getHistorico() { return historico; }
        public String getTipo() { return tipo; }
        public long getValorCentavos() { return valorCentavos; }
        public long getSaldoAcumuladoCentavos() { return saldoAcumuladoCentavos; }
    }
}
