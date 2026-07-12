package br.com.erpkit.whatsapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Estado da conversa por telefone (schema {@code whatsapp.estado_conversa}). Ate a V11
 * a tabela era um placeholder vazio; a V12 adicionou o handoff de atendimento humano.
 *
 * <p>Quando {@link #emAtendimento} = true, o bot NAO responde aquele telefone (guard no
 * {@code MensagemAsyncListener} antes do dispatch ao ERP): a recepcao assume/encerra pelo
 * inbox. Conceito GENERICO do canal (nao conhece dominio de ERP).
 */
@Entity
@Table(schema = "whatsapp", name = "estado_conversa")
public class EstadoConversa {

    @Id
    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "ultima_atualizacao", nullable = false)
    private Instant ultimaAtualizacao;

    @Column(name = "em_atendimento", nullable = false)
    private boolean emAtendimento;

    @Column(name = "iniciado_em")
    private Instant iniciadoEm;

    public EstadoConversa() {
        // JPA
    }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public Instant getUltimaAtualizacao() { return ultimaAtualizacao; }
    public void setUltimaAtualizacao(Instant ultimaAtualizacao) { this.ultimaAtualizacao = ultimaAtualizacao; }

    public boolean isEmAtendimento() { return emAtendimento; }
    public void setEmAtendimento(boolean emAtendimento) { this.emAtendimento = emAtendimento; }

    public Instant getIniciadoEm() { return iniciadoEm; }
    public void setIniciadoEm(Instant iniciadoEm) { this.iniciadoEm = iniciadoEm; }
}
