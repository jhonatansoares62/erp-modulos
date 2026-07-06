package br.com.erpkit.contabil.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Apuração de CMV por inventário periódico. Mapeia contabil.inventario_apuracao (V7).
 * CMV = estoqueInicial + compras − estoqueFinal. A apuração vigente do período é ativo=true;
 * reapurar desativa a anterior e estorna seu lançamento.
 */
@Entity
@Table(schema = "contabil", name = "inventario_apuracao")
public class InventarioApuracao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "periodo_de", nullable = false)
    private LocalDate periodoDe;

    @Column(name = "periodo_ate", nullable = false)
    private LocalDate periodoAte;

    @Column(name = "estoque_inicial_centavos", nullable = false)
    private long estoqueInicialCentavos;

    @Column(name = "compras_centavos", nullable = false)
    private long comprasCentavos;

    @Column(name = "estoque_final_centavos", nullable = false)
    private long estoqueFinalCentavos;

    @Column(name = "cmv_centavos", nullable = false)
    private long cmvCentavos;

    @Column(name = "lancamento_id")
    private Long lancamentoId;

    @Column(name = "apurado_por", length = 120)
    private String apuradoPor;

    @Column(name = "apurado_em", insertable = false, updatable = false)
    private Instant apuradoEm;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getPeriodoDe() { return periodoDe; }
    public void setPeriodoDe(LocalDate periodoDe) { this.periodoDe = periodoDe; }
    public LocalDate getPeriodoAte() { return periodoAte; }
    public void setPeriodoAte(LocalDate periodoAte) { this.periodoAte = periodoAte; }
    public long getEstoqueInicialCentavos() { return estoqueInicialCentavos; }
    public void setEstoqueInicialCentavos(long v) { this.estoqueInicialCentavos = v; }
    public long getComprasCentavos() { return comprasCentavos; }
    public void setComprasCentavos(long v) { this.comprasCentavos = v; }
    public long getEstoqueFinalCentavos() { return estoqueFinalCentavos; }
    public void setEstoqueFinalCentavos(long v) { this.estoqueFinalCentavos = v; }
    public long getCmvCentavos() { return cmvCentavos; }
    public void setCmvCentavos(long v) { this.cmvCentavos = v; }
    public Long getLancamentoId() { return lancamentoId; }
    public void setLancamentoId(Long lancamentoId) { this.lancamentoId = lancamentoId; }
    public String getApuradoPor() { return apuradoPor; }
    public void setApuradoPor(String apuradoPor) { this.apuradoPor = apuradoPor; }
    public Instant getApuradoEm() { return apuradoEm; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
