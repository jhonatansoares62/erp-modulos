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
 * Pagamento do DAS registrado NO módulo (autoridade única por competência). O UNIQUE em
 * competencia impede dupla baixa do 2.1.3.01, tanto do app standalone quanto do ERP.
 */
@Entity
@Table(schema = "contabil", name = "pagamento_das")
public class PagamentoDas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 7)
    private String competencia;

    @Column(name = "valor_centavos", nullable = false)
    private long valorCentavos;

    @Column(name = "data_pagamento", nullable = false)
    private LocalDate dataPagamento;

    @Column(name = "conta_liquidacao", nullable = false, length = 20)
    private String contaLiquidacao;

    @Column(name = "lancamento_id")
    private Long lancamentoId;

    @Column(name = "evento_id")
    private UUID eventoId;

    @Column(nullable = false, length = 20)
    private String origem = "app";

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCompetencia() { return competencia; }
    public void setCompetencia(String competencia) { this.competencia = competencia; }
    public long getValorCentavos() { return valorCentavos; }
    public void setValorCentavos(long valorCentavos) { this.valorCentavos = valorCentavos; }
    public LocalDate getDataPagamento() { return dataPagamento; }
    public void setDataPagamento(LocalDate dataPagamento) { this.dataPagamento = dataPagamento; }
    public String getContaLiquidacao() { return contaLiquidacao; }
    public void setContaLiquidacao(String contaLiquidacao) { this.contaLiquidacao = contaLiquidacao; }
    public Long getLancamentoId() { return lancamentoId; }
    public void setLancamentoId(Long lancamentoId) { this.lancamentoId = lancamentoId; }
    public UUID getEventoId() { return eventoId; }
    public void setEventoId(UUID eventoId) { this.eventoId = eventoId; }
    public String getOrigem() { return origem; }
    public void setOrigem(String origem) { this.origem = origem; }
    public Instant getCreatedAt() { return createdAt; }
}
