package br.com.erpkit.whatsapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Log de mensagem WhatsApp (entrante ou saida). Mapeia {@code whatsapp.mensagens_log}
 * (V2 Phase 1).
 *
 * <p>{@code wamid} e {@code UNIQUE NOT NULL} — gate atomico de idempotencia. Phase 2
 * Plan 03 usa o fallback documentado no RESEARCH §2.4: {@code save()} envolvido em
 * try/catch de {@link org.springframework.dao.DataIntegrityViolationException}, ja
 * que H2 v2.3.232 nao suporta a sintaxe Postgres-native {@code ON CONFLICT}
 * (gate empirico Wave 1 — ver {@code OnConflictSpikeTest}). UNIQUE constraint do
 * banco e o gate atomico real.
 *
 * <p>{@code conteudo} e {@code @Lob} mapeado para {@code TEXT} no Postgres / CLOB no H2 —
 * suporta payloads grandes (lista interactive com descricoes, mensagem text longa).
 *
 * <p>{@code direcao} usa {@code @Enumerated(STRING)} com enum {@link Direcao} em lowercase
 * — bate com CHECK constraint {@code direcao IN ('in', 'out')}.
 */
@Entity
@Table(schema = "whatsapp", name = "mensagens_log")
public class MensagemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wamid", nullable = false, unique = true, length = 255)
    private String wamid;

    @Column(name = "telefone", nullable = false, length = 20)
    private String telefone;

    // wa_id EXATO do Meta (com 9o digito) — numero de RESPOSTA. NULL para saidas
    // (telefone ja e o wa_id) e rows antigas. Ver V5 migration.
    @Column(name = "wa_id", length = 20)
    private String waId;

    @Column(name = "direcao", nullable = false, length = 3)
    @Enumerated(EnumType.STRING)
    private Direcao direcao;

    @Column(name = "tipo", length = 50)
    private String tipo;  // String em vez de enum — flexivel para "desconhecido" e tipos novos do Meta (D-05)

    // V2 migration declara `conteudo TEXT`. H2 PG-mode armazena TEXT como
    // CHARACTER VARYING (sem limite) — Hibernate `@Lob` espera OID/CLOB e quebra
    // schema validation. columnDefinition = "TEXT" alinha o mapeamento JPA com
    // o que Flyway aplicou em ambos H2 PG-mode e PostgreSQL real (TEXT em PG e
    // unbounded varchar nativo, sem oid/large object).
    @Column(name = "conteudo", columnDefinition = "TEXT")
    private String conteudo;

    @Column(name = "media_id", length = 255)
    private String mediaId;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private Instant criadoEm;

    // ── V7 (observabilidade §12): dados antes descartados. Todos nullable. ──
    // Entrada: idClienteErp (quando resolve), eventoEm (ts Meta), comando (intencao).
    // Saida:   eventoEm (envio) + status/status_em/erro_*/conversationId/categoria/
    //          billable/conversaOrigem chegam via status webhook (UPDATE por wamid).
    @Column(name = "id_cliente_erp")
    private Long idClienteErp;

    @Column(name = "evento_em")
    private Instant eventoEm;

    @Column(name = "status", length = 12)
    private String status;

    @Column(name = "status_em")
    private Instant statusEm;

    @Column(name = "erro_codigo", length = 20)
    private String erroCodigo;

    @Column(name = "erro_titulo", length = 255)
    private String erroTitulo;

    @Column(name = "conversation_id", length = 80)
    private String conversationId;

    @Column(name = "categoria", length = 30)
    private String categoria;

    @Column(name = "billable")
    private Boolean billable;

    @Column(name = "conversa_origem", length = 30)
    private String conversaOrigem;

    @Column(name = "comando", length = 255)
    private String comando;

    // V8 (§12 #6): desfecho do bot na entrada — respondido/nao_entendi/sem_resposta/erro.
    @Column(name = "resultado", length = 20)
    private String resultado;

    public MensagemLog() {
        // JPA exige construtor padrao
    }

    public MensagemLog(String wamid, String telefone, Direcao direcao, String tipo, String conteudo, String mediaId) {
        this.wamid = wamid;
        this.telefone = telefone;
        this.direcao = direcao;
        this.tipo = tipo;
        this.conteudo = conteudo;
        this.mediaId = mediaId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getWamid() { return wamid; }
    public void setWamid(String wamid) { this.wamid = wamid; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getWaId() { return waId; }
    public void setWaId(String waId) { this.waId = waId; }

    public Direcao getDirecao() { return direcao; }
    public void setDirecao(Direcao direcao) { this.direcao = direcao; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getConteudo() { return conteudo; }
    public void setConteudo(String conteudo) { this.conteudo = conteudo; }

    public String getMediaId() { return mediaId; }
    public void setMediaId(String mediaId) { this.mediaId = mediaId; }

    public Instant getCriadoEm() { return criadoEm; }
    // sem setter — campo gerenciado pelo banco

    public Long getIdClienteErp() { return idClienteErp; }
    public void setIdClienteErp(Long idClienteErp) { this.idClienteErp = idClienteErp; }

    public Instant getEventoEm() { return eventoEm; }
    public void setEventoEm(Instant eventoEm) { this.eventoEm = eventoEm; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getStatusEm() { return statusEm; }
    public void setStatusEm(Instant statusEm) { this.statusEm = statusEm; }

    public String getErroCodigo() { return erroCodigo; }
    public void setErroCodigo(String erroCodigo) { this.erroCodigo = erroCodigo; }

    public String getErroTitulo() { return erroTitulo; }
    public void setErroTitulo(String erroTitulo) { this.erroTitulo = erroTitulo; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public Boolean getBillable() { return billable; }
    public void setBillable(Boolean billable) { this.billable = billable; }

    public String getConversaOrigem() { return conversaOrigem; }
    public void setConversaOrigem(String conversaOrigem) { this.conversaOrigem = conversaOrigem; }

    public String getComando() { return comando; }
    public void setComando(String comando) { this.comando = comando; }

    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MensagemLog that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        // NAO expor conteudo (PII) — apenas metadados
        return "MensagemLog{id=" + id
             + ", wamid=" + wamid
             + ", direcao=" + direcao
             + ", tipo=" + tipo
             + ", mediaId=" + mediaId
             + ", criadoEm=" + criadoEm + "}";
    }
}
