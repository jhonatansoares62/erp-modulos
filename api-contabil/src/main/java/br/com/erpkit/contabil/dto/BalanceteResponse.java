package br.com.erpkit.contabil.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Balancete de verificação de 6 colunas: uma linha por conta analítica com saldo acumulado ≠ 0
 * OU movimento no período — Saldo anterior | Débitos | Créditos | Saldo atual (cada saldo com lado D/C).
 * Duas conferências: movimento (Σ débitos = Σ créditos) e saldos (Σ saldos atuais devedores = Σ credores).
 */
public class BalanceteResponse {

    private LocalDate de;
    private LocalDate ate;
    private List<Linha> linhas;
    private long totalDebitos;
    private long totalCreditos;
    private boolean fecha;   // totalDebitos == totalCreditos (movimento do período)
    private long totalSaldoAtualDevedorCentavos;
    private long totalSaldoAtualCredorCentavos;
    private boolean fechaSaldos;   // Σ saldos atuais devedores == Σ saldos atuais credores

    public BalanceteResponse(LocalDate de, LocalDate ate, List<Linha> linhas,
                             long totalDebitos, long totalCreditos,
                             long totalSaldoAtualDevedorCentavos, long totalSaldoAtualCredorCentavos) {
        this.de = de;
        this.ate = ate;
        this.linhas = linhas;
        this.totalDebitos = totalDebitos;
        this.totalCreditos = totalCreditos;
        this.fecha = totalDebitos == totalCreditos;
        this.totalSaldoAtualDevedorCentavos = totalSaldoAtualDevedorCentavos;
        this.totalSaldoAtualCredorCentavos = totalSaldoAtualCredorCentavos;
        this.fechaSaldos = totalSaldoAtualDevedorCentavos == totalSaldoAtualCredorCentavos;
    }

    public LocalDate getDe() { return de; }
    public LocalDate getAte() { return ate; }
    public List<Linha> getLinhas() { return linhas; }
    public long getTotalDebitos() { return totalDebitos; }
    public long getTotalCreditos() { return totalCreditos; }
    public boolean isFecha() { return fecha; }
    public long getTotalSaldoAtualDevedorCentavos() { return totalSaldoAtualDevedorCentavos; }
    public long getTotalSaldoAtualCredorCentavos() { return totalSaldoAtualCredorCentavos; }
    public boolean isFechaSaldos() { return fechaSaldos; }

    /**
     * Linha do balancete de 6 colunas. Saldo anterior e saldo atual em centavos, cada um com o lado (D/C).
     * saldoCentavos/saldoNatureza são mantidos como alias do saldo ATUAL (compat. com consumidores antigos).
     */
    public static class Linha {
        private String codigo;
        private String nome;
        private long saldoAnteriorCentavos;
        private String saldoAnteriorNatureza;   // D | C
        private long debitos;
        private long creditos;
        private long saldoAtualCentavos;
        private String saldoAtualNatureza;       // D | C

        public Linha(String codigo, String nome,
                     long saldoAnteriorCentavos, String saldoAnteriorNatureza,
                     long debitos, long creditos,
                     long saldoAtualCentavos, String saldoAtualNatureza) {
            this.codigo = codigo;
            this.nome = nome;
            this.saldoAnteriorCentavos = saldoAnteriorCentavos;
            this.saldoAnteriorNatureza = saldoAnteriorNatureza;
            this.debitos = debitos;
            this.creditos = creditos;
            this.saldoAtualCentavos = saldoAtualCentavos;
            this.saldoAtualNatureza = saldoAtualNatureza;
        }

        public String getCodigo() { return codigo; }
        public String getNome() { return nome; }
        public long getSaldoAnteriorCentavos() { return saldoAnteriorCentavos; }
        public String getSaldoAnteriorNatureza() { return saldoAnteriorNatureza; }
        public long getDebitos() { return debitos; }
        public long getCreditos() { return creditos; }
        public long getSaldoAtualCentavos() { return saldoAtualCentavos; }
        public String getSaldoAtualNatureza() { return saldoAtualNatureza; }

        // Compat: saldoCentavos/saldoNatureza = saldo atual.
        public long getSaldoCentavos() { return saldoAtualCentavos; }
        public String getSaldoNatureza() { return saldoAtualNatureza; }
    }
}
