package br.com.erpkit.contabil.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lançamento contábil (cabeçalho). Mapeia contabil.lancamento (V1).
 * 'lancado' é imutável; correção só via estorno (novo lançamento com estornaId → original).
 */
@Entity
@Table(schema = "contabil", name = "lancamento")
public class Lancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero")
    private Long numero;

    @Column(name = "data_competencia", nullable = false)
    private LocalDate dataCompetencia;

    @Column(name = "historico", length = 300)
    private String historico;

    @Column(name = "origem_evento_id")
    private UUID origemEventoId;

    @Column(name = "origem_documento", length = 120)
    private String origemDocumento;

    /** Este lançamento estorna o lançamento X. */
    @Column(name = "estorna_id")
    private Long estornaId;

    /** O lançamento X foi estornado por este. */
    @Column(name = "estornado_por_id")
    private Long estornadoPorId;

    /** rascunho | lancado | estornado */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "rascunho";

    @Column(name = "lancado_em")
    private Instant lancadoEm;

    @Column(name = "hash", length = 64)
    private String hash;

    @Column(name = "hash_anterior", length = 64)
    private String hashAnterior;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public Lancamento() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getNumero() { return numero; }
    public void setNumero(Long numero) { this.numero = numero; }

    public LocalDate getDataCompetencia() { return dataCompetencia; }
    public void setDataCompetencia(LocalDate dataCompetencia) { this.dataCompetencia = dataCompetencia; }

    public String getHistorico() { return historico; }
    public void setHistorico(String historico) { this.historico = historico; }

    public UUID getOrigemEventoId() { return origemEventoId; }
    public void setOrigemEventoId(UUID origemEventoId) { this.origemEventoId = origemEventoId; }

    public String getOrigemDocumento() { return origemDocumento; }
    public void setOrigemDocumento(String origemDocumento) { this.origemDocumento = origemDocumento; }

    public Long getEstornaId() { return estornaId; }
    public void setEstornaId(Long estornaId) { this.estornaId = estornaId; }

    public Long getEstornadoPorId() { return estornadoPorId; }
    public void setEstornadoPorId(Long estornadoPorId) { this.estornadoPorId = estornadoPorId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getLancadoEm() { return lancadoEm; }
    public void setLancadoEm(Instant lancadoEm) { this.lancadoEm = lancadoEm; }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }

    public String getHashAnterior() { return hashAnterior; }
    public void setHashAnterior(String hashAnterior) { this.hashAnterior = hashAnterior; }

    public Instant getCreatedAt() { return createdAt; }
}
