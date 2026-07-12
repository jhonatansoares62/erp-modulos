package br.com.erpkit.whatsapp.dto;

/**
 * Uma mensagem do feed de recentes (entrada ou saida) — item de
 * {@link FeedRecentesResponse}. Espelha o shape historico do
 * {@code MonitorController.MonitorMensagem} (dev/meta) para que a tela de Testes
 * do app do atendente consuma o MESMO formato tanto em dev quanto em producao.
 *
 * @param id       id da linha em {@code whatsapp.mensagens_log}
 * @param direcao  {@code "in"} | {@code "out"} (lowercase, bate com {@code Direcao})
 * @param telefone numero para RESPONDER: o {@code wa_id} exato (com 9) quando houver,
 *                 senao o {@code telefone} (saidas ja gravam o wa_id ali)
 * @param tipo     tipo Meta da mensagem (text, interactive, image, ...) ou {@code null}
 * @param conteudo texto/preview da mensagem ou {@code null}
 * @param criadoEm epoch millis de criacao ou {@code null}
 */
public record MensagemRecenteResponse(
        Long id,
        String direcao,
        String telefone,
        String tipo,
        String conteudo,
        Long criadoEm
) {
}
