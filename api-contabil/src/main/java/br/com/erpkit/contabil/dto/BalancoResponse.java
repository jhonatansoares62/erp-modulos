package br.com.erpkit.contabil.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Balanço Patrimonial: posição (acumulado do início até 'data'). ATIVO de um lado,
 * PASSIVO + PL do outro. O resultado do exercício ainda não encerrado entra no PL para
 * o balanço fechar (Ativo = Passivo + PL).
 */
public class BalancoResponse {

    private LocalDate data;
    private List<Linha> ativo;
    private List<Linha> passivoPl;
    private long totalAtivo;
    private long totalPassivoPl;
    private long resultadoExercicio;
    private boolean fecha;   // totalAtivo == totalPassivoPl

    public BalancoResponse(LocalDate data, List<Linha> ativo, List<Linha> passivoPl,
                           long totalAtivo, long totalPassivoPl, long resultadoExercicio) {
        this.data = data;
        this.ativo = ativo;
        this.passivoPl = passivoPl;
        this.totalAtivo = totalAtivo;
        this.totalPassivoPl = totalPassivoPl;
        this.resultadoExercicio = resultadoExercicio;
        this.fecha = totalAtivo == totalPassivoPl;
    }

    public LocalDate getData() { return data; }
    public List<Linha> getAtivo() { return ativo; }
    public List<Linha> getPassivoPl() { return passivoPl; }
    public long getTotalAtivo() { return totalAtivo; }
    public long getTotalPassivoPl() { return totalPassivoPl; }
    public long getResultadoExercicio() { return resultadoExercicio; }
    public boolean isFecha() { return fecha; }

    /** Linha do balanço: saldo em centavos (já com o lado correto do grupo). */
    public static class Linha {
        private String codigo;
        private String nome;
        private long saldoCentavos;

        public Linha(String codigo, String nome, long saldoCentavos) {
            this.codigo = codigo;
            this.nome = nome;
            this.saldoCentavos = saldoCentavos;
        }

        public String getCodigo() { return codigo; }
        public String getNome() { return nome; }
        public long getSaldoCentavos() { return saldoCentavos; }
    }
}
