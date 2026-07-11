package br.com.erpkit.whatsapp.dto;

import java.time.Instant;

/**
 * Uma mensagem no historico de um telefone para a tela de chat do atendente
 * (GET {@code /api/whatsapp/conversas/{telefone}/mensagens}). Ordenada ASC
 * (cronologica — natural para renderizar um chat de cima para baixo).
 *
 * <p><b>Classe com getters/setters</b> (convencao do monorepo — sem Java records).
 *
 * <p><b>{@code direcao}:</b> {@code ENTRADA} (in) ou {@code SAIDA} (out) — rotulos PT-BR
 * derivados do enum {@link br.com.erpkit.whatsapp.model.Direcao} (armazenado como
 * {@code in}/{@code out}).
 *
 * <p><b>{@code conteudo}:</b> texto cru como persistido em {@code mensagens_log.conteudo}
 * (para {@code text} e o corpo; para {@code interactive_button}/{@code interactive_list}
 * e {@code id|titulo}; para {@code document} e o nome do arquivo; para {@code image}/
 * {@code audio} e o mime-type). {@code preview} traz uma versao amigavel/curta para exibir.
 *
 * <p><b>{@code status}/{@code resultado}:</b> so preenchidos quando existirem —
 * {@code status} de entrega (sent/delivered/read/failed) chega por webhook nas SAIDAS;
 * {@code resultado} e o desfecho do bot nas ENTRADAS. {@code null} quando nao se aplica.
 */
public class MensagemResponse {

    private Long id;
    private String direcao;
    private String tipo;
    private String conteudo;
    private String preview;
    private Instant timestamp;
    private String status;
    private String resultado;
    private String wamid;

    public MensagemResponse() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDirecao() { return direcao; }
    public void setDirecao(String direcao) { this.direcao = direcao; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getConteudo() { return conteudo; }
    public void setConteudo(String conteudo) { this.conteudo = conteudo; }

    public String getPreview() { return preview; }
    public void setPreview(String preview) { this.preview = preview; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }

    public String getWamid() { return wamid; }
    public void setWamid(String wamid) { this.wamid = wamid; }
}
