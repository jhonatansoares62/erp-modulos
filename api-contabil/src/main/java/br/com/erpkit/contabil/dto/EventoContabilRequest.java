package br.com.erpkit.contabil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Map;

/**
 * Envelope do evento de negócio recebido do ERP (contrato /v1 — ver CONTRATO-EVENTOS.md).
 */
public class EventoContabilRequest {

    @NotBlank(message = "eventoId é obrigatório")
    private String eventoId;

    @NotBlank(message = "tipo é obrigatório")
    private String tipo;

    @NotBlank(message = "origem é obrigatória")
    private String origem;

    private String empresaId;

    @NotNull(message = "dataEvento é obrigatória")
    private LocalDate dataEvento;

    private long valorCentavos;

    private String moeda = "BRL";

    private Referencia referencia;

    private Map<String, Object> contexto;

    public String getEventoId() { return eventoId; }
    public void setEventoId(String eventoId) { this.eventoId = eventoId; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getOrigem() { return origem; }
    public void setOrigem(String origem) { this.origem = origem; }

    public String getEmpresaId() { return empresaId; }
    public void setEmpresaId(String empresaId) { this.empresaId = empresaId; }

    public LocalDate getDataEvento() { return dataEvento; }
    public void setDataEvento(LocalDate dataEvento) { this.dataEvento = dataEvento; }

    public long getValorCentavos() { return valorCentavos; }
    public void setValorCentavos(long valorCentavos) { this.valorCentavos = valorCentavos; }

    public String getMoeda() { return moeda; }
    public void setMoeda(String moeda) { this.moeda = moeda; }

    public Referencia getReferencia() { return referencia; }
    public void setReferencia(Referencia referencia) { this.referencia = referencia; }

    public Map<String, Object> getContexto() { return contexto; }
    public void setContexto(Map<String, Object> contexto) { this.contexto = contexto; }

    public static class Referencia {
        private String entidade;
        private String id;
        private String numero;

        public String getEntidade() { return entidade; }
        public void setEntidade(String entidade) { this.entidade = entidade; }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getNumero() { return numero; }
        public void setNumero(String numero) { this.numero = numero; }
    }
}
