package br.com.erpkit.contabil.client.dto;

import java.time.LocalDate;
import java.util.Map;

/**
 * Envelope do evento de negócio enviado pelo ERP ao módulo contábil.
 * Ver CONTRATO-EVENTOS.md (contrato /v1).
 */
public class EventoContabilRequest {

    /** UUID gerado no ERP — chave de idempotência. */
    private String eventoId;
    /** Tipo canônico (ex.: venda.finalizada, recebimento.baixado). */
    private String tipo;
    /** ERP emissor (ex.: erp-mudas) — escopo de configuração. */
    private String origem;
    /** Multi-empresa (opcional). */
    private String empresaId;
    /** Competência (data do fato gerador). */
    private LocalDate dataEvento;
    /** Valor principal em centavos — base do balanceamento. */
    private long valorCentavos;
    /** Moeda (default BRL). */
    private String moeda = "BRL";
    /** Rastreabilidade ao documento de origem. */
    private Referencia referencia;
    /** Campos específicos do tipo (inputs do roteiro). */
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

    /** Referência ao documento de origem no ERP. */
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
