package br.com.erpkit.contabil.dto;

import br.com.erpkit.contabil.model.EventoRecebido;

import java.time.Instant;
import java.time.LocalDate;

/** Evento pendente (sem roteiro) aguardando classificação. */
public class PendenciaResponse {

    private String eventoId;
    private String tipo;
    private String origem;
    private LocalDate dataEvento;
    private long valorCentavos;
    private Instant recebidoEm;

    public static PendenciaResponse de(EventoRecebido e) {
        PendenciaResponse r = new PendenciaResponse();
        r.eventoId = e.getId().toString();
        r.tipo = e.getTipo();
        r.origem = e.getOrigem();
        r.dataEvento = e.getDataEvento();
        r.valorCentavos = e.getValorCentavos();
        r.recebidoEm = e.getRecebidoEm();
        return r;
    }

    public String getEventoId() { return eventoId; }
    public String getTipo() { return tipo; }
    public String getOrigem() { return origem; }
    public LocalDate getDataEvento() { return dataEvento; }
    public long getValorCentavos() { return valorCentavos; }
    public Instant getRecebidoEm() { return recebidoEm; }
}
