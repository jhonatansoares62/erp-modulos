package br.com.erpkit.contabil.dto;

import br.com.erpkit.contabil.model.InventarioApuracao;

import java.time.Instant;
import java.time.LocalDate;

/** Resultado de uma apuração de CMV (também usado no histórico de auditoria). */
public class InventarioApuracaoResponse {

    private Long id;
    private LocalDate de;
    private LocalDate ate;
    private long estoqueInicialCentavos;
    private long comprasCentavos;
    private long estoqueFinalCentavos;
    private long cmvCentavos;
    private Long lancamentoId;
    private String apuradoPor;
    private Instant apuradoEm;
    private boolean ativo;

    public static InventarioApuracaoResponse de(InventarioApuracao a) {
        InventarioApuracaoResponse r = new InventarioApuracaoResponse();
        r.id = a.getId();
        r.de = a.getPeriodoDe();
        r.ate = a.getPeriodoAte();
        r.estoqueInicialCentavos = a.getEstoqueInicialCentavos();
        r.comprasCentavos = a.getComprasCentavos();
        r.estoqueFinalCentavos = a.getEstoqueFinalCentavos();
        r.cmvCentavos = a.getCmvCentavos();
        r.lancamentoId = a.getLancamentoId();
        r.apuradoPor = a.getApuradoPor();
        r.apuradoEm = a.getApuradoEm();
        r.ativo = a.isAtivo();
        return r;
    }

    public Long getId() { return id; }
    public LocalDate getDe() { return de; }
    public LocalDate getAte() { return ate; }
    public long getEstoqueInicialCentavos() { return estoqueInicialCentavos; }
    public long getComprasCentavos() { return comprasCentavos; }
    public long getEstoqueFinalCentavos() { return estoqueFinalCentavos; }
    public long getCmvCentavos() { return cmvCentavos; }
    public Long getLancamentoId() { return lancamentoId; }
    public String getApuradoPor() { return apuradoPor; }
    public Instant getApuradoEm() { return apuradoEm; }
    public boolean isAtivo() { return ativo; }
}
