package br.com.erpkit.contabil.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Período fechado (lock date). Mapeia contabil.periodo_fechado (V1).
 * competencia = 'YYYY-MM' (mensal) ou 'YYYY' (exercício). Lançamento com data no período
 * fechado (ou anterior) é rejeitado (F8).
 */
@Entity
@Table(schema = "contabil", name = "periodo_fechado")
public class PeriodoFechado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", length = 60)
    private String empresaId;

    @Column(name = "competencia", nullable = false, length = 7)
    private String competencia;

    /** mensal | exercicio */
    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    @Column(name = "data_fechamento", insertable = false, updatable = false)
    private Instant dataFechamento;

    @Column(name = "fechado_por", length = 120)
    private String fechadoPor;

    public PeriodoFechado() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmpresaId() { return empresaId; }
    public void setEmpresaId(String empresaId) { this.empresaId = empresaId; }

    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Instant getDataFechamento() { return dataFechamento; }

    public String getFechadoPor() { return fechadoPor; }
    public void setFechadoPor(String fechadoPor) { this.fechadoPor = fechadoPor; }
}
