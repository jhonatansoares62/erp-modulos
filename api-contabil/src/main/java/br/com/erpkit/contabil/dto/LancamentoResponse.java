package br.com.erpkit.contabil.dto;

import br.com.erpkit.contabil.model.Lancamento;
import br.com.erpkit.contabil.model.Partida;

import java.time.LocalDate;
import java.util.List;

/** Lançamento com suas partidas. */
public class LancamentoResponse {

    private Long id;
    private Long numero;
    private LocalDate dataCompetencia;
    private String historico;
    private String status;
    private String origemDocumento;
    private Long estornaId;
    private Long estornadoPorId;
    private List<PartidaLinha> partidas;

    public static LancamentoResponse de(Lancamento l, List<Partida> ps) {
        LancamentoResponse r = new LancamentoResponse();
        r.id = l.getId();
        r.numero = l.getNumero();
        r.dataCompetencia = l.getDataCompetencia();
        r.historico = l.getHistorico();
        r.status = l.getStatus();
        r.origemDocumento = l.getOrigemDocumento();
        r.estornaId = l.getEstornaId();
        r.estornadoPorId = l.getEstornadoPorId();
        r.partidas = ps.stream().map(PartidaLinha::de).toList();
        return r;
    }

    public Long getId() { return id; }
    public Long getNumero() { return numero; }
    public LocalDate getDataCompetencia() { return dataCompetencia; }
    public String getHistorico() { return historico; }
    public String getStatus() { return status; }
    public String getOrigemDocumento() { return origemDocumento; }
    public Long getEstornaId() { return estornaId; }
    public Long getEstornadoPorId() { return estornadoPorId; }
    public List<PartidaLinha> getPartidas() { return partidas; }

    public static class PartidaLinha {
        private Long contaId;
        private String tipo;
        private long valorCentavos;

        static PartidaLinha de(Partida p) {
            PartidaLinha l = new PartidaLinha();
            l.contaId = p.getContaId();
            l.tipo = p.getTipo();
            l.valorCentavos = p.getValorCentavos();
            return l;
        }

        public Long getContaId() { return contaId; }
        public String getTipo() { return tipo; }
        public long getValorCentavos() { return valorCentavos; }
    }
}
