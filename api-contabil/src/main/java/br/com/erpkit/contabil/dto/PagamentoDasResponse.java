package br.com.erpkit.contabil.dto;

import br.com.erpkit.contabil.model.PagamentoDas;

import java.time.Instant;
import java.time.LocalDate;

public class PagamentoDasResponse {

    private final Long id;
    private final String competencia;
    private final long valorCentavos;
    private final LocalDate dataPagamento;
    private final String contaLiquidacao;
    private final String contaLiquidacaoNome;
    private final Long lancamentoId;
    private final String origem;
    private final Instant createdAt;

    public PagamentoDasResponse(PagamentoDas p, String contaNome) {
        this.id = p.getId();
        this.competencia = p.getCompetencia();
        this.valorCentavos = p.getValorCentavos();
        this.dataPagamento = p.getDataPagamento();
        this.contaLiquidacao = p.getContaLiquidacao();
        this.contaLiquidacaoNome = contaNome;
        this.lancamentoId = p.getLancamentoId();
        this.origem = p.getOrigem();
        this.createdAt = p.getCreatedAt();
    }

    public Long getId() { return id; }
    public String getCompetencia() { return competencia; }
    public long getValorCentavos() { return valorCentavos; }
    public LocalDate getDataPagamento() { return dataPagamento; }
    public String getContaLiquidacao() { return contaLiquidacao; }
    public String getContaLiquidacaoNome() { return contaLiquidacaoNome; }
    public Long getLancamentoId() { return lancamentoId; }
    public String getOrigem() { return origem; }
    public Instant getCreatedAt() { return createdAt; }
}
