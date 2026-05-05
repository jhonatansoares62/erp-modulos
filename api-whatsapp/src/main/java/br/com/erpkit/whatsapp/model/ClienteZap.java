package br.com.erpkit.whatsapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Cliente WhatsApp — mapeia 1:1 com {@code whatsapp.clientes_zap} (V1 Phase 1).
 *
 * <p>{@code idClienteErp} e nullable: politica D-07 do CONTEXT.md cria registros
 * com {@code id_cliente_erp = null} para telefones nao mapeados ainda no ERP. Um
 * job de reconciliation (fora desta milestone) pode preencher depois.
 *
 * <p>{@code telefone} armazenado JA NORMALIZADO (D-03) — sempre UTF-8 / digitos
 * apenas, formato {@code 55<DDD><numero>}. Lookups SEMPRE via {@code TelefoneBR.normalizar}
 * antes de buscar.
 *
 * <p>{@code criadoEm} usa {@code DEFAULT NOW()} do banco — Hibernate ignora INSERT
 * (insertable=false), apenas le no SELECT (updatable=false). Garante que o relogio
 * do banco e a fonte de verdade do timestamp de criacao (PITFALLS C-01).
 */
@Entity
@Table(schema = "whatsapp", name = "clientes_zap")
public class ClienteZap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_cliente_erp")
    private Long idClienteErp;

    @Column(name = "telefone", nullable = false, unique = true, length = 20)
    private String telefone;

    @Column(name = "ultima_mensagem_em")
    private Instant ultimaMensagemEm;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private Instant criadoEm;

    public ClienteZap() {
        // JPA exige construtor padrao
    }

    /** Helper para criacao manual (used em ClienteZapService.identificar). */
    public ClienteZap(String telefone, Long idClienteErp) {
        this.telefone = telefone;
        this.idClienteErp = idClienteErp;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIdClienteErp() { return idClienteErp; }
    public void setIdClienteErp(Long idClienteErp) { this.idClienteErp = idClienteErp; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public Instant getUltimaMensagemEm() { return ultimaMensagemEm; }
    public void setUltimaMensagemEm(Instant ultimaMensagemEm) { this.ultimaMensagemEm = ultimaMensagemEm; }

    public Instant getCriadoEm() { return criadoEm; }
    // sem setter — campo gerenciado pelo banco

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClienteZap that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        // NAO expor telefone completo em toString (defesa em profundidade — telefone e PII)
        return "ClienteZap{id=" + id
             + ", idClienteErp=" + idClienteErp
             + ", telefone=" + (telefone == null ? null : telefone.substring(0, Math.min(4, telefone.length())) + "***")
             + ", ultimaMensagemEm=" + ultimaMensagemEm + "}";
    }
}
