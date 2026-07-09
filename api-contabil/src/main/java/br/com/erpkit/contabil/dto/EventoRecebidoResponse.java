package br.com.erpkit.contabil.dto;

/** Resposta da ingestão de evento. status = processado | pendente | sem_regra | erro | periodo_fechado. */
public class EventoRecebidoResponse {

    private String eventoId;
    private String status;
    private Long lancamentoId;

    public EventoRecebidoResponse() {
    }

    public EventoRecebidoResponse(String eventoId, String status, Long lancamentoId) {
        this.eventoId = eventoId;
        this.status = status;
        this.lancamentoId = lancamentoId;
    }

    public String getEventoId() { return eventoId; }
    public void setEventoId(String eventoId) { this.eventoId = eventoId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getLancamentoId() { return lancamentoId; }
    public void setLancamentoId(Long lancamentoId) { this.lancamentoId = lancamentoId; }
}
