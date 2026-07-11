package br.com.erpkit.whatsapp.dto;

import java.time.Instant;

/**
 * Detalhe do contato + estado da janela 24h de uma conversa
 * (GET {@code /api/whatsapp/conversas/{telefone}}). Usado pelo cabecalho da tela
 * de chat do atendente.
 *
 * <p><b>Classe com getters/setters</b> (convencao do monorepo — sem Java records).
 *
 * <p><b>Janela 24h:</b> mesma derivacao do {@link ConversaResumoResponse} —
 * {@code clientes_zap.ultima_mensagem_em} (ultima ENTRADA, relogio do banco) + 24h.
 *
 * <p><b>{@code idClienteErp}:</b> vinculo com o cadastro do ERP quando resolvido
 * (D-07); {@code null} para telefones ainda nao mapeados. {@code nome} sempre
 * {@code null} (contato nao tem nome armazenado ainda).
 */
public class ConversaDetalheResponse {

    private String telefone;
    private String nome;
    private Long idClienteErp;
    private Instant ultimaMensagemEm;
    private long totalMensagens;
    private boolean janelaAberta;
    private Instant janelaExpiraEm;

    public ConversaDetalheResponse() {
    }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public Long getIdClienteErp() { return idClienteErp; }
    public void setIdClienteErp(Long idClienteErp) { this.idClienteErp = idClienteErp; }

    public Instant getUltimaMensagemEm() { return ultimaMensagemEm; }
    public void setUltimaMensagemEm(Instant ultimaMensagemEm) { this.ultimaMensagemEm = ultimaMensagemEm; }

    public long getTotalMensagens() { return totalMensagens; }
    public void setTotalMensagens(long totalMensagens) { this.totalMensagens = totalMensagens; }

    public boolean isJanelaAberta() { return janelaAberta; }
    public void setJanelaAberta(boolean janelaAberta) { this.janelaAberta = janelaAberta; }

    public Instant getJanelaExpiraEm() { return janelaExpiraEm; }
    public void setJanelaExpiraEm(Instant janelaExpiraEm) { this.janelaExpiraEm = janelaExpiraEm; }
}
