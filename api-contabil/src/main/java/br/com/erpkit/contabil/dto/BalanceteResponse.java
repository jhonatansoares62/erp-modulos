package br.com.erpkit.contabil.dto;

import java.time.LocalDate;
import java.util.List;

/** Balancete de verificação: uma linha por conta analítica com movimento no período. */
public class BalanceteResponse {

    private LocalDate de;
    private LocalDate ate;
    private List<Linha> linhas;
    private long totalDebitos;
    private long totalCreditos;
    private boolean fecha;   // totalDebitos == totalCreditos

    public BalanceteResponse(LocalDate de, LocalDate ate, List<Linha> linhas,
                             long totalDebitos, long totalCreditos) {
        this.de = de;
        this.ate = ate;
        this.linhas = linhas;
        this.totalDebitos = totalDebitos;
        this.totalCreditos = totalCreditos;
        this.fecha = totalDebitos == totalCreditos;
    }

    public LocalDate getDe() { return de; }
    public LocalDate getAte() { return ate; }
    public List<Linha> getLinhas() { return linhas; }
    public long getTotalDebitos() { return totalDebitos; }
    public long getTotalCreditos() { return totalCreditos; }
    public boolean isFecha() { return fecha; }

    /** Linha do balancete: saldo em centavos, com o lado (D/C) do saldo. */
    public static class Linha {
        private String codigo;
        private String nome;
        private long debitos;
        private long creditos;
        private long saldoCentavos;
        private String saldoNatureza;   // D | C

        public Linha(String codigo, String nome, long debitos, long creditos,
                     long saldoCentavos, String saldoNatureza) {
            this.codigo = codigo;
            this.nome = nome;
            this.debitos = debitos;
            this.creditos = creditos;
            this.saldoCentavos = saldoCentavos;
            this.saldoNatureza = saldoNatureza;
        }

        public String getCodigo() { return codigo; }
        public String getNome() { return nome; }
        public long getDebitos() { return debitos; }
        public long getCreditos() { return creditos; }
        public long getSaldoCentavos() { return saldoCentavos; }
        public String getSaldoNatureza() { return saldoNatureza; }
    }
}
