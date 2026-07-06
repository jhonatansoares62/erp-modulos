package br.com.erpkit.contabil.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento de negócio recebido do ERP. Mapeia contabil.evento_recebido (V1).
 * id = eventoId (UUID do cliente) — chave de idempotência. payload é o JSON bruto,
 * guardado para reprocessar ao criar regra a partir de pendência.
 */
@Entity
@Table(schema = "contabil", name = "evento_recebido")
public class EventoRecebido {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tipo", nullable = false, length = 60)
    private String tipo;

    @Column(name = "origem", nullable = false, length = 60)
    private String origem;

    @Column(name = "empresa_id", length = 60)
    private String empresaId;

    @Column(name = "payload")
    private String payload;

    @Column(name = "data_evento", nullable = false)
    private LocalDate dataEvento;

    @Column(name = "valor_centavos", nullable = false)
    private long valorCentavos;

    /** pendente | processado | sem_regra | erro */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "pendente";

    @Column(name = "lancamento_id")
    private Long lancamentoId;

    @Column(name = "erro_mensagem", length = 500)
    private String erroMensagem;

    @Column(name = "recebido_em", insertable = false, updatable = false)
    private Instant recebidoEm;

    @Column(name = "processado_em")
    private Instant processadoEm;

    public EventoRecebido() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getOrigem() { return origem; }
    public void setOrigem(String origem) { this.origem = origem; }

    public String getEmpresaId() { return empresaId; }
    public void setEmpresaId(String empresaId) { this.empresaId = empresaId; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public LocalDate getDataEvento() { return dataEvento; }
    public void setDataEvento(LocalDate dataEvento) { this.dataEvento = dataEvento; }

    public long getValorCentavos() { return valorCentavos; }
    public void setValorCentavos(long valorCentavos) { this.valorCentavos = valorCentavos; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getLancamentoId() { return lancamentoId; }
    public void setLancamentoId(Long lancamentoId) { this.lancamentoId = lancamentoId; }

    public String getErroMensagem() { return erroMensagem; }
    public void setErroMensagem(String erroMensagem) { this.erroMensagem = erroMensagem; }

    public Instant getRecebidoEm() { return recebidoEm; }

    public Instant getProcessadoEm() { return processadoEm; }
    public void setProcessadoEm(Instant processadoEm) { this.processadoEm = processadoEm; }
}
