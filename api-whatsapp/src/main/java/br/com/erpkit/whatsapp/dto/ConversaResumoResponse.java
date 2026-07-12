package br.com.erpkit.whatsapp.dto;

import java.time.Instant;

/**
 * Resumo de uma conversa (por telefone) para a lista de Inbox do app do atendente
 * (GET {@code /api/whatsapp/conversas}). Uma linha por telefone, ordenada por
 * ultima atividade DESC.
 *
 * <p><b>Classe com getters/setters</b> (convencao do monorepo — sem Java records
 * para DTOs). Jackson serializa via getters.
 *
 * <p><b>Janela 24h:</b> {@code janelaAberta} e {@code janelaExpiraEm} sao derivados
 * de {@code clientes_zap.ultima_mensagem_em} — o mesmo campo (relogio do banco,
 * atualizado SO em mensagem ENTRANTE via PER-07) que o {@code WindowEnforcementService}
 * usa como fonte da verdade da trava 24h. {@code janelaExpiraEm = ultima_mensagem_em + 24h};
 * {@code janelaAberta = agora < janelaExpiraEm}. Se o telefone nao esta em
 * {@code clientes_zap} ou nunca recebeu entrada, {@code janelaAberta=false} e
 * {@code janelaExpiraEm=null}. NAO existe tabela de estado ativa (a {@code estado_conversa}
 * da V4 e um placeholder nunca populado), por isso a derivacao vem de {@code clientes_zap}.
 *
 * <p><b>Sem {@code naoLidas}:</b> o modulo nao tem conceito de mensagem lida/nao-lida
 * (nenhuma coluna), entao esse campo foi deliberadamente omitido (sem inventar schema).
 *
 * <p><b>{@code nome}:</b> sempre {@code null} por enquanto — {@code clientes_zap} nao
 * armazena o nome do contato. Campo mantido no contrato para preenchimento futuro.
 */
public class ConversaResumoResponse {

    private String telefone;
    private String nome;
    private String ultimaMensagemPreview;
    private Instant ultimaMensagemEm;
    private String ultimaDirecao;
    private long totalMensagens;
    private boolean janelaAberta;
    private Instant janelaExpiraEm;
    private boolean emAtendimento;

    public ConversaResumoResponse() {
    }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getUltimaMensagemPreview() { return ultimaMensagemPreview; }
    public void setUltimaMensagemPreview(String ultimaMensagemPreview) { this.ultimaMensagemPreview = ultimaMensagemPreview; }

    public Instant getUltimaMensagemEm() { return ultimaMensagemEm; }
    public void setUltimaMensagemEm(Instant ultimaMensagemEm) { this.ultimaMensagemEm = ultimaMensagemEm; }

    public String getUltimaDirecao() { return ultimaDirecao; }
    public void setUltimaDirecao(String ultimaDirecao) { this.ultimaDirecao = ultimaDirecao; }

    public long getTotalMensagens() { return totalMensagens; }
    public void setTotalMensagens(long totalMensagens) { this.totalMensagens = totalMensagens; }

    public boolean isJanelaAberta() { return janelaAberta; }
    public void setJanelaAberta(boolean janelaAberta) { this.janelaAberta = janelaAberta; }

    public Instant getJanelaExpiraEm() { return janelaExpiraEm; }
    public void setJanelaExpiraEm(Instant janelaExpiraEm) { this.janelaExpiraEm = janelaExpiraEm; }

    public boolean isEmAtendimento() { return emAtendimento; }
    public void setEmAtendimento(boolean emAtendimento) { this.emAtendimento = emAtendimento; }
}
