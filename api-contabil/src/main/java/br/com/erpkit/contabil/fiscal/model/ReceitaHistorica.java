package br.com.erpkit.contabil.fiscal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Receita bruta histórica por competência (V22) — PARÂMETRO FISCAL que alimenta o RBT12 de empresa
 * migrando; NÃO é lançamento contábil. Single-tenant, PK = competência 'YYYY-MM'.
 */
@Entity
@Table(schema = "contabil", name = "fiscal_receita_historica")
public class ReceitaHistorica {

    @Id
    @Column(length = 7)
    private String competencia;   // 'YYYY-MM'

    @Column(name = "receita_bruta_centavos", nullable = false)
    private long receitaBrutaCentavos;

    public ReceitaHistorica() {
    }

    public ReceitaHistorica(String competencia, long receitaBrutaCentavos) {
        this.competencia = competencia;
        this.receitaBrutaCentavos = receitaBrutaCentavos;
    }

    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }
    public long getReceitaBrutaCentavos() { return receitaBrutaCentavos; }
    public void setReceitaBrutaCentavos(long receitaBrutaCentavos) { this.receitaBrutaCentavos = receitaBrutaCentavos; }
}
